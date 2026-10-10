package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "item_number", nullable = false)
    private Integer itemNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_item_id")
    private DeliveryItem deliveryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Fakturierte Menge (negativ bei Stornorechnungen). */
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_net_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitNetPrice;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "cost_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal costAmount = BigDecimal.ZERO;
}
