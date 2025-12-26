// order-management/src/main/java/com/thomas/order_management/service/InventoryService.java
package com.thomas.order_management.service;

import com.thomas.order_management.dto.inventory.*;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;

    public InventoryService(WarehouseRepository warehouseRepository,
                            WarehouseStockRepository warehouseStockRepository,
                            StockMovementRepository stockMovementRepository,
                            ProductRepository productRepository) {
        this.warehouseRepository = warehouseRepository;
        this.warehouseStockRepository = warehouseStockRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
    }

    // === Warehouse Management ===

    public List<WarehouseDto> getAllWarehouses() {
        return warehouseRepository.findAll().stream()
                .map(this::toWarehouseDto)
                .collect(Collectors.toList());
    }

    public List<WarehouseDto> getActiveWarehouses() {
        return warehouseRepository.findByActiveTrue().stream()
                .map(this::toWarehouseDto)
                .collect(Collectors.toList());
    }

    public WarehouseDto getWarehouseById(Long id) {
        return warehouseRepository.findById(id)
                .map(this::toWarehouseDto)
                .orElseThrow(() -> new RuntimeException("Lager nicht gefunden: " + id));
    }

    @Transactional
    public WarehouseDto createWarehouse(WarehouseDto dto) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(dto.getCode());
        warehouse.setName(dto.getName());
        warehouse.setDescription(dto.getDescription());
        warehouse.setAddress(dto.getAddress());
        warehouse.setCity(dto.getCity());
        warehouse.setZipCode(dto.getZipCode());
        warehouse.setCountry(dto.getCountry());
        warehouse.setActive(dto.getActive() != null ? dto.getActive() : true);
        warehouse.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);

        // Wenn dieses Lager das Standard-Lager sein soll, andere zurücksetzen
        if (Boolean.TRUE.equals(warehouse.getIsDefault())) {
            warehouseRepository.findByIsDefaultTrue().ifPresent(w -> {
                w.setIsDefault(false);
                warehouseRepository.save(w);
            });
        }

        return toWarehouseDto(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseDto updateWarehouse(Long id, WarehouseDto dto) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lager nicht gefunden: " + id));

        warehouse.setName(dto.getName());
        warehouse.setDescription(dto.getDescription());
        warehouse.setAddress(dto.getAddress());
        warehouse.setCity(dto.getCity());
        warehouse.setZipCode(dto.getZipCode());
        warehouse.setCountry(dto.getCountry());
        warehouse.setActive(dto.getActive());

        if (Boolean.TRUE.equals(dto.getIsDefault()) && !warehouse.getIsDefault()) {
            warehouseRepository.findByIsDefaultTrue().ifPresent(w -> {
                w.setIsDefault(false);
                warehouseRepository.save(w);
            });
            warehouse.setIsDefault(true);
        }

        return toWarehouseDto(warehouseRepository.save(warehouse));
    }

    public List<Object[]> getWarehouseOverview() {
        return warehouseRepository.getWarehouseOverview();
    }

    // === Stock Management ===

    public List<WarehouseStockDto> getStockByWarehouse(Long warehouseId) {
        return warehouseStockRepository.findByWarehouseId(warehouseId).stream()
                .map(this::toWarehouseStockDto)
                .collect(Collectors.toList());
    }

    public List<WarehouseStockDto> getStockByProduct(Long productId) {
        return warehouseStockRepository.findByProductId(productId).stream()
                .map(this::toWarehouseStockDto)
                .collect(Collectors.toList());
    }

    public List<WarehouseStockDto> getLowStockItems() {
        return warehouseStockRepository.findLowStockItems().stream()
                .map(this::toWarehouseStockDto)
                .collect(Collectors.toList());
    }

    public List<WarehouseStockDto> getOverStockItems() {
        return warehouseStockRepository.findOverStockItems().stream()
                .map(this::toWarehouseStockDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public WarehouseStockDto updateStock(Long warehouseId, Long productId, Integer quantity, 
                                         Integer minStock, Integer maxStock, String binLocation) {
        WarehouseStock stock = warehouseStockRepository
                .findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseGet(() -> {
                    WarehouseStock newStock = new WarehouseStock();
                    newStock.setWarehouse(warehouseRepository.findById(warehouseId)
                            .orElseThrow(() -> new RuntimeException("Lager nicht gefunden")));
                    newStock.setProduct(productRepository.findById(productId)
                            .orElseThrow(() -> new RuntimeException("Produkt nicht gefunden")));
                    newStock.setQuantity(0);
                    return newStock;
                });

        if (quantity != null) stock.setQuantity(quantity);
        if (minStock != null) stock.setMinStock(minStock);
        if (maxStock != null) stock.setMaxStock(maxStock);
        if (binLocation != null) stock.setBinLocation(binLocation);

        return toWarehouseStockDto(warehouseStockRepository.save(stock));
    }

    public Integer getTotalStockByProduct(Long productId) {
        Integer total = warehouseStockRepository.getTotalStockByProduct(productId);
        return total != null ? total : 0;
    }

    public List<Object[]> getStockOverview() {
        return warehouseStockRepository.getStockOverviewByProduct();
    }

    // === Stock Movements ===

    public Page<StockMovementDto> getStockMovements(Pageable pageable) {
        return stockMovementRepository.findAllOrderByCreatedAtDesc(pageable)
                .map(this::toStockMovementDto);
    }

    public Page<StockMovementDto> getStockMovementsByWarehouse(Long warehouseId, Pageable pageable) {
        return stockMovementRepository.findByWarehouse(warehouseId, pageable)
                .map(this::toStockMovementDto);
    }

    public List<StockMovementDto> getStockMovementsByProduct(Long productId) {
        return stockMovementRepository.findByProductId(productId).stream()
                .map(this::toStockMovementDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public StockMovementDto createStockMovement(StockMovementRequestDto request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Produkt nicht gefunden"));

        StockMovement movement = new StockMovement();
        movement.setMovementType(request.getMovementType());
        movement.setProduct(product);
        movement.setQuantity(request.getQuantity());
        movement.setReason(request.getReason());
        movement.setReferenceNumber(request.getReferenceNumber());
        movement.setReferenceType(request.getReferenceType());

        switch (request.getMovementType()) {
            case GOODS_RECEIPT -> {
                Warehouse target = warehouseRepository.findById(request.getTargetWarehouseId())
                        .orElseThrow(() -> new RuntimeException("Ziellager nicht gefunden"));
                movement.setTargetWarehouse(target);
                processGoodsReceipt(target, product, request.getQuantity(), movement);
            }
            case GOODS_ISSUE -> {
                Warehouse source = warehouseRepository.findById(request.getSourceWarehouseId())
                        .orElseThrow(() -> new RuntimeException("Quelllager nicht gefunden"));
                movement.setSourceWarehouse(source);
                processGoodsIssue(source, product, request.getQuantity(), movement);
            }
            case TRANSFER -> {
                Warehouse source = warehouseRepository.findById(request.getSourceWarehouseId())
                        .orElseThrow(() -> new RuntimeException("Quelllager nicht gefunden"));
                Warehouse target = warehouseRepository.findById(request.getTargetWarehouseId())
                        .orElseThrow(() -> new RuntimeException("Ziellager nicht gefunden"));
                movement.setSourceWarehouse(source);
                movement.setTargetWarehouse(target);
                processTransfer(source, target, product, request.getQuantity(), movement);
            }
            case INVENTORY_ADJUSTMENT -> {
                Warehouse warehouse = warehouseRepository.findById(
                        request.getTargetWarehouseId() != null ? request.getTargetWarehouseId() : request.getSourceWarehouseId())
                        .orElseThrow(() -> new RuntimeException("Lager nicht gefunden"));
                movement.setTargetWarehouse(warehouse);
                processInventoryAdjustment(warehouse, product, request.getQuantity(), movement);
            }
            case RETURN -> {
                Warehouse target = warehouseRepository.findById(request.getTargetWarehouseId())
                        .orElseThrow(() -> new RuntimeException("Ziellager nicht gefunden"));
                movement.setTargetWarehouse(target);
                processGoodsReceipt(target, product, request.getQuantity(), movement);
            }
            case SCRAP -> {
                Warehouse source = warehouseRepository.findById(request.getSourceWarehouseId())
                        .orElseThrow(() -> new RuntimeException("Quelllager nicht gefunden"));
                movement.setSourceWarehouse(source);
                processGoodsIssue(source, product, request.getQuantity(), movement);
            }
        }

        return toStockMovementDto(stockMovementRepository.save(movement));
    }

    private void processGoodsReceipt(Warehouse warehouse, Product product, Integer quantity, StockMovement movement) {
        WarehouseStock stock = warehouseStockRepository
                .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                .orElseGet(() -> {
                    WarehouseStock newStock = new WarehouseStock(warehouse, product, 0);
                    return warehouseStockRepository.save(newStock);
                });

        movement.setQuantityBefore(stock.getQuantity());
        stock.setQuantity(stock.getQuantity() + quantity);
        movement.setQuantityAfter(stock.getQuantity());
        warehouseStockRepository.save(stock);

        // Auch Gesamtbestand im Produkt aktualisieren
        updateProductTotalStock(product);
    }

    private void processGoodsIssue(Warehouse warehouse, Product product, Integer quantity, StockMovement movement) {
        WarehouseStock stock = warehouseStockRepository
                .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                .orElseThrow(() -> new RuntimeException("Kein Bestand in diesem Lager"));

        if (stock.getQuantity() < quantity) {
            throw new RuntimeException("Nicht genügend Bestand vorhanden. Verfügbar: " + stock.getQuantity());
        }

        movement.setQuantityBefore(stock.getQuantity());
        stock.setQuantity(stock.getQuantity() - quantity);
        movement.setQuantityAfter(stock.getQuantity());
        warehouseStockRepository.save(stock);

        updateProductTotalStock(product);
    }

    private void processTransfer(Warehouse source, Warehouse target, Product product, 
                                  Integer quantity, StockMovement movement) {
        // Aus Quelllager entnehmen
        WarehouseStock sourceStock = warehouseStockRepository
                .findByWarehouseIdAndProductId(source.getId(), product.getId())
                .orElseThrow(() -> new RuntimeException("Kein Bestand im Quelllager"));

        if (sourceStock.getQuantity() < quantity) {
            throw new RuntimeException("Nicht genügend Bestand im Quelllager. Verfügbar: " + sourceStock.getQuantity());
        }

        movement.setQuantityBefore(sourceStock.getQuantity());
        sourceStock.setQuantity(sourceStock.getQuantity() - quantity);
        warehouseStockRepository.save(sourceStock);

        // In Ziellager einbuchen
        WarehouseStock targetStock = warehouseStockRepository
                .findByWarehouseIdAndProductId(target.getId(), product.getId())
                .orElseGet(() -> {
                    WarehouseStock newStock = new WarehouseStock(target, product, 0);
                    return warehouseStockRepository.save(newStock);
                });

        targetStock.setQuantity(targetStock.getQuantity() + quantity);
        movement.setQuantityAfter(targetStock.getQuantity());
        warehouseStockRepository.save(targetStock);
    }

    private void processInventoryAdjustment(Warehouse warehouse, Product product, 
                                            Integer newQuantity, StockMovement movement) {
        WarehouseStock stock = warehouseStockRepository
                .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                .orElseGet(() -> {
                    WarehouseStock newStock = new WarehouseStock(warehouse, product, 0);
                    return warehouseStockRepository.save(newStock);
                });

        movement.setQuantityBefore(stock.getQuantity());
        int difference = newQuantity - stock.getQuantity();
        movement.setQuantity(Math.abs(difference));
        stock.setQuantity(newQuantity);
        movement.setQuantityAfter(newQuantity);
        stock.setLastCountedAt(LocalDateTime.now());
        warehouseStockRepository.save(stock);

        updateProductTotalStock(product);
    }

    private void updateProductTotalStock(Product product) {
        Integer totalStock = warehouseStockRepository.getTotalStockByProduct(product.getId());
        product.setStockQuantity(totalStock != null ? totalStock : 0);
        productRepository.save(product);
    }

    // === Statistics ===

    public List<Object[]> getMovementStatistics(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return stockMovementRepository.getMovementStatistics(startDate);
    }

    public List<Object[]> getDailyMovementTrend(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return stockMovementRepository.getDailyMovementTrend(startDate);
    }

    public List<Object[]> getTopMovedProducts(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return stockMovementRepository.getTopMovedProducts(startDate);
    }

    // === Mapping Methods ===

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

        // Aggregierte Werte
        List<WarehouseStock> stocks = warehouse.getStocks();
        dto.setProductCount((long) stocks.size());
        dto.setTotalStock(stocks.stream().mapToLong(WarehouseStock::getQuantity).sum());
        dto.setInventoryValue(stocks.stream()
                .map(s -> s.getProduct().getPrice().multiply(BigDecimal.valueOf(s.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return dto;
    }

    private WarehouseStockDto toWarehouseStockDto(WarehouseStock stock) {
        WarehouseStockDto dto = new WarehouseStockDto();
        dto.setId(stock.getId());
        dto.setWarehouseId(stock.getWarehouse().getId());
        dto.setWarehouseCode(stock.getWarehouse().getCode());
        dto.setWarehouseName(stock.getWarehouse().getName());
        dto.setProductId(stock.getProduct().getId());
        dto.setProductName(stock.getProduct().getName());
        dto.setProductCategory(stock.getProduct().getCategory());
        dto.setProductPrice(stock.getProduct().getPrice());
        dto.setQuantity(stock.getQuantity());
        dto.setMinStock(stock.getMinStock());
        dto.setMaxStock(stock.getMaxStock());
        dto.setBinLocation(stock.getBinLocation());
        dto.setLastCountedAt(stock.getLastCountedAt());

        // Berechnete Werte
        dto.setStockValue(stock.getProduct().getPrice().multiply(BigDecimal.valueOf(stock.getQuantity())));
        
        if (stock.getQuantity() == 0) {
            dto.setStockStatus("OUT");
        } else if (stock.getQuantity() <= stock.getMinStock()) {
            dto.setStockStatus("LOW");
        } else if (stock.getQuantity() > stock.getMaxStock()) {
            dto.setStockStatus("OVER");
        } else {
            dto.setStockStatus("OK");
        }

        if (stock.getQuantity() < stock.getMinStock()) {
            dto.setReorderQuantity(stock.getMaxStock() - stock.getQuantity());
        }

        return dto;
    }

    private StockMovementDto toStockMovementDto(StockMovement movement) {
        StockMovementDto dto = new StockMovementDto();
        dto.setId(movement.getId());
        dto.setMovementNumber(movement.getMovementNumber());
        dto.setMovementType(movement.getMovementType());
        dto.setProductId(movement.getProduct().getId());
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

        dto.setQuantity(movement.getQuantity());
        dto.setQuantityBefore(movement.getQuantityBefore());
        dto.setQuantityAfter(movement.getQuantityAfter());
        dto.setReason(movement.getReason());
        dto.setReferenceNumber(movement.getReferenceNumber());
        dto.setReferenceType(movement.getReferenceType());
        dto.setCreatedBy(movement.getCreatedBy());
        dto.setCreatedAt(movement.getCreatedAt());

        return dto;
    }
}
