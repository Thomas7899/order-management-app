package com.thomas.order_management.service.sales;

import com.thomas.order_management.dto.ChangeDocumentDto;
import com.thomas.order_management.dto.PageResponse;
import com.thomas.order_management.dto.sales.DeliveryDto;
import com.thomas.order_management.dto.sales.LogisticsRequests;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.mapper.SalesDocumentMapper;
import com.thomas.order_management.model.*;
import com.thomas.order_management.model.ChangeDocument.ChangeType;
import com.thomas.order_management.model.ChangeDocument.ObjectClass;
import com.thomas.order_management.model.StockMovement.MovementType;
import com.thomas.order_management.model.StockMovement.ReferenceType;
import com.thomas.order_management.repository.*;
import com.thomas.order_management.service.ChangeDocumentService;
import com.thomas.order_management.service.CurrentUserProvider;
import com.thomas.order_management.service.NumberRangeService;
import com.thomas.order_management.service.StockPostingService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Auslieferungen (angelehnt an VL01N/VL02N/VL09).
 */
@Service
@Transactional
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final StockPostingService stockPostingService;
    private final NumberRangeService numberRangeService;
    private final ChangeDocumentService changeDocuments;
    private final CurrentUserProvider currentUser;
    private final SalesDocumentMapper mapper;

    public DeliveryService(DeliveryRepository deliveryRepository,
                           OrderRepository orderRepository,
                           WarehouseRepository warehouseRepository,
                           WarehouseStockRepository stockRepository,
                           StockMovementRepository movementRepository,
                           StockPostingService stockPostingService,
                           NumberRangeService numberRangeService,
                           ChangeDocumentService changeDocuments,
                           CurrentUserProvider currentUser,
                           SalesDocumentMapper mapper) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.stockPostingService = stockPostingService;
        this.numberRangeService = numberRangeService;
        this.changeDocuments = changeDocuments;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<DeliveryDto> search(DeliverySearch criteria, Pageable pageable) {
        Page<Delivery> page = deliveryRepository.findAll(specification(criteria), pageable);
        return PageResponse.of(page, mapper::toDto);
    }

    @Transactional(readOnly = true)
    public DeliveryDto get(Long id) {
        return mapper.toDto(load(id));
    }

    @Transactional(readOnly = true)
    public List<DeliveryDto> getByOrder(Long orderId) {
        return deliveryRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeDocumentDto> getChanges(Long id) {
        load(id);
        return changeDocuments.getChanges(ObjectClass.DELIVERY, id);
    }

    // ------------------------------------------------------------------
    // Anlegen
    // ------------------------------------------------------------------

    public DeliveryDto create(LogisticsRequests.CreateDelivery request) {
        return mapper.toDto(createDelivery(request, null));
    }

    public Delivery createDelivery(LogisticsRequests.CreateDelivery request, LocalDateTime createdAtOverride) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Kundenauftrag " + request.orderId() + " nicht gefunden"));

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessException("VL", "361", "Kundenauftrag " + order.getOrderNumber() + " ist "
                    + order.getStatus().getDisplayName().toLowerCase(), HttpStatus.CONFLICT);
        }
        if (!order.getCreditStatus().allowsDelivery()) {
            throw new BusinessException("VL", "150", "Kundenauftrag " + order.getOrderNumber()
                    + " ist wegen Kreditsperre für die Lieferung gesperrt");
        }

        Map<OrderItem, Integer> quantities = determineQuantities(order, request.items());
        if (quantities.isEmpty()) {
            throw new BusinessException("VL", "248", "Kundenauftrag " + order.getOrderNumber()
                    + " enthält keine lieferbaren (bestätigten) Positionen");
        }

        Warehouse warehouse = request.warehouseId() != null
                ? warehouseRepository.findById(request.warehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lagerort " + request.warehouseId() + " nicht gefunden"))
                : determineWarehouse(quantities);
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new BusinessException("VL", "251", "Lagerort " + warehouse.getCode() + " ist nicht aktiv");
        }

        // Liefermengen auf den freien Bestand des Lagerorts begrenzen; der Rest bleibt für Folgelieferungen offen
        boolean explicitItems = request.items() != null && !request.items().isEmpty();
        Map<OrderItem, Integer> deliverable = new LinkedHashMap<>();
        for (Map.Entry<OrderItem, Integer> entry : quantities.entrySet()) {
            int free = freeStock(warehouse, entry.getKey().getProduct());
            if (explicitItems && entry.getValue() > free) {
                throw new BusinessException("M7", "021", "Position " + entry.getKey().getItemNumber()
                        + ": im Lagerort " + warehouse.getCode() + " sind nur " + free + " "
                        + entry.getKey().getProduct().getUnitOfMeasure() + " frei");
            }
            int quantity = Math.min(entry.getValue(), free);
            if (quantity > 0) {
                deliverable.put(entry.getKey(), quantity);
            }
        }
        if (deliverable.isEmpty()) {
            throw new BusinessException("VL", "248", "Kein freier Bestand im Lagerort " + warehouse.getCode()
                    + " für die offenen Positionen von Auftrag " + order.getOrderNumber());
        }
        quantities = deliverable;

        LocalDateTime createdAt = createdAtOverride != null ? createdAtOverride : LocalDateTime.now();
        Delivery delivery = new Delivery();
        delivery.setDeliveryNumber(numberRangeService.next(NumberRangeObject.DELIVERY));
        delivery.setOrder(order);
        delivery.setCustomer(order.getCustomer());
        delivery.setWarehouse(warehouse);
        delivery.setShippingAddress(order.getShippingAddress());
        delivery.setPlannedGoodsIssueDate(request.plannedGoodsIssueDate() != null
                ? request.plannedGoodsIssueDate() : createdAt.toLocalDate());
        delivery.setCreatedAt(createdAt);
        delivery.setCreatedBy(currentUser.currentUsername());

        int itemNumber = 10;
        for (Map.Entry<OrderItem, Integer> entry : quantities.entrySet()) {
            OrderItem orderItem = entry.getKey();
            delivery.addItem(new DeliveryItem(itemNumber, orderItem, entry.getValue()));
            orderItem.setDeliveredQuantity(orderItem.getDeliveredQuantity() + entry.getValue());
            itemNumber += 10;
        }
        order.updateStatus();

        Delivery saved = deliveryRepository.save(delivery);
        changeDocuments.logEvent(ObjectClass.DELIVERY, saved.getId(), saved.getDeliveryNumber(), ChangeType.INSERT,
                "Auslieferung zu Auftrag " + order.getOrderNumber() + " aus Lagerort " + warehouse.getCode() + " angelegt",
                createdAt);
        changeDocuments.logEvent(ObjectClass.SALES_ORDER, order.getId(), order.getOrderNumber(), ChangeType.EVENT,
                "Auslieferung " + saved.getDeliveryNumber() + " angelegt", createdAt);
        return saved;
    }

    // ------------------------------------------------------------------
    // Warenausgang
    // ------------------------------------------------------------------

    public DeliveryDto postGoodsIssue(Long id, LogisticsRequests.PostGoodsIssue request) {
        LocalDateTime postingDate = request != null && request.postingDate() != null
                ? request.postingDate().atTime(LocalTime.now()) : LocalDateTime.now();
        return mapper.toDto(postGoodsIssueAt(id, postingDate));
    }

    public Delivery postGoodsIssueAt(Long id, LocalDateTime postingDate) {
        Delivery delivery = load(id);
        if (delivery.getStatus() != Delivery.Status.CREATED) {
            throw new BusinessException("VL", "602", "Für Lieferung " + delivery.getDeliveryNumber()
                    + " ist kein Warenausgang möglich (Status: " + delivery.getStatus().getDisplayName() + ")",
                    HttpStatus.CONFLICT);
        }

        List<StockPostingService.Posting> postings = delivery.getItems().stream()
                .map(item -> new StockPostingService.Posting(
                        MovementType.DELIVERY_ISSUE, item.getProduct(), delivery.getWarehouse(), null,
                        item.getQuantity(), postingDate,
                        "Warenausgang zu Auftrag " + delivery.getOrder().getOrderNumber(),
                        ReferenceType.DELIVERY, delivery.getDeliveryNumber()))
                .toList();
        String firstDocument = stockPostingService.postDocument(postings).get(0).getMovementNumber();

        delivery.setStatus(Delivery.Status.GOODS_ISSUED);
        delivery.setGoodsIssueDate(postingDate);
        delivery.setGoodsIssueDocument(firstDocument);
        changeDocuments.logEvent(ObjectClass.DELIVERY, delivery.getId(), delivery.getDeliveryNumber(), ChangeType.EVENT,
                "Warenausgang gebucht (Materialbeleg " + firstDocument + ")", postingDate);
        return deliveryRepository.save(delivery);
    }

    public DeliveryDto reverseGoodsIssue(Long id, LogisticsRequests.Cancel request) {
        Delivery delivery = load(id);
        if (delivery.getStatus() != Delivery.Status.GOODS_ISSUED) {
            throw new BusinessException("VL", "603", "Für Lieferung " + delivery.getDeliveryNumber()
                    + " ist kein Warenausgang gebucht", HttpStatus.CONFLICT);
        }
        if (delivery.getBillingStatus() != ProcessingStatus.NOT_PROCESSED) {
            throw new BusinessException("VL", "604", "Lieferung " + delivery.getDeliveryNumber()
                    + " ist bereits fakturiert; bitte zuerst die Faktura stornieren", HttpStatus.CONFLICT);
        }

        LocalDateTime date = request != null && request.date() != null ? request.date().atTime(LocalTime.now()) : LocalDateTime.now();
        List<StockMovement> issued = movementRepository
                .findByReferenceTypeAndReferenceNumber(ReferenceType.DELIVERY, delivery.getDeliveryNumber()).stream()
                .filter(m -> "601".equals(m.getMovementCode()) && !m.isReversed())
                .sorted(Comparator.comparing(StockMovement::getDocumentItem))
                .toList();
        stockPostingService.reverseDocument(issued, date, "Storno Warenausgang Lieferung " + delivery.getDeliveryNumber());

        delivery.setStatus(Delivery.Status.CREATED);
        delivery.setGoodsIssueDate(null);
        delivery.setGoodsIssueDocument(null);
        changeDocuments.logEvent(ObjectClass.DELIVERY, delivery.getId(), delivery.getDeliveryNumber(), ChangeType.EVENT,
                "Warenausgang storniert (Bewegungsart 602)", date);
        return mapper.toDto(deliveryRepository.save(delivery));
    }

    public DeliveryDto cancel(Long id) {
        Delivery delivery = load(id);
        if (delivery.getStatus() != Delivery.Status.CREATED) {
            throw new BusinessException("VL", "605", "Lieferung " + delivery.getDeliveryNumber()
                    + " kann nur ohne gebuchten Warenausgang storniert werden", HttpStatus.CONFLICT);
        }
        for (DeliveryItem item : delivery.getItems()) {
            OrderItem orderItem = item.getOrderItem();
            orderItem.setDeliveredQuantity(Math.max(0, orderItem.getDeliveredQuantity() - item.getQuantity()));
        }
        delivery.setStatus(Delivery.Status.CANCELLED);
        delivery.setBillingStatus(ProcessingStatus.NOT_RELEVANT);
        delivery.getOrder().updateStatus();
        changeDocuments.logEvent(ObjectClass.DELIVERY, delivery.getId(), delivery.getDeliveryNumber(), ChangeType.DELETE,
                "Lieferung storniert");
        changeDocuments.logEvent(ObjectClass.SALES_ORDER, delivery.getOrder().getId(), delivery.getOrder().getOrderNumber(),
                ChangeType.EVENT, "Lieferung " + delivery.getDeliveryNumber() + " storniert");
        return mapper.toDto(deliveryRepository.save(delivery));
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    private Map<OrderItem, Integer> determineQuantities(Order order, List<LogisticsRequests.DeliveryItem> requested) {
        Map<OrderItem, Integer> result = new LinkedHashMap<>();
        if (requested == null || requested.isEmpty()) {
            for (OrderItem item : order.getOrderItems()) {
                if (item.getOpenConfirmedQuantity() > 0) {
                    result.put(item, item.getOpenConfirmedQuantity());
                }
            }
            return result;
        }
        for (LogisticsRequests.DeliveryItem req : requested) {
            OrderItem item = order.getItem(req.orderItemNumber());
            if (item == null) {
                throw new ResourceNotFoundException("Position " + req.orderItemNumber() + " nicht gefunden");
            }
            if (req.quantity() > item.getOpenConfirmedQuantity()) {
                throw new BusinessException("VL", "249", "Position " + item.getItemNumber() + ": Liefermenge "
                        + req.quantity() + " übersteigt die offene bestätigte Menge " + item.getOpenConfirmedQuantity());
            }
            result.merge(item, req.quantity(), Integer::sum);
        }
        return result;
    }

    /**
     * Wählt den Lagerort, der die Liefermengen am besten abdeckt; bei Gleichstand gewinnt der Standardlagerort.
     */
    private Warehouse determineWarehouse(Map<OrderItem, Integer> quantities) {
        List<Warehouse> candidates = warehouseRepository.findAllActiveOrderByDefault();
        if (candidates.isEmpty()) {
            throw new BusinessException("VL", "250", "Kein aktiver Lagerort vorhanden");
        }
        Warehouse best = candidates.get(0);
        long bestCoverage = -1;
        for (Warehouse warehouse : candidates) {
            long coverage = 0;
            for (Map.Entry<OrderItem, Integer> entry : quantities.entrySet()) {
                coverage += Math.min(entry.getValue(), freeStock(warehouse, entry.getKey().getProduct()));
            }
            if (coverage > bestCoverage) {
                best = warehouse;
                bestCoverage = coverage;
            }
        }
        return best;
    }

    /** Lagerortbestand abzüglich der Mengen in Lieferungen ohne Warenausgang. */
    private int freeStock(Warehouse warehouse, Product product) {
        int stock = stockRepository.findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                .map(WarehouseStock::getQuantity).orElse(0);
        long reserved = deliveryRepository.sumOpenDeliveryQuantity(product.getId(), warehouse.getId());
        return (int) Math.max(0, stock - reserved);
    }

    private Delivery load(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lieferung " + id + " nicht gefunden"));
    }

    public record DeliverySearch(
            String search,
            Delivery.Status status,
            ProcessingStatus billingStatus,
            Long warehouseId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
    }

    private static Specification<Delivery> specification(DeliverySearch c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.search() != null && !c.search().isBlank()) {
                String like = "%" + c.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("deliveryNumber")), like),
                        cb.like(cb.lower(root.get("order").get("orderNumber")), like),
                        cb.like(cb.lower(root.get("customer").get("lastName")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("customer").get("customerNumber"), "")), like)));
            }
            if (c.status() != null) predicates.add(cb.equal(root.get("status"), c.status()));
            if (c.billingStatus() != null) predicates.add(cb.equal(root.get("billingStatus"), c.billingStatus()));
            if (c.warehouseId() != null) predicates.add(cb.equal(root.get("warehouse").get("id"), c.warehouseId()));
            if (c.dateFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("plannedGoodsIssueDate"), c.dateFrom()));
            if (c.dateTo() != null) predicates.add(cb.lessThanOrEqualTo(root.get("plannedGoodsIssueDate"), c.dateTo()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
