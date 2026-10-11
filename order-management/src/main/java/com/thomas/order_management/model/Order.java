// order-management/src/main/java/com/thomas/order_management/model/Order.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Kundenauftrag (SAP SD, Auftragsart TA). Der Gesamtstatus wird aus den Positionen abgeleitet.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_customer", columnList = "customer_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_date", columnList = "order_date")
})
@Getter
@Setter
public class Order {

    public static final String ORDER_TYPE_STANDARD = "TA";
    public static final String CURRENCY = "EUR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 10)
    private String orderNumber;

    @Column(name = "order_type", nullable = false, length = 4)
    private String orderType = ORDER_TYPE_STANDARD;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Belegdatum. */
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "requested_delivery_date")
    private LocalDate requestedDeliveryDate;

    /** Bestellnummer des Kunden. */
    @Column(name = "customer_reference", length = 35)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_terms", length = 4)
    private PaymentTerms paymentTerms;

    @Column(nullable = false, length = 3)
    private String currency = CURRENCY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private ProcessingStatus deliveryStatus = ProcessingStatus.NOT_PROCESSED;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", nullable = false, length = 20)
    private ProcessingStatus billingStatus = ProcessingStatus.NOT_PROCESSED;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_status", nullable = false, length = 20)
    private CreditStatus creditStatus = CreditStatus.NOT_CHECKED;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Bruttobetrag (Netto + MWST). */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(name = "billing_address", length = 500)
    private String billingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("itemNumber ASC")
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Order() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.orderDate = now;
    }

    public Order(Customer customer, String orderNumber) {
        this();
        this.customer = customer;
        this.orderNumber = orderNumber;
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        if (item.getItemNumber() == null) {
            item.setItemNumber(nextItemNumber());
        }
        orderItems.add(item);
    }

    public int nextItemNumber() {
        return orderItems.stream()
                .map(OrderItem::getItemNumber)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 10;
    }

    public OrderItem getItem(int itemNumber) {
        return orderItems.stream()
                .filter(i -> i.getItemNumber() == itemNumber)
                .findFirst()
                .orElse(null);
    }

    /** Summiert die Werte aller nicht abgesagten Positionen. */
    public void recalculateTotals() {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (OrderItem item : orderItems) {
            if (item.isRejected()) continue;
            net = net.add(item.getNetAmount());
            tax = tax.add(item.getTaxAmount());
        }
        this.netAmount = net;
        this.taxAmount = tax;
        this.totalAmount = net.add(tax);
    }

    /** Leitet Liefer-, Faktura- und Gesamtstatus aus den Positionen ab. */
    public void updateStatus() {
        long relevant = 0;
        long delivered = 0;
        long billed = 0;
        boolean anyActive = false;
        for (OrderItem item : orderItems) {
            if (item.isRejected()) continue;
            anyActive = true;
            relevant += item.getQuantity();
            delivered += item.getDeliveredQuantity();
            billed += item.getBilledQuantity();
        }

        this.deliveryStatus = ProcessingStatus.of(delivered, relevant);
        this.billingStatus = ProcessingStatus.of(billed, relevant);

        if (!anyActive) {
            this.status = OrderStatus.CANCELLED;
        } else if (deliveryStatus == ProcessingStatus.FULLY_PROCESSED && billingStatus == ProcessingStatus.FULLY_PROCESSED) {
            this.status = OrderStatus.COMPLETED;
        } else if (deliveryStatus == ProcessingStatus.NOT_PROCESSED && billingStatus == ProcessingStatus.NOT_PROCESSED) {
            this.status = OrderStatus.OPEN;
        } else {
            this.status = OrderStatus.IN_PROCESS;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
