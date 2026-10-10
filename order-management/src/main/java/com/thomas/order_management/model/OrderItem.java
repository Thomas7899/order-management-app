// order-management/src/main/java/com/thomas/order_management/model/OrderItem.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Auftragsposition mit Mengenfortschreibung (bestätigt, geliefert, fakturiert)
 * und den Konditionszeilen der Preisfindung.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Positionsnummer (10, 20, 30, ...). */
    @Column(name = "item_number", nullable = false)
    private Integer itemNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Auftragsmenge. */
    @Column(nullable = false)
    private Integer quantity;

    /** Durch die Verfügbarkeitsprüfung bestätigte Menge. */
    @Column(name = "confirmed_quantity", nullable = false)
    private Integer confirmedQuantity = 0;

    /** In Lieferungen übernommene Menge. */
    @Column(name = "delivered_quantity", nullable = false)
    private Integer deliveredQuantity = 0;

    @Column(name = "billed_quantity", nullable = false)
    private Integer billedQuantity = 0;

    /** Listenpreis je Mengeneinheit (Konditionsart PR00). */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** Manueller Positionsrabatt in Prozent (Konditionsart RA01). */
    @Column(name = "item_discount_percent", precision = 5, scale = 2)
    private BigDecimal itemDiscountPercent = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 4)
    private RejectionReason rejectionReason;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepNumber ASC")
    private List<PricingCondition> conditions = new ArrayList<>();

    public OrderItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public boolean isRejected() {
        return rejectionReason != null;
    }

    /** Bestätigte, aber noch nicht in eine Lieferung übernommene Menge. */
    public int getOpenConfirmedQuantity() {
        if (isRejected()) return 0;
        return Math.max(0, confirmedQuantity - deliveredQuantity);
    }

    /** Noch nicht gelieferte Auftragsmenge. */
    public int getOpenQuantity() {
        if (isRejected()) return 0;
        return Math.max(0, quantity - deliveredQuantity);
    }

    /** Nettopreis je Einheit nach allen Rabatten. */
    public BigDecimal getNetUnitPrice() {
        if (quantity == null || quantity == 0) return BigDecimal.ZERO;
        return netAmount.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP);
    }

    public void replaceConditions(List<PricingCondition> newConditions) {
        conditions.clear();
        for (PricingCondition condition : newConditions) {
            condition.setOrderItem(this);
            conditions.add(condition);
        }
    }
}
