package com.thomas.order_management.service.sales;

import com.thomas.order_management.dto.ChangeDocumentDto;
import com.thomas.order_management.dto.PageResponse;
import com.thomas.order_management.dto.sales.InvoiceDto;
import com.thomas.order_management.dto.sales.LogisticsRequests;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.mapper.SalesDocumentMapper;
import com.thomas.order_management.model.*;
import com.thomas.order_management.model.ChangeDocument.ChangeType;
import com.thomas.order_management.model.ChangeDocument.ObjectClass;
import com.thomas.order_management.repository.DeliveryRepository;
import com.thomas.order_management.repository.InvoiceRepository;
import com.thomas.order_management.service.ChangeDocumentService;
import com.thomas.order_management.service.CurrentUserProvider;
import com.thomas.order_management.service.NumberRangeService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fakturierung (angelehnt an VF01/VF11) und Zahlungseingang (vereinfacht, F-28).
 */
@Service
@Transactional
public class BillingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InvoiceRepository invoiceRepository;
    private final DeliveryRepository deliveryRepository;
    private final NumberRangeService numberRangeService;
    private final ChangeDocumentService changeDocuments;
    private final CurrentUserProvider currentUser;
    private final SalesDocumentMapper mapper;

    public BillingService(InvoiceRepository invoiceRepository,
                          DeliveryRepository deliveryRepository,
                          NumberRangeService numberRangeService,
                          ChangeDocumentService changeDocuments,
                          CurrentUserProvider currentUser,
                          SalesDocumentMapper mapper) {
        this.invoiceRepository = invoiceRepository;
        this.deliveryRepository = deliveryRepository;
        this.numberRangeService = numberRangeService;
        this.changeDocuments = changeDocuments;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<InvoiceDto> search(InvoiceSearch criteria, Pageable pageable) {
        Page<Invoice> page = invoiceRepository.findAll(specification(criteria), pageable);
        return PageResponse.of(page, mapper::toDto);
    }

    @Transactional(readOnly = true)
    public InvoiceDto get(Long id) {
        return mapper.toDto(load(id));
    }

    @Transactional(readOnly = true)
    public Invoice getEntity(Long id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> getByOrder(Long orderId) {
        return invoiceRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> getByCustomer(Long customerId) {
        return invoiceRepository.findByCustomerIdOrderByBillingDateDesc(customerId).stream().map(mapper::toDto).toList();
    }

    /** Fakturavorrat (VF04): Lieferungen mit Warenausgang, die noch nicht fakturiert sind. */
    @Transactional(readOnly = true)
    public List<com.thomas.order_management.dto.sales.DeliveryDto> getBillingDueList() {
        return deliveryRepository.findBillingDueList(ProcessingStatus.NOT_PROCESSED).stream()
                .map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeDocumentDto> getChanges(Long id) {
        load(id);
        return changeDocuments.getChanges(ObjectClass.INVOICE, id);
    }

    // ------------------------------------------------------------------
    // Fakturieren
    // ------------------------------------------------------------------

    public InvoiceDto create(LogisticsRequests.CreateInvoice request) {
        LocalDate billingDate = request.billingDate() != null ? request.billingDate() : LocalDate.now();
        return mapper.toDto(createInvoice(request.deliveryId(), billingDate, LocalDateTime.now()));
    }

    public Invoice createInvoice(Long deliveryId, LocalDate billingDate, LocalDateTime createdAt) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Lieferung " + deliveryId + " nicht gefunden"));
        if (!delivery.isGoodsIssued()) {
            throw new BusinessException("VF", "016", "Lieferung " + delivery.getDeliveryNumber()
                    + " ist nicht fakturierbar: Warenausgang noch nicht gebucht", HttpStatus.CONFLICT);
        }
        if (delivery.getBillingStatus() != ProcessingStatus.NOT_PROCESSED) {
            throw new BusinessException("VF", "017", "Lieferung " + delivery.getDeliveryNumber()
                    + " ist bereits fakturiert", HttpStatus.CONFLICT);
        }
        if (billingDate.isBefore(delivery.getGoodsIssueDate().toLocalDate())) {
            throw new BusinessException("VF", "018", "Fakturadatum liegt vor dem Warenausgangsdatum", HttpStatus.BAD_REQUEST);
        }

        Order order = delivery.getOrder();
        PaymentTerms terms = order.getPaymentTerms() != null ? order.getPaymentTerms() : PaymentTerms.ZB30;

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(numberRangeService.next(NumberRangeObject.INVOICE));
        invoice.setBillingType(Invoice.BillingType.F2);
        invoice.setOrder(order);
        invoice.setDelivery(delivery);
        invoice.setCustomer(order.getCustomer());
        invoice.setBillingDate(billingDate);
        invoice.setPaymentTerms(terms);
        invoice.setDueDate(billingDate.plusDays(terms.getNetDays()));
        if (terms.getCashDiscountPercent().signum() > 0) {
            invoice.setCashDiscountDate(billingDate.plusDays(terms.getCashDiscountDays()));
            invoice.setCashDiscountPercent(terms.getCashDiscountPercent());
        }
        invoice.setCreatedAt(createdAt);
        invoice.setCreatedBy(currentUser.currentUsername());

        int itemNumber = 10;
        for (DeliveryItem deliveryItem : delivery.getItems()) {
            OrderItem orderItem = deliveryItem.getOrderItem();
            int qty = deliveryItem.getQuantity();

            InvoiceItem item = new InvoiceItem();
            item.setItemNumber(itemNumber);
            item.setOrderItem(orderItem);
            item.setDeliveryItem(deliveryItem);
            item.setProduct(deliveryItem.getProduct());
            item.setQuantity(qty);
            item.setUnitNetPrice(orderItem.getNetUnitPrice());
            BigDecimal net = orderItem.getNetAmount()
                    .multiply(BigDecimal.valueOf(qty))
                    .divide(BigDecimal.valueOf(orderItem.getQuantity()), 2, RoundingMode.HALF_UP);
            BigDecimal tax = net.multiply(orderItem.getTaxRate()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            item.setNetAmount(net);
            item.setTaxRate(orderItem.getTaxRate());
            item.setTaxAmount(tax);
            item.setGrossAmount(net.add(tax));
            item.setCostAmount(deliveryItem.getProduct().getValuationPrice()
                    .multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
            invoice.addItem(item);

            orderItem.setBilledQuantity(orderItem.getBilledQuantity() + qty);
            itemNumber += 10;
        }
        invoice.recalculateTotals();

        delivery.setBillingStatus(ProcessingStatus.FULLY_PROCESSED);
        order.updateStatus();

        Invoice saved = invoiceRepository.save(invoice);
        changeDocuments.logEvent(ObjectClass.INVOICE, saved.getId(), saved.getInvoiceNumber(), ChangeType.INSERT,
                "Rechnung zu Lieferung " + delivery.getDeliveryNumber() + " angelegt", createdAt);
        changeDocuments.logEvent(ObjectClass.SALES_ORDER, order.getId(), order.getOrderNumber(), ChangeType.EVENT,
                "Rechnung " + saved.getInvoiceNumber() + " angelegt", createdAt);
        return saved;
    }

    // ------------------------------------------------------------------
    // Stornieren
    // ------------------------------------------------------------------

    public InvoiceDto cancel(Long id, LogisticsRequests.Cancel request) {
        LocalDate date = request != null && request.date() != null ? request.date() : LocalDate.now();
        return mapper.toDto(cancelInvoice(id, date));
    }

    public Invoice cancelInvoice(Long id, LocalDate cancellationDate) {
        Invoice original = load(id);
        if (original.getBillingType() != Invoice.BillingType.F2) {
            throw new BusinessException("VF", "047", "Eine Stornorechnung kann nicht storniert werden", HttpStatus.CONFLICT);
        }
        if (original.getStatus() == Invoice.Status.CANCELLED) {
            throw new BusinessException("VF", "048", "Rechnung " + original.getInvoiceNumber() + " ist bereits storniert", HttpStatus.CONFLICT);
        }
        if (original.getStatus() == Invoice.Status.PAID) {
            throw new BusinessException("VF", "049", "Rechnung " + original.getInvoiceNumber()
                    + " ist bereits ausgeglichen; bitte zuerst den Ausgleich zurücknehmen", HttpStatus.CONFLICT);
        }

        Invoice reversal = new Invoice();
        reversal.setInvoiceNumber(numberRangeService.next(NumberRangeObject.INVOICE));
        reversal.setBillingType(Invoice.BillingType.S1);
        reversal.setOrder(original.getOrder());
        reversal.setDelivery(original.getDelivery());
        reversal.setCustomer(original.getCustomer());
        reversal.setReferenceInvoice(original);
        reversal.setBillingDate(cancellationDate);
        reversal.setPaymentTerms(original.getPaymentTerms());
        reversal.setDueDate(cancellationDate);
        reversal.setStatus(Invoice.Status.PAID);
        reversal.setPaidAt(cancellationDate);
        reversal.setCreatedAt(LocalDateTime.now());
        reversal.setCreatedBy(currentUser.currentUsername());

        for (InvoiceItem item : original.getItems()) {
            InvoiceItem copy = new InvoiceItem();
            copy.setItemNumber(item.getItemNumber());
            copy.setOrderItem(item.getOrderItem());
            copy.setDeliveryItem(item.getDeliveryItem());
            copy.setProduct(item.getProduct());
            copy.setQuantity(-item.getQuantity());
            copy.setUnitNetPrice(item.getUnitNetPrice());
            copy.setNetAmount(item.getNetAmount().negate());
            copy.setTaxRate(item.getTaxRate());
            copy.setTaxAmount(item.getTaxAmount().negate());
            copy.setGrossAmount(item.getGrossAmount().negate());
            copy.setCostAmount(item.getCostAmount().negate());
            reversal.addItem(copy);

            OrderItem orderItem = item.getOrderItem();
            orderItem.setBilledQuantity(Math.max(0, orderItem.getBilledQuantity() - item.getQuantity()));
        }
        reversal.recalculateTotals();

        original.setStatus(Invoice.Status.CANCELLED);
        if (original.getDelivery() != null) {
            original.getDelivery().setBillingStatus(ProcessingStatus.NOT_PROCESSED);
        }
        original.getOrder().updateStatus();

        Invoice saved = invoiceRepository.save(reversal);
        changeDocuments.logEvent(ObjectClass.INVOICE, original.getId(), original.getInvoiceNumber(), ChangeType.EVENT,
                "Storniert durch Stornorechnung " + saved.getInvoiceNumber());
        changeDocuments.logEvent(ObjectClass.INVOICE, saved.getId(), saved.getInvoiceNumber(), ChangeType.INSERT,
                "Stornorechnung zu " + original.getInvoiceNumber() + " angelegt");
        changeDocuments.logEvent(ObjectClass.SALES_ORDER, original.getOrder().getId(), original.getOrder().getOrderNumber(),
                ChangeType.EVENT, "Rechnung " + original.getInvoiceNumber() + " storniert");
        return saved;
    }

    // ------------------------------------------------------------------
    // Zahlungseingang
    // ------------------------------------------------------------------

    public InvoiceDto postPayment(Long id, LogisticsRequests.Payment request) {
        LocalDate date = request != null && request.paymentDate() != null ? request.paymentDate() : LocalDate.now();
        BigDecimal amount = request != null ? request.amount() : null;
        return mapper.toDto(postPayment(id, date, amount));
    }

    public Invoice postPayment(Long id, LocalDate paymentDate, BigDecimal amount) {
        Invoice invoice = load(id);
        if (invoice.getBillingType() != Invoice.BillingType.F2 || invoice.getStatus() != Invoice.Status.OPEN) {
            throw new BusinessException("F5", "263", "Rechnung " + invoice.getInvoiceNumber()
                    + " ist kein offener Posten", HttpStatus.CONFLICT);
        }
        if (paymentDate.isBefore(invoice.getBillingDate())) {
            throw new BusinessException("F5", "264", "Zahlungsdatum liegt vor dem Fakturadatum", HttpStatus.BAD_REQUEST);
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (invoice.getCashDiscountDate() != null && !paymentDate.isAfter(invoice.getCashDiscountDate())) {
            discount = invoice.getGrossAmount().multiply(invoice.getCashDiscountPercent())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        BigDecimal expected = invoice.getGrossAmount().subtract(discount);
        BigDecimal paid = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : expected;
        if (paid.compareTo(expected) < 0) {
            throw new BusinessException("F5", "702", "Zahlbetrag " + eur(paid) + " reicht nicht zum Ausgleich; erwartet "
                    + eur(expected) + (discount.signum() > 0 ? " (nach " + eur(discount) + " Skonto)" : ""));
        }

        invoice.setStatus(Invoice.Status.PAID);
        invoice.setPaidAt(paymentDate);
        invoice.setPaidAmount(paid);
        invoice.setCashDiscountAmount(discount);
        // Zeitpunkt für den Änderungsbeleg: heute jetzt, sonst Mittag, aber nie vor dem Anlegen der Rechnung
        LocalDateTime clearedAt = paymentDate.equals(LocalDate.now()) ? LocalDateTime.now() : paymentDate.atTime(12, 0);
        if (invoice.getCreatedAt() != null && clearedAt.isBefore(invoice.getCreatedAt())) {
            clearedAt = invoice.getCreatedAt();
        }
        changeDocuments.logEvent(ObjectClass.INVOICE, invoice.getId(), invoice.getInvoiceNumber(), ChangeType.EVENT,
                "Zahlungseingang " + eur(paid) + (discount.signum() > 0 ? ", Skonto " + eur(discount) : ""),
                clearedAt);
        return invoiceRepository.save(invoice);
    }

    private Invoice load(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Faktura " + id + " nicht gefunden"));
    }

    public record InvoiceSearch(
            String search,
            Invoice.Status status,
            Invoice.BillingType billingType,
            Boolean overdue,
            Long customerId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
    }

    private static Specification<Invoice> specification(InvoiceSearch c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.search() != null && !c.search().isBlank()) {
                String like = "%" + c.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceNumber")), like),
                        cb.like(cb.lower(root.get("order").get("orderNumber")), like),
                        cb.like(cb.lower(root.get("customer").get("lastName")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("customer").get("customerNumber"), "")), like)));
            }
            if (c.status() != null) predicates.add(cb.equal(root.get("status"), c.status()));
            if (c.billingType() != null) predicates.add(cb.equal(root.get("billingType"), c.billingType()));
            if (Boolean.TRUE.equals(c.overdue())) {
                predicates.add(cb.equal(root.get("status"), Invoice.Status.OPEN));
                predicates.add(cb.equal(root.get("billingType"), Invoice.BillingType.F2));
                predicates.add(cb.lessThan(root.get("dueDate"), LocalDate.now()));
            }
            if (c.customerId() != null) predicates.add(cb.equal(root.get("customer").get("id"), c.customerId()));
            if (c.dateFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("billingDate"), c.dateFrom()));
            if (c.dateTo() != null) predicates.add(cb.lessThanOrEqualTo(root.get("billingDate"), c.dateTo()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Betrag für Meldungstexte, z. B. "1.517,87 EUR". */
    private static String eur(BigDecimal amount) {
        return String.format(Locale.GERMANY, "%,.2f EUR", amount);
    }
}
