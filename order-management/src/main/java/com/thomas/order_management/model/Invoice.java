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
 * Faktura (SAP: Fakturaart F2 Rechnung, S1 Stornorechnung). Lieferbezogen: eine Rechnung je Lieferung.
 */
@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoices_order", columnList = "order_id"),
        @Index(name = "idx_invoices_customer", columnList = "customer_id"),
        @Index(name = "idx_invoices_billing_date", columnList = "billing_date")
})
@Getter
@Setter
public class Invoice {

    public enum BillingType {
        F2("Rechnung"),
        S1("Stornorechnung");

        private final String displayName;

        BillingType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Status {
        OPEN("Offen"),
        PAID("Ausgeglichen"),
        CANCELLED("Storniert");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 10)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 2)
    private BillingType billingType = BillingType.F2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    /** Regulierer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Bei Stornorechnungen: die stornierte Rechnung. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_invoice_id")
    private Invoice referenceInvoice;

    @Column(name = "billing_date", nullable = false)
    private LocalDate billingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_terms", length = 4)
    private PaymentTerms paymentTerms;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "cash_discount_date")
    private LocalDate cashDiscountDate;

    @Column(name = "cash_discount_percent", precision = 5, scale = 2)
    private BigDecimal cashDiscountPercent = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = Order.CURRENCY;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount = BigDecimal.ZERO;

    /** Summe der Einstandskosten (statistische Kondition VPRS). */
    @Column(name = "cost_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "paid_at")
    private LocalDate paidAt;

    @Column(name = "paid_amount", precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "cash_discount_amount", precision = 12, scale = 2)
    private BigDecimal cashDiscountAmount;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("itemNumber ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void addItem(InvoiceItem item) {
        item.setInvoice(this);
        items.add(item);
    }

    public void recalculateTotals() {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (InvoiceItem item : items) {
            net = net.add(item.getNetAmount());
            tax = tax.add(item.getTaxAmount());
            cost = cost.add(item.getCostAmount());
        }
        this.netAmount = net;
        this.taxAmount = tax;
        this.grossAmount = net.add(tax);
        this.costAmount = cost;
    }

    public boolean isOverdue(LocalDate today) {
        return status == Status.OPEN && billingType == BillingType.F2 && dueDate != null && dueDate.isBefore(today);
    }
}
