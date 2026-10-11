// order-management/src/main/java/com/thomas/order_management/service/InventoryService.java
package com.thomas.order_management.service;

import com.thomas.order_management.dto.PageResponse;
import com.thomas.order_management.dto.inventory.*;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.exception.ConflictException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lagerorte, Bestände und Materialbelege. Bestandsänderungen laufen ausschließlich über
 * {@link StockPostingService}.
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final StockPostingService stockPostingService;

    public InventoryService(WarehouseRepository warehouseRepository,
                            WarehouseStockRepository warehouseStockRepository,
                            StockMovementRepository stockMovementRepository,
                            ProductRepository productRepository,
                            StockPostingService stockPostingService) {
        this.warehouseRepository = warehouseRepository;
        this.warehouseStockRepository = warehouseStockRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.stockPostingService = stockPostingService;
    }

    // === Lagerorte ===

    public List<WarehouseDto> getAllWarehouses() {
        return warehouseRepository.findAll().stream().map(this::toWarehouseDto).toList();
    }

    public List<WarehouseDto> getActiveWarehouses() {
        return warehouseRepository.findAllActiveOrderByDefault().stream().map(this::toWarehouseDto).toList();
    }

    public WarehouseDto getWarehouseById(Long id) {
        return toWarehouseDto(loadWarehouse(id));
    }

    @Transactional
    public WarehouseDto createWarehouse(WarehouseDto dto) {
        warehouseRepository.findByCode(dto.getCode()).ifPresent(w -> {
            throw new ConflictException("Lagerort " + dto.getCode() + " existiert bereits");
        });
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(dto.getCode());
        applyWarehouseFields(warehouse, dto);
        warehouse.setActive(dto.getActive() != null ? dto.getActive() : true);
        warehouse.setIsDefault(false);
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            makeDefault(warehouse);
        }
        return toWarehouseDto(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseDto updateWarehouse(Long id, WarehouseDto dto) {
        Warehouse warehouse = loadWarehouse(id);
        applyWarehouseFields(warehouse, dto);
        if (dto.getActive() != null) {
            if (!dto.getActive() && Boolean.TRUE.equals(warehouse.getIsDefault())) {
                throw new BusinessException("M7", "110", "Der Standardlagerort kann nicht deaktiviert werden");
            }
            warehouse.setActive(dto.getActive());
        }
        if (Boolean.TRUE.equals(dto.getIsDefault()) && !Boolean.TRUE.equals(warehouse.getIsDefault())) {
            makeDefault(warehouse);
        }
        return toWarehouseDto(warehouseRepository.save(warehouse));
    }

    public List<Object[]> getWarehouseOverview() {
        return warehouseRepository.getWarehouseOverview();
    }

    // === Bestände ===

    public List<WarehouseStockDto> getStockByWarehouse(Long warehouseId) {
        return warehouseStockRepository.findByWarehouseId(warehouseId).stream().map(this::toWarehouseStockDto).toList();
    }

    public List<WarehouseStockDto> getStockByProduct(Long productId) {
        return warehouseStockRepository.findByProductId(productId).stream().map(this::toWarehouseStockDto).toList();
    }

    public List<WarehouseStockDto> getLowStockItems() {
        return warehouseStockRepository.findLowStockItems().stream().map(this::toWarehouseStockDto).toList();
    }

    public List<WarehouseStockDto> getOverStockItems() {
        return warehouseStockRepository.findOverStockItems().stream().map(this::toWarehouseStockDto).toList();
    }

    /**
     * Pflegt Lagerplatz sowie Mindest- und Höchstbestand. Die Menge selbst ist nur über
     * Materialbelege (z. B. Inventurdifferenz 701/702) änderbar.
     */
    @Transactional
    public WarehouseStockDto updateStockSettings(Long warehouseId, Long productId, Integer minStock, Integer maxStock,
                                                 String binLocation) {
        Warehouse warehouse = loadWarehouse(warehouseId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Material " + productId + " nicht gefunden"));
        WarehouseStock stock = warehouseStockRepository.findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseGet(() -> new WarehouseStock(warehouse, product, 0));
        if (minStock != null) stock.setMinStock(minStock);
        if (maxStock != null) stock.setMaxStock(maxStock);
        if (stock.getMinStock() > stock.getMaxStock()) {
            throw new BusinessException("M7", "120", "Mindestbestand darf den Höchstbestand nicht überschreiten");
        }
        if (binLocation != null) stock.setBinLocation(binLocation.isBlank() ? null : binLocation.trim());
        return toWarehouseStockDto(warehouseStockRepository.save(stock));
    }

    public Integer getTotalStockByProduct(Long productId) {
        Integer total = warehouseStockRepository.getTotalStockByProduct(productId);
        return total != null ? total : 0;
    }

    public List<Object[]> getStockOverview() {
        return warehouseStockRepository.getStockOverviewByProduct();
    }

    // === Materialbelege ===

    public PageResponse<StockMovementDto> searchMovements(MovementSearch criteria, Pageable pageable) {
        Page<StockMovement> page = stockMovementRepository.findAll(specification(criteria), pageable);
        return PageResponse.of(page, InventoryService::toStockMovementDto);
    }

    public Page<StockMovementDto> getStockMovementsByWarehouse(Long warehouseId, Pageable pageable) {
        return stockMovementRepository.findByWarehouse(warehouseId, pageable).map(InventoryService::toStockMovementDto);
    }

    public List<StockMovementDto> getStockMovementsByProduct(Long productId) {
        return stockMovementRepository.findByProductIdOrderByPostingDateDescIdDesc(productId).stream()
                .map(InventoryService::toStockMovementDto).toList();
    }

    @Transactional
    public StockMovementDto createStockMovement(StockMovementRequestDto request) {
        if (request.getMovementType() == StockMovement.MovementType.DELIVERY_ISSUE) {
            throw new BusinessException("M7", "301",
                    "Bewegungsart 601 wird ausschließlich über den Warenausgang der Lieferung gebucht");
        }
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Material " + request.getProductId() + " nicht gefunden"));
        Warehouse source = request.getSourceWarehouseId() != null ? loadWarehouse(request.getSourceWarehouseId()) : null;
        Warehouse target = request.getTargetWarehouseId() != null ? loadWarehouse(request.getTargetWarehouseId()) : null;

        LocalDateTime postingDate = request.getPostingDate() != null
                ? request.getPostingDate().atTime(LocalTime.now()) : LocalDateTime.now();

        StockMovement movement = stockPostingService.post(new StockPostingService.Posting(
                request.getMovementType(), product, source, target, request.getQuantity(), postingDate,
                request.getReason(),
                request.getReferenceType() != null ? request.getReferenceType() : defaultReferenceType(request.getMovementType()),
                request.getReferenceNumber()));
        return toStockMovementDto(movement);
    }

    @Transactional
    public StockMovementDto reverseMovement(Long movementId, LocalDate postingDate, String reason) {
        LocalDateTime date = postingDate != null ? postingDate.atTime(LocalTime.now()) : LocalDateTime.now();
        return toStockMovementDto(stockPostingService.reverse(movementId, date, reason));
    }

    // === Statistik ===

    public List<Object[]> getMovementStatistics(int days) {
        return stockMovementRepository.getMovementStatistics(LocalDateTime.now().minusDays(days));
    }

    public List<Object[]> getDailyMovementTrend(int days) {
        return stockMovementRepository.getDailyMovementTrend(LocalDateTime.now().minusDays(days));
    }

    public List<Object[]> getTopMovedProducts(int days) {
        return stockMovementRepository.getTopMovedProducts(LocalDateTime.now().minusDays(days));
    }

    // === Mapping ===

    private void applyWarehouseFields(Warehouse warehouse, WarehouseDto dto) {
        warehouse.setName(dto.getName());
        warehouse.setDescription(dto.getDescription());
        warehouse.setAddress(dto.getAddress());
        warehouse.setCity(dto.getCity());
        warehouse.setZipCode(dto.getZipCode());
        warehouse.setCountry(dto.getCountry());
    }

    private void makeDefault(Warehouse warehouse) {
        warehouseRepository.findByIsDefaultTrue().ifPresent(w -> {
            w.setIsDefault(false);
            warehouseRepository.save(w);
        });
        warehouse.setIsDefault(true);
        warehouse.setActive(true);
    }

    private Warehouse loadWarehouse(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lagerort " + id + " nicht gefunden"));
    }

    private static StockMovement.ReferenceType defaultReferenceType(StockMovement.MovementType type) {
        return switch (type) {
            case GOODS_RECEIPT -> StockMovement.ReferenceType.PURCHASE_ORDER;
            case RETURN -> StockMovement.ReferenceType.RETURN_ORDER;
            case INVENTORY_ADJUSTMENT -> StockMovement.ReferenceType.INVENTORY;
            default -> StockMovement.ReferenceType.MANUAL;
        };
    }

    private WarehouseDto toWarehouseDto(Warehouse warehouse) {
        WarehouseDto dto = new WarehouseDto();
        dto.setId(warehouse.getId());
        dto.setCode(warehouse.getCode());
        dto.setName(warehouse.getName());
        dto.setDescription(warehouse.getDescription());
        dto.setAddress(warehouse.getAddress());
        dto.setCity(warehouse.getCity());
        dto.setZipCode(warehouse.getZipCode());
        dto.setCountry(warehouse.getCountry());
        dto.setActive(warehouse.getActive());
        dto.setIsDefault(warehouse.getIsDefault());
        dto.setCreatedAt(warehouse.getCreatedAt());

        List<WarehouseStock> stocks = warehouseStockRepository.findByWarehouseId(warehouse.getId());
        dto.setProductCount(stocks.stream().filter(s -> s.getQuantity() > 0).count());
        dto.setTotalStock(stocks.stream().mapToLong(WarehouseStock::getQuantity).sum());
        dto.setInventoryValue(stocks.stream()
                .map(s -> s.getProduct().getValuationPrice().multiply(BigDecimal.valueOf(s.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return dto;
    }

    private WarehouseStockDto toWarehouseStockDto(WarehouseStock stock) {
        WarehouseStockDto dto = new WarehouseStockDto();
        Product product = stock.getProduct();
        dto.setId(stock.getId());
        dto.setWarehouseId(stock.getWarehouse().getId());
        dto.setWarehouseCode(stock.getWarehouse().getCode());
        dto.setWarehouseName(stock.getWarehouse().getName());
        dto.setProductId(product.getId());
        dto.setMaterialNumber(product.getMaterialNumber());
        dto.setProductName(product.getName());
        dto.setUnitOfMeasure(product.getUnitOfMeasure());
        dto.setProductCategory(product.getCategory());
        dto.setProductPrice(product.getValuationPrice());
        dto.setQuantity(stock.getQuantity());
        dto.setMinStock(stock.getMinStock());
        dto.setMaxStock(stock.getMaxStock());
        dto.setBinLocation(stock.getBinLocation());
        dto.setLastCountedAt(stock.getLastCountedAt());
        dto.setStockValue(product.getValuationPrice().multiply(BigDecimal.valueOf(stock.getQuantity())));

        if (stock.getQuantity() == 0) {
            dto.setStockStatus("OUT");
        } else if (stock.isLowStock()) {
            dto.setStockStatus("LOW");
        } else if (stock.isOverStock()) {
            dto.setStockStatus("OVER");
        } else {
            dto.setStockStatus("OK");
        }
        if (stock.isLowStock() || (stock.isPlanned() && stock.getQuantity() == 0)) {
            dto.setReorderQuantity(Math.max(0, stock.getMaxStock() - stock.getQuantity()));
        }
        return dto;
    }

    public static StockMovementDto toStockMovementDto(StockMovement movement) {
        StockMovementDto dto = new StockMovementDto();
        dto.setId(movement.getId());
        dto.setMovementNumber(movement.getMovementNumber());
        dto.setDocumentItem(movement.getDocumentItem());
        dto.setMovementType(movement.getMovementType());
        dto.setMovementCode(movement.getMovementCode());
        dto.setMovementTypeDisplay(movementText(movement));
        dto.setProductId(movement.getProduct().getId());
        dto.setMaterialNumber(movement.getProduct().getMaterialNumber());
        dto.setProductName(movement.getProduct().getName());

        if (movement.getSourceWarehouse() != null) {
            dto.setSourceWarehouseId(movement.getSourceWarehouse().getId());
            dto.setSourceWarehouseCode(movement.getSourceWarehouse().getCode());
            dto.setSourceWarehouseName(movement.getSourceWarehouse().getName());
        }
        if (movement.getTargetWarehouse() != null) {
            dto.setTargetWarehouseId(movement.getTargetWarehouse().getId());
            dto.setTargetWarehouseCode(movement.getTargetWarehouse().getCode());
            dto.setTargetWarehouseName(movement.getTargetWarehouse().getName());
        }

        dto.setStockEffect(StockPostingService.stockEffect(movement));
        dto.setQuantity(movement.getQuantity());
        dto.setQuantityBefore(movement.getQuantityBefore());
        dto.setQuantityAfter(movement.getQuantityAfter());
        dto.setReason(movement.getReason());
        dto.setReferenceNumber(movement.getReferenceNumber());
        dto.setReferenceType(movement.getReferenceType());
        dto.setReversed(movement.isReversed());
        dto.setReversedMovementNumber(movement.getReversedMovement() != null
                ? movement.getReversedMovement().getMovementNumber() + "/" + movement.getReversedMovement().getDocumentItem() : null);
        dto.setPostingDate(movement.getPostingDate());
        dto.setCreatedBy(movement.getCreatedBy());
        dto.setCreatedAt(movement.getCreatedAt());
        return dto;
    }

    private static String movementText(StockMovement m) {
        String code = m.getMovementCode();
        return switch (code) {
            case "701" -> "Inventurdifferenz (Zugang)";
            case "702" -> "Inventurdifferenz (Abgang)";
            default -> m.getReversedMovement() != null
                    ? "Storno " + m.getMovementType().getDescription()
                    : m.getMovementType().getDescription();
        };
    }

    public record MovementSearch(
            String search,
            List<String> movementCodes,
            Long productId,
            Long warehouseId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
    }

    private static Specification<StockMovement> specification(MovementSearch c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.search() != null && !c.search().isBlank()) {
                String like = "%" + c.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("movementNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("referenceNumber"), "")), like),
                        cb.like(cb.lower(root.get("product").get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("product").get("materialNumber"), "")), like)));
            }
            if (c.movementCodes() != null && !c.movementCodes().isEmpty()) {
                predicates.add(root.get("movementCode").in(c.movementCodes()));
            }
            if (c.productId() != null) predicates.add(cb.equal(root.get("product").get("id"), c.productId()));
            if (c.warehouseId() != null) {
                var source = root.join("sourceWarehouse", jakarta.persistence.criteria.JoinType.LEFT);
                var target = root.join("targetWarehouse", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.or(
                        cb.equal(source.get("id"), c.warehouseId()),
                        cb.equal(target.get("id"), c.warehouseId())));
            }
            if (c.dateFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("postingDate"), c.dateFrom().atStartOfDay()));
            if (c.dateTo() != null) predicates.add(cb.lessThan(root.get("postingDate"), c.dateTo().plusDays(1).atStartOfDay()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
