// order-management/src/main/java/com/thomas/order_management/model/StockMovement.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Materialbeleg mit SAP-Bewegungsart. Bestände werden nur über Materialbelege verändert;
 * eine Stornierung erzeugt einen Gegenbeleg mit der Storno-Bewegungsart.
 */
@Entity
@Table(name = "stock_movements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_movements_document_item", columnNames = {"movement_number", "document_item"})
}, indexes = {
        @Index(name = "idx_stock_movements_product", columnList = "product_id"),
        @Index(name = "idx_stock_movements_posting_date", columnList = "posting_date")
})
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Materialbelegnummer (49xxxxxxxx); mehrere Positionen teilen sich eine Nummer. */
    @Column(name = "movement_number", nullable = false, length = 10)
    private String movementNumber;

    /** Position im Materialbeleg (1, 2, 3, ...). */
    @Column(name = "document_item", nullable = false)
    private Integer documentItem = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovementType movementType;

    /** SAP-Bewegungsart, z. B. 101, 601, 311, 701. */
    @Column(name = "movement_code", nullable = false, length = 3)
    private String movementCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id")
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_warehouse_id")
    private Warehouse targetWarehouse;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "quantity_before")
    private Integer quantityBefore;

    @Column(name = "quantity_after")
    private Integer quantityAfter;

    @Column
    private String reason;

    @Column(length = 35)
    private String referenceNumber; // z. B. Lieferung, Bestellung, Inventurbeleg

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReferenceType referenceType;

    /** Bei Stornobelegen: der stornierte Materialbeleg. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_movement_id")
    private StockMovement reversedMovement;

    /** Wurde dieser Beleg bereits storniert? */
    @Column(nullable = false)
    private boolean reversed;

    @Column(name = "posting_date", nullable = false)
    private LocalDateTime postingDate;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum MovementType {
        INITIAL_ENTRY("561", "562", "Initialer Bestandsaufbau"),
        GOODS_RECEIPT("101", "102", "Wareneingang"),
        GOODS_ISSUE("201", "202", "Warenausgang Kostenstelle"),
        DELIVERY_ISSUE("601", "602", "Warenausgang Lieferung"),
        TRANSFER("311", "312", "Umlagerung Lagerort an Lagerort"),
        INVENTORY_ADJUSTMENT("701", "702", "Inventurdifferenz"),
        RETURN("651", "652", "Retoure vom Kunden"),
        SCRAP("551", "552", "Verschrottung");

        private final String code;
        private final String reversalCode;
        private final String description;

        MovementType(String code, String reversalCode, String description) {
            this.code = code;
            this.reversalCode = reversalCode;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getReversalCode() {
            return reversalCode;
        }

        public String getDescription() {
            return description;
        }

        public boolean increasesStock() {
            return this == INITIAL_ENTRY || this == GOODS_RECEIPT || this == RETURN;
        }
    }

    public enum ReferenceType {
        ORDER,
        DELIVERY,
        PURCHASE_ORDER,
        RETURN_ORDER,
        INVENTORY,
        MANUAL
    }

    @PrePersist
    public void onPersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.postingDate == null) {
            this.postingDate = this.createdAt;
        }
    }
}
