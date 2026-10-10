package com.thomas.order_management.service;

import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.model.*;
import com.thomas.order_management.model.StockMovement.MovementType;
import com.thomas.order_management.model.StockMovement.ReferenceType;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.StockMovementRepository;
import com.thomas.order_management.repository.WarehouseRepository;
import com.thomas.order_management.repository.WarehouseStockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Bucht Materialbelege. Jede Bestandsveränderung im System läuft über diese Klasse,
 * damit Lagerortbestand, Gesamtbestand des Materials und Belegfluss konsistent bleiben.
 */
@Service
@Transactional
public class StockPostingService {

    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final NumberRangeService numberRangeService;
    private final CurrentUserProvider currentUser;

    public StockPostingService(WarehouseStockRepository stockRepository,
                               StockMovementRepository movementRepository,
                               ProductRepository productRepository,
                               WarehouseRepository warehouseRepository,
                               NumberRangeService numberRangeService,
                               CurrentUserProvider currentUser) {
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.numberRangeService = numberRangeService;
        this.currentUser = currentUser;
    }

    public record Posting(
            MovementType type,
            Product product,
            Warehouse source,
            Warehouse target,
            int quantity,
            LocalDateTime postingDate,
            String reason,
            ReferenceType referenceType,
            String referenceNumber
    ) {
    }

    public StockMovement post(Posting posting) {
        Posting p = attach(posting);
        if (p.quantity() < 0 || (p.quantity() == 0 && p.type() != MovementType.INVENTORY_ADJUSTMENT)) {
            throw new BusinessException("M7", "002", "Bitte eine Menge größer 0 erfassen");
        }

        StockMovement movement = newMovement(p);

        switch (p.type()) {
            case INITIAL_ENTRY, GOODS_RECEIPT, RETURN -> {
                Warehouse target = require(p.target(), "Ziellagerort");
                movement.setTargetWarehouse(target);
                WarehouseStock stock = stockFor(target, p.product());
                movement.setQuantityBefore(stock.getQuantity());
                stock.setQuantity(stock.getQuantity() + p.quantity());
                movement.setQuantityAfter(stock.getQuantity());
            }
            case GOODS_ISSUE, DELIVERY_ISSUE, SCRAP -> {
                Warehouse source = require(p.source(), "Quelllagerort");
                movement.setSourceWarehouse(source);
                WarehouseStock stock = stockFor(source, p.product());
                checkSufficient(stock, p.quantity());
                movement.setQuantityBefore(stock.getQuantity());
                stock.setQuantity(stock.getQuantity() - p.quantity());
                movement.setQuantityAfter(stock.getQuantity());
            }
            case TRANSFER -> {
                Warehouse source = require(p.source(), "Quelllagerort");
                Warehouse target = require(p.target(), "Ziellagerort");
                if (source.getId().equals(target.getId())) {
                    throw new BusinessException("M7", "053", "Quell- und Ziellagerort dürfen nicht identisch sein");
                }
                movement.setSourceWarehouse(source);
                movement.setTargetWarehouse(target);
                WarehouseStock from = stockFor(source, p.product());
                checkSufficient(from, p.quantity());
                WarehouseStock to = stockFor(target, p.product());
                movement.setQuantityBefore(from.getQuantity());
                from.setQuantity(from.getQuantity() - p.quantity());
                to.setQuantity(to.getQuantity() + p.quantity());
                movement.setQuantityAfter(to.getQuantity());
            }
            case INVENTORY_ADJUSTMENT -> {
                Warehouse warehouse = require(p.target() != null ? p.target() : p.source(), "Lagerort");
                movement.setTargetWarehouse(warehouse);
                WarehouseStock stock = stockFor(warehouse, p.product());
                int difference = p.quantity() - stock.getQuantity();
                if (difference == 0) {
                    throw new BusinessException("MI", "034", "Keine Inventurdifferenz: gezählte Menge entspricht dem Buchbestand");
                }
                movement.setMovementCode(difference > 0 ? "701" : "702");
                movement.setQuantity(Math.abs(difference));
                movement.setQuantityBefore(stock.getQuantity());
                stock.setQuantity(p.quantity());
                stock.setLastCountedAt(movement.getPostingDate());
                movement.setQuantityAfter(stock.getQuantity());
            }
        }

        StockMovement saved = movementRepository.save(movement);
        refreshProductStock(p.product());
        return saved;
    }

    /** Lädt Material und Lagerorte im aktuellen Persistenzkontext, auch wenn der Aufrufer losgelöste Objekte übergibt. */
    private Posting attach(Posting p) {
        Product product = productRepository.findById(p.product().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Material " + p.product().getId() + " nicht gefunden"));
        Warehouse source = p.source() != null ? warehouseRepository.findById(p.source().getId()).orElse(null) : null;
        Warehouse target = p.target() != null ? warehouseRepository.findById(p.target().getId()).orElse(null) : null;
        return new Posting(p.type(), product, source, target, p.quantity(), p.postingDate(), p.reason(),
                p.referenceType(), p.referenceNumber());
    }

    /** Storniert einen Materialbeleg durch einen Gegenbeleg mit der Storno-Bewegungsart. */
    public StockMovement reverse(Long movementId, LocalDateTime postingDate, String reason) {
        StockMovement original = movementRepository.findById(movementId)
                .orElseThrow(() -> new ResourceNotFoundException("Materialbeleg " + movementId + " nicht gefunden"));
        if (original.isReversed()) {
            throw new BusinessException("M7", "089", "Materialbeleg " + original.getMovementNumber() + " ist bereits storniert", HttpStatus.CONFLICT);
        }
        if (original.getReversedMovement() != null) {
            throw new BusinessException("M7", "090", "Ein Stornobeleg kann nicht storniert werden", HttpStatus.CONFLICT);
        }
        if (original.getMovementType() == MovementType.DELIVERY_ISSUE) {
            throw new BusinessException("VL", "602",
                    "Warenausgang zur Lieferung " + original.getReferenceNumber() + " bitte über die Lieferung stornieren",
                    HttpStatus.CONFLICT);
        }
        return reverseForDocument(original, postingDate, reason);
    }

    /** Storno ohne Prüfung der Referenzart; wird vom Lieferungsstorno verwendet. */
    public StockMovement reverseForDocument(StockMovement original, LocalDateTime postingDate, String reason) {
        Product product = original.getProduct();
        LocalDateTime date = postingDate != null ? postingDate : LocalDateTime.now();

        StockMovement reversal = new StockMovement();
        reversal.setMovementNumber(numberRangeService.next(NumberRangeObject.MATERIAL_DOCUMENT));
        reversal.setMovementType(original.getMovementType());
        reversal.setMovementCode(reversalCode(original));
        reversal.setProduct(product);
        reversal.setQuantity(original.getQuantity());
        reversal.setReason(reason != null ? reason : "Storno zu Materialbeleg " + original.getMovementNumber());
        reversal.setReferenceType(original.getReferenceType());
        reversal.setReferenceNumber(original.getReferenceNumber());
        reversal.setReversedMovement(original);
        reversal.setPostingDate(date);
        reversal.setCreatedAt(date);
        reversal.setCreatedBy(currentUser.currentUsername());

        int qty = original.getQuantity();
        switch (original.getMovementType()) {
            case INITIAL_ENTRY, GOODS_RECEIPT, RETURN -> {
                WarehouseStock stock = stockFor(original.getTargetWarehouse(), product);
                checkSufficient(stock, qty);
                reversal.setSourceWarehouse(original.getTargetWarehouse());
                reversal.setQuantityBefore(stock.getQuantity());
                stock.setQuantity(stock.getQuantity() - qty);
                reversal.setQuantityAfter(stock.getQuantity());
            }
            case GOODS_ISSUE, DELIVERY_ISSUE, SCRAP -> {
                WarehouseStock stock = stockFor(original.getSourceWarehouse(), product);
                reversal.setTargetWarehouse(original.getSourceWarehouse());
                reversal.setQuantityBefore(stock.getQuantity());
                stock.setQuantity(stock.getQuantity() + qty);
                reversal.setQuantityAfter(stock.getQuantity());
            }
            case TRANSFER -> {
                WarehouseStock back = stockFor(original.getTargetWarehouse(), product);
                checkSufficient(back, qty);
                WarehouseStock to = stockFor(original.getSourceWarehouse(), product);
                reversal.setSourceWarehouse(original.getTargetWarehouse());
                reversal.setTargetWarehouse(original.getSourceWarehouse());
                reversal.setQuantityBefore(back.getQuantity());
                back.setQuantity(back.getQuantity() - qty);
                to.setQuantity(to.getQuantity() + qty);
                reversal.setQuantityAfter(to.getQuantity());
            }
            case INVENTORY_ADJUSTMENT -> {
                WarehouseStock stock = stockFor(original.getTargetWarehouse(), product);
                boolean wasGain = "701".equals(original.getMovementCode());
                if (wasGain) checkSufficient(stock, qty);
                reversal.setTargetWarehouse(original.getTargetWarehouse());
                reversal.setQuantityBefore(stock.getQuantity());
                stock.setQuantity(stock.getQuantity() + (wasGain ? -qty : qty));
                reversal.setQuantityAfter(stock.getQuantity());
            }
        }

        original.setReversed(true);
        StockMovement saved = movementRepository.save(reversal);
        refreshProductStock(product);
        return saved;
    }

    /** Bestandswirkung eines Belegs, positiv = Zugang. Umlagerungen sind bestandsneutral. */
    public static int stockEffect(StockMovement m) {
        String code = m.getMovementCode();
        return switch (code) {
            case "101", "561", "651", "701", "202", "602", "552" -> m.getQuantity();
            case "102", "562", "652", "702", "201", "601", "551" -> -m.getQuantity();
            default -> 0;
        };
    }

    private StockMovement newMovement(Posting p) {
        StockMovement movement = new StockMovement();
        movement.setMovementNumber(numberRangeService.next(NumberRangeObject.MATERIAL_DOCUMENT));
        movement.setMovementType(p.type());
        movement.setMovementCode(p.type().getCode());
        movement.setProduct(p.product());
        movement.setQuantity(p.quantity());
        movement.setReason(p.reason());
        movement.setReferenceType(p.referenceType() != null ? p.referenceType() : ReferenceType.MANUAL);
        movement.setReferenceNumber(p.referenceNumber());
        LocalDateTime date = p.postingDate() != null ? p.postingDate() : LocalDateTime.now();
        movement.setPostingDate(date);
        movement.setCreatedAt(date);
        movement.setCreatedBy(currentUser.currentUsername());
        return movement;
    }

    private String reversalCode(StockMovement original) {
        if (original.getMovementType() == MovementType.INVENTORY_ADJUSTMENT) {
            return "701".equals(original.getMovementCode()) ? "702" : "701";
        }
        return original.getMovementType().getReversalCode();
    }

    private WarehouseStock stockFor(Warehouse warehouse, Product product) {
        return stockRepository.findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                .orElseGet(() -> stockRepository.save(new WarehouseStock(warehouse, product, 0)));
    }

    private void checkSufficient(WarehouseStock stock, int quantity) {
        if (stock.getQuantity() < quantity) {
            throw new BusinessException("M7", "021",
                    "Bestand unterschritten: Material " + stock.getProduct().getMaterialNumber()
                            + " im Lagerort " + stock.getWarehouse().getCode()
                            + " (verfügbar " + stock.getQuantity() + ", benötigt " + quantity + ")");
        }
    }

    private Warehouse require(Warehouse warehouse, String label) {
        if (warehouse == null) {
            throw new BusinessException("M7", "018", label + " ist für diese Bewegungsart erforderlich", HttpStatus.BAD_REQUEST);
        }
        return warehouse;
    }

    private void refreshProductStock(Product product) {
        stockRepository.flush();
        Integer total = stockRepository.getTotalStockByProduct(product.getId());
        product.setStockQuantity(total != null ? total : 0);
        productRepository.save(product);
    }
}
