package com.thomas.order_management.service.sales;

import com.thomas.order_management.dto.ChangeDocumentDto;
import com.thomas.order_management.dto.PageResponse;
import com.thomas.order_management.dto.sales.*;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.mapper.SalesDocumentMapper;
import com.thomas.order_management.model.*;
import com.thomas.order_management.model.ChangeDocument.ChangeType;
import com.thomas.order_management.model.ChangeDocument.ObjectClass;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.OrderItemRepository;
import com.thomas.order_management.repository.OrderRepository;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.service.ChangeDocumentService;
import com.thomas.order_management.service.CurrentUserProvider;
import com.thomas.order_management.service.NumberRangeService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Kundenauftragsbearbeitung (angelehnt an VA01/VA02/VA03).
 */
@Service
@Transactional
public class SalesOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final PricingService pricingService;
    private final AvailabilityService availabilityService;
    private final CreditCheckService creditCheckService;
    private final NumberRangeService numberRangeService;
    private final ChangeDocumentService changeDocuments;
    private final CurrentUserProvider currentUser;
    private final SalesDocumentMapper mapper;

    public SalesOrderService(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             CustomerRepository customerRepository,
                             ProductRepository productRepository,
                             PricingService pricingService,
                             AvailabilityService availabilityService,
                             CreditCheckService creditCheckService,
                             NumberRangeService numberRangeService,
                             ChangeDocumentService changeDocuments,
                             CurrentUserProvider currentUser,
                             SalesDocumentMapper mapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.pricingService = pricingService;
        this.availabilityService = availabilityService;
        this.creditCheckService = creditCheckService;
        this.numberRangeService = numberRangeService;
        this.changeDocuments = changeDocuments;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<OrderListItemDto> search(OrderSearch criteria, Pageable pageable) {
        Page<Order> page = orderRepository.findAll(specification(criteria), pageable);
        return PageResponse.of(page, mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        return mapper.toDto(load(id));
    }

    @Transactional(readOnly = true)
    public OrderDto getByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Kundenauftrag " + orderNumber + " nicht gefunden"));
    }

    @Transactional(readOnly = true)
    public List<OrderListItemDto> getByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByOrderDateDesc(customerId).stream()
                .map(mapper::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeDocumentDto> getChanges(Long id) {
        load(id);
        return changeDocuments.getChanges(ObjectClass.SALES_ORDER, id);
    }

    // ------------------------------------------------------------------
    // Simulation und Anlage
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrderSimulationDto simulate(OrderRequests.Create request) {
        Customer customer = loadCustomer(request.customerId());
        List<OrderSimulationDto.Message> messages = new ArrayList<>();
        List<OrderSimulationDto.Item> items = new ArrayList<>();
        Map<Long, Integer> consumed = new HashMap<>();

        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        int itemNumber = 10;
        for (OrderRequests.Item req : request.items()) {
            Product product = loadSellableProduct(req.productId());
            PricingService.PricingResult pricing = pricingService.price(customer, product, req.quantity(), req.discountPercent());
            int available = Math.max(0, availabilityService.availableQuantity(product.getId(), null)
                    - consumed.getOrDefault(product.getId(), 0));
            int confirmed = Math.min(req.quantity(), available);
            consumed.merge(product.getId(), confirmed, Integer::sum);

            if (confirmed < req.quantity()) {
                messages.add(new OrderSimulationDto.Message("W", "V1 382",
                        "Position " + itemNumber + ": Material " + product.getMaterialNumber() + " nur "
                                + confirmed + " von " + req.quantity() + " " + product.getUnitOfMeasure()
                                + " verfügbar; Restmenge wird nicht bestätigt"));
            }

            items.add(new OrderSimulationDto.Item(itemNumber, product.getId(), product.getMaterialNumber(),
                    product.getName(), product.getUnitOfMeasure(), req.quantity(), confirmed, available,
                    pricing.unitPrice(), pricing.netAmount(), pricing.taxRate(), pricing.taxAmount(),
                    pricing.grossAmount(),
                    pricing.conditions().stream().map(mapper::toConditionDto).toList()));
            net = net.add(pricing.netAmount());
            tax = tax.add(pricing.taxAmount());
            itemNumber += 10;
        }

        BigDecimal gross = net.add(tax);
        CreditStatus creditStatus = creditCheckService.check(customer, null, gross);
        CreditExposureDto exposure = creditCheckService.exposure(customer);
        if (creditStatus == CreditStatus.BLOCKED) {
            messages.add(new OrderSimulationDto.Message("W", "V1 154",
                    "Kreditlimit von Debitor " + customer.getCustomerNumber()
                            + " überschritten; der Auftrag wird mit Kreditsperre gesichert"));
        }
        if (messages.isEmpty()) {
            messages.add(new OrderSimulationDto.Message("S", "V1 311", "Alle Positionen vollständig bestätigt"));
        }

        return new OrderSimulationDto(items, net, tax, gross, Order.CURRENCY,
                SalesDocumentMapper.credit(creditStatus), exposure,
                SalesDocumentMapper.paymentTerms(customer.getPaymentTerms()), messages);
    }

    public OrderDto create(OrderRequests.Create request) {
        return mapper.toDto(createOrder(request, null));
    }

    /**
     * Legt einen Auftrag an. {@code documentDateTime} erlaubt ein abweichendes Belegdatum
     * (z. B. für Datenübernahmen); null = jetzt.
     */
    public Order createOrder(OrderRequests.Create request, LocalDateTime documentDateTime) {
        Customer customer = loadCustomer(request.customerId());
        if (!Boolean.TRUE.equals(customer.getActive())) {
            throw new BusinessException("V1", "070", "Debitor " + customer.getCustomerNumber() + " ist gesperrt");
        }

        LocalDateTime documentDate = documentDateTime != null ? documentDateTime
                : request.documentDate() != null ? request.documentDate().atTime(LocalTime.now()) : LocalDateTime.now();

        Order order = new Order(customer, numberRangeService.next(NumberRangeObject.SALES_ORDER));
        order.setOrderDate(documentDate);
        order.setCreatedAt(documentDate);
        order.setUpdatedAt(documentDate);
        order.setRequestedDeliveryDate(request.requestedDeliveryDate() != null
                ? request.requestedDeliveryDate()
                : documentDate.toLocalDate().plusDays(3));
        if (order.getRequestedDeliveryDate().isBefore(documentDate.toLocalDate())) {
            throw new BusinessException("V1", "202", "Wunschlieferdatum liegt vor dem Belegdatum", HttpStatus.BAD_REQUEST);
        }
        order.setCustomerReference(blankToNull(request.customerReference()));
        order.setPaymentTerms(customer.getPaymentTerms());
        String defaultAddress = customer.getFormattedAddress();
        order.setShippingAddress(Optional.ofNullable(blankToNull(request.shippingAddress())).orElse(defaultAddress));
        order.setBillingAddress(Optional.ofNullable(blankToNull(request.billingAddress())).orElse(defaultAddress));
        order.setNotes(blankToNull(request.notes()));
        order.setCreatedBy(currentUser.currentUsername());

        Map<Long, Integer> consumed = new HashMap<>();
        for (OrderRequests.Item req : request.items()) {
            Product product = loadSellableProduct(req.productId());
            OrderItem item = new OrderItem(product, req.quantity());
            applyPricing(item, customer, req.discountPercent());
            int available = Math.max(0, availabilityService.availableQuantity(product.getId(), null)
                    - consumed.getOrDefault(product.getId(), 0));
            item.setConfirmedQuantity(Math.min(req.quantity(), available));
            consumed.merge(product.getId(), item.getConfirmedQuantity(), Integer::sum);
            order.addItem(item);
        }

        order.recalculateTotals();
        order.setCreditStatus(creditCheckService.check(customer, null, order.getTotalAmount()));
        order.updateStatus();

        Order saved = orderRepository.save(order);
        changeDocuments.logEvent(ObjectClass.SALES_ORDER, saved.getId(), saved.getOrderNumber(), ChangeType.INSERT,
                "Kundenauftrag angelegt" + (saved.getCreditStatus() == CreditStatus.BLOCKED ? " (Kreditsperre)" : ""),
                documentDate);
        return saved;
    }

    // ------------------------------------------------------------------
    // Ändern
    // ------------------------------------------------------------------

    public OrderDto updateHeader(Long id, OrderRequests.UpdateHeader request) {
        Order order = loadChangeable(id);
        String number = order.getOrderNumber();

        if (request.requestedDeliveryDate() != null) {
            if (request.requestedDeliveryDate().isBefore(order.getOrderDate().toLocalDate())) {
                throw new BusinessException("V1", "202", "Wunschlieferdatum liegt vor dem Belegdatum", HttpStatus.BAD_REQUEST);
            }
            changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, id, number, "Wunschlieferdatum",
                    order.getRequestedDeliveryDate(), request.requestedDeliveryDate());
            order.setRequestedDeliveryDate(request.requestedDeliveryDate());
        }
        if (request.customerReference() != null) {
            changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, id, number, "Bestellnummer Kunde",
                    order.getCustomerReference(), blankToNull(request.customerReference()));
            order.setCustomerReference(blankToNull(request.customerReference()));
        }
        if (request.shippingAddress() != null) {
            changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, id, number, "Lieferadresse",
                    order.getShippingAddress(), request.shippingAddress());
            order.setShippingAddress(request.shippingAddress());
        }
        if (request.billingAddress() != null) {
            changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, id, number, "Rechnungsadresse",
                    order.getBillingAddress(), request.billingAddress());
            order.setBillingAddress(request.billingAddress());
        }
        if (request.notes() != null) {
            changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, id, number, "Notiz",
                    order.getNotes(), blankToNull(request.notes()));
            order.setNotes(blankToNull(request.notes()));
        }
        return mapper.toDto(orderRepository.save(order));
    }

    public OrderDto addItem(Long id, OrderRequests.Item request) {
        Order order = loadChangeable(id);
        Product product = loadSellableProduct(request.productId());
        OrderItem item = new OrderItem(product, request.quantity());
        applyPricing(item, order.getCustomer(), request.discountPercent());
        item.setConfirmedQuantity(Math.min(request.quantity(),
                availabilityService.availableQuantity(product.getId(), null)));
        order.addItem(item);
        afterItemChange(order);
        changeDocuments.logEvent(ObjectClass.SALES_ORDER, id, order.getOrderNumber(), ChangeType.INSERT,
                "Position " + item.getItemNumber() + " (" + product.getMaterialNumber() + ", "
                        + request.quantity() + " " + product.getUnitOfMeasure() + ") hinzugefügt");
        return mapper.toDto(orderRepository.save(order));
    }

    public OrderDto changeItem(Long id, int itemNumber, OrderRequests.ChangeItem request) {
        Order order = loadChangeable(id);
        OrderItem item = loadItem(order, itemNumber);
        if (item.isRejected()) {
            throw new BusinessException("V1", "078", "Position " + itemNumber + " ist abgesagt");
        }
        if (request.quantity() < item.getDeliveredQuantity()) {
            throw new BusinessException("V1", "079", "Position " + itemNumber + ": Menge darf die bereits gelieferte Menge ("
                    + item.getDeliveredQuantity() + ") nicht unterschreiten");
        }
        if (item.getBilledQuantity() > 0 && request.discountPercent() != null
                && request.discountPercent().compareTo(nz(item.getItemDiscountPercent())) != 0) {
            throw new BusinessException("V1", "080", "Position " + itemNumber + " ist bereits fakturiert; Preisänderung nicht möglich");
        }

        String number = order.getOrderNumber();
        changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, id, number,
                "Pos. " + itemNumber + " Menge", item.getQuantity(), request.quantity());
        changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, id, number,
                "Pos. " + itemNumber + " Rabatt %", item.getItemDiscountPercent(),
                request.discountPercent() != null ? request.discountPercent() : item.getItemDiscountPercent());

        item.setQuantity(request.quantity());
        applyPricing(item, order.getCustomer(),
                request.discountPercent() != null ? request.discountPercent() : item.getItemDiscountPercent());
        reconfirmItem(item);
        afterItemChange(order);
        return mapper.toDto(orderRepository.save(order));
    }

    public OrderDto reject(Long id, OrderRequests.Reject request) {
        Order order = loadChangeable(id);
        List<OrderItem> candidates = order.getOrderItems().stream()
                .filter(i -> !i.isRejected())
                .filter(i -> request.itemNumbers() == null || request.itemNumbers().isEmpty()
                        || request.itemNumbers().contains(i.getItemNumber()))
                .toList();

        List<OrderItem> rejectable = new ArrayList<>();
        for (OrderItem item : candidates) {
            if (item.getDeliveredQuantity() > 0) {
                if (request.itemNumbers() != null && request.itemNumbers().contains(item.getItemNumber())) {
                    throw new BusinessException("V1", "081", "Position " + item.getItemNumber()
                            + " ist bereits (teil-)geliefert und kann nicht abgesagt werden");
                }
                continue;
            }
            rejectable.add(item);
        }
        if (rejectable.isEmpty()) {
            throw new BusinessException("V1", "082", "Keine absagbaren Positionen vorhanden");
        }

        for (OrderItem item : rejectable) {
            item.setRejectionReason(request.reason());
            item.setConfirmedQuantity(0);
            changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, id, order.getOrderNumber(),
                    "Pos. " + item.getItemNumber() + " Absagegrund", null,
                    request.reason().name() + " " + request.reason().getDescription());
        }
        afterItemChange(order);
        return mapper.toDto(orderRepository.save(order));
    }

    public OrderDto releaseCreditBlock(Long id) {
        Order order = load(id);
        if (order.getCreditStatus() != CreditStatus.BLOCKED) {
            throw new BusinessException("V1", "155", "Auftrag " + order.getOrderNumber() + " hat keine Kreditsperre", HttpStatus.CONFLICT);
        }
        changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, id, order.getOrderNumber(), "Kreditstatus",
                order.getCreditStatus().getDisplayName(), CreditStatus.RELEASED.getDisplayName());
        order.setCreditStatus(CreditStatus.RELEASED);
        return mapper.toDto(orderRepository.save(order));
    }

    /** Erneute Verfügbarkeitsprüfung für alle offenen Positionen eines Auftrags. */
    public OrderDto reconfirm(Long id) {
        Order order = loadChangeable(id);
        order.getOrderItems().stream().filter(i -> !i.isRejected()).forEach(this::reconfirmItem);
        order.updateStatus();
        return mapper.toDto(orderRepository.save(order));
    }

    /**
     * Rückstandsbearbeitung (angelehnt an V_RA): prüft alle nicht vollständig bestätigten Positionen
     * in der Reihenfolge des Auftragsdatums erneut.
     */
    public int processBacklog() {
        int improved = 0;
        for (OrderItem item : orderItemRepository.findBacklogItems()) {
            int before = item.getConfirmedQuantity();
            reconfirmItem(item);
            orderItemRepository.saveAndFlush(item);
            if (item.getConfirmedQuantity() > before) {
                improved++;
            }
        }
        return improved;
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    void reconfirmItem(OrderItem item) {
        if (item.isRejected()) {
            item.setConfirmedQuantity(0);
            return;
        }
        int available = item.getId() != null
                ? availabilityService.availableQuantity(item.getProduct().getId(), item.getId())
                : availabilityService.availableQuantity(item.getProduct().getId(), null);
        int openNeeded = item.getQuantity() - item.getDeliveredQuantity();
        item.setConfirmedQuantity(item.getDeliveredQuantity() + Math.min(openNeeded, available));
    }

    private void applyPricing(OrderItem item, Customer customer, BigDecimal discountPercent) {
        PricingService.PricingResult pricing = pricingService.price(customer, item.getProduct(), item.getQuantity(), discountPercent);
        item.setUnitPrice(pricing.unitPrice());
        item.setItemDiscountPercent(discountPercent != null ? discountPercent : BigDecimal.ZERO);
        item.setNetAmount(pricing.netAmount());
        item.setTaxRate(pricing.taxRate());
        item.setTaxAmount(pricing.taxAmount());
        item.setGrossAmount(pricing.grossAmount());
        item.replaceConditions(pricing.conditions());
    }

    private void afterItemChange(Order order) {
        order.recalculateTotals();
        order.updateStatus();
        if (order.getCreditStatus() != CreditStatus.RELEASED) {
            CreditStatus newStatus = creditCheckService.check(order.getCustomer(), order.getId(),
                    CreditCheckService.unbilledGrossValue(order));
            if (newStatus != order.getCreditStatus()) {
                changeDocuments.logFieldChange(ObjectClass.SALES_ORDER, order.getId(), order.getOrderNumber(),
                        "Kreditstatus", order.getCreditStatus().getDisplayName(), newStatus.getDisplayName());
                order.setCreditStatus(newStatus);
            }
        }
    }

    Order load(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kundenauftrag " + id + " nicht gefunden"));
    }

    private Order loadChangeable(Long id) {
        Order order = load(id);
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessException("V1", "042", "Kundenauftrag " + order.getOrderNumber() + " ist "
                    + order.getStatus().getDisplayName().toLowerCase() + " und kann nicht mehr geändert werden",
                    HttpStatus.CONFLICT);
        }
        return order;
    }

    private OrderItem loadItem(Order order, int itemNumber) {
        OrderItem item = order.getItem(itemNumber);
        if (item == null) {
            throw new ResourceNotFoundException("Position " + itemNumber + " in Auftrag " + order.getOrderNumber() + " nicht gefunden");
        }
        return item;
    }

    private Customer loadCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Debitor " + customerId + " nicht gefunden"));
    }

    private Product loadSellableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Material " + productId + " nicht gefunden"));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new BusinessException("V1", "391", "Material " + product.getMaterialNumber() + " ist für den Vertrieb gesperrt");
        }
        return product;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public record OrderSearch(
            String search,
            List<OrderStatus> statuses,
            Long customerId,
            CreditStatus creditStatus,
            ProcessingStatus deliveryStatus,
            ProcessingStatus billingStatus,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
    }

    private static Specification<Order> specification(OrderSearch c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.search() != null && !c.search().isBlank()) {
                String like = "%" + c.search().trim().toLowerCase() + "%";
                var customer = root.join("customer", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("customerReference"), "")), like),
                        cb.like(cb.lower(customer.get("lastName")), like),
                        cb.like(cb.lower(customer.get("firstName")), like),
                        cb.like(cb.lower(cb.coalesce(customer.get("customerNumber"), "")), like)));
            }
            if (c.statuses() != null && !c.statuses().isEmpty()) {
                predicates.add(root.get("status").in(c.statuses()));
            }
            if (c.customerId() != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), c.customerId()));
            }
            if (c.creditStatus() != null) {
                predicates.add(cb.equal(root.get("creditStatus"), c.creditStatus()));
            }
            if (c.deliveryStatus() != null) {
                predicates.add(cb.equal(root.get("deliveryStatus"), c.deliveryStatus()));
            }
            if (c.billingStatus() != null) {
                predicates.add(cb.equal(root.get("billingStatus"), c.billingStatus()));
            }
            if (c.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), c.dateFrom().atStartOfDay()));
            }
            if (c.dateTo() != null) {
                predicates.add(cb.lessThan(root.get("orderDate"), c.dateTo().plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
