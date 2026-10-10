package com.thomas.order_management.mapper;

import com.thomas.order_management.dto.sales.*;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.WarehouseStockRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Übersetzt Vertriebsbelege in API-Darstellungen. Schlüssel werden immer mit Kurztext ausgegeben.
 */
@Component
public class SalesDocumentMapper {

    private final WarehouseStockRepository stockRepository;

    public SalesDocumentMapper(WarehouseStockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public PartnerDto partner(Customer c) {
        if (c == null) return null;
        return new PartnerDto(c.getId(), c.getCustomerNumber(), c.getFullName(), c.getEmail(), c.getCity(), c.getCountry());
    }

    public OrderListItemDto toListItem(Order o) {
        return new OrderListItemDto(
                o.getId(), o.getOrderNumber(), partner(o.getCustomer()),
                o.getOrderDate(), o.getRequestedDeliveryDate(), o.getCustomerReference(),
                status(o.getStatus()), processing(o.getDeliveryStatus()), processing(o.getBillingStatus()),
                credit(o.getCreditStatus()),
                o.getNetAmount(), o.getTotalAmount(), o.getCurrency(),
                o.getOrderItems().size());
    }

    public OrderDto toDto(Order o) {
        return new OrderDto(
                o.getId(), o.getVersion(), o.getOrderNumber(), o.getOrderType(), partner(o.getCustomer()),
                o.getOrderDate(), o.getRequestedDeliveryDate(), o.getCustomerReference(),
                paymentTerms(o.getPaymentTerms()), o.getCurrency(),
                status(o.getStatus()), processing(o.getDeliveryStatus()), processing(o.getBillingStatus()),
                credit(o.getCreditStatus()),
                o.getNetAmount(), o.getTaxAmount(), o.getTotalAmount(),
                o.getNotes(), o.getShippingAddress(), o.getBillingAddress(),
                o.getCreatedBy(), o.getCreatedAt(), o.getUpdatedAt(),
                o.getOrderItems().stream().map(this::toItemDto).toList());
    }

    public OrderDto.Item toItemDto(OrderItem i) {
        Product p = i.getProduct();
        return new OrderDto.Item(
                i.getId(), i.getItemNumber(), p.getId(), p.getMaterialNumber(), p.getName(), p.getUnitOfMeasure(),
                i.getQuantity(), i.getConfirmedQuantity(), i.getDeliveredQuantity(), i.getBilledQuantity(),
                i.getOpenQuantity(),
                i.getUnitPrice(), i.getItemDiscountPercent(), i.getNetAmount(), i.getTaxRate(),
                i.getTaxAmount(), i.getGrossAmount(),
                i.getRejectionReason() != null
                        ? CodeText.of(i.getRejectionReason().name(), i.getRejectionReason().getDescription())
                        : null,
                i.getConditions().stream().map(this::toConditionDto).toList());
    }

    public OrderDto.Condition toConditionDto(PricingCondition c) {
        return new OrderDto.Condition(c.getStepNumber(), c.getConditionType(), c.getDescription(),
                c.getRate(), c.getRateUnit(), c.getConditionValue(), c.isStatistical());
    }

    public DeliveryDto toDto(Delivery d) {
        Warehouse w = d.getWarehouse();
        int total = d.getItems().stream().mapToInt(DeliveryItem::getQuantity).sum();
        return new DeliveryDto(
                d.getId(), d.getVersion(), d.getDeliveryNumber(), d.getDeliveryType(),
                d.getOrder().getId(), d.getOrder().getOrderNumber(), partner(d.getCustomer()),
                w.getId(), w.getCode(), w.getName(),
                CodeText.of(d.getStatus().name(), d.getStatus().getDisplayName()),
                processing(d.getBillingStatus()),
                d.getPlannedGoodsIssueDate(), d.getGoodsIssueDate(), d.getGoodsIssueDocument(),
                d.getShippingAddress(), total, d.getCreatedBy(), d.getCreatedAt(),
                d.getItems().stream().map(item -> new DeliveryDto.Item(
                        item.getItemNumber(), item.getOrderItem().getItemNumber(),
                        item.getProduct().getId(), item.getProduct().getMaterialNumber(),
                        item.getProduct().getName(), item.getProduct().getUnitOfMeasure(),
                        item.getQuantity(),
                        stockRepository.findByWarehouseIdAndProductId(w.getId(), item.getProduct().getId())
                                .map(WarehouseStock::getBinLocation).orElse(null)))
                        .toList());
    }

    public InvoiceDto toDto(Invoice inv) {
        LocalDate today = LocalDate.now();
        boolean overdue = inv.isOverdue(today);
        int daysOverdue = overdue ? (int) ChronoUnit.DAYS.between(inv.getDueDate(), today) : 0;
        BigDecimal margin = inv.getNetAmount().subtract(inv.getCostAmount());
        return new InvoiceDto(
                inv.getId(), inv.getVersion(), inv.getInvoiceNumber(),
                CodeText.of(inv.getBillingType().name(), inv.getBillingType().getDisplayName()),
                inv.getOrder().getId(), inv.getOrder().getOrderNumber(),
                inv.getDelivery() != null ? inv.getDelivery().getId() : null,
                inv.getDelivery() != null ? inv.getDelivery().getDeliveryNumber() : null,
                inv.getReferenceInvoice() != null ? inv.getReferenceInvoice().getInvoiceNumber() : null,
                partner(inv.getCustomer()),
                inv.getOrder().getBillingAddress(),
                inv.getBillingDate(), paymentTerms(inv.getPaymentTerms()), inv.getDueDate(),
                inv.getCashDiscountDate(), inv.getCashDiscountPercent(), inv.getCurrency(),
                inv.getNetAmount(), inv.getTaxAmount(), inv.getGrossAmount(), inv.getCostAmount(), margin,
                CodeText.of(inv.getStatus().name(), overdue ? "Überfällig" : inv.getStatus().getDisplayName()),
                overdue, daysOverdue,
                inv.getPaidAt(), inv.getPaidAmount(), inv.getCashDiscountAmount(),
                inv.getCreatedBy(), inv.getCreatedAt(),
                inv.getItems().stream().map(item -> new InvoiceDto.Item(
                        item.getItemNumber(), item.getOrderItem().getItemNumber(),
                        item.getProduct().getId(), item.getProduct().getMaterialNumber(),
                        item.getProduct().getName(), item.getProduct().getUnitOfMeasure(),
                        item.getQuantity(), item.getUnitNetPrice(), item.getNetAmount(),
                        item.getTaxRate(), item.getTaxAmount(), item.getGrossAmount()))
                        .toList());
    }

    public static CodeText status(OrderStatus s) {
        return s == null ? null : CodeText.of(s.name(), s.getDisplayName());
    }

    public static CodeText processing(ProcessingStatus s) {
        return s == null ? null : CodeText.of(s.name(), s.getDisplayName());
    }

    public static CodeText credit(CreditStatus s) {
        return s == null ? null : CodeText.of(s.name(), s.getDisplayName());
    }

    public static CodeText paymentTerms(PaymentTerms t) {
        return t == null ? null : CodeText.of(t.name(), t.getDescription());
    }
}
