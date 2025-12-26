// order-management/src/main/java/com/thomas/order_management/model/StockMovement.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String movementNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType movementType;

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

    @Column
    private String referenceNumber; // z.B. Bestellnummer, Lieferscheinnummer

    @Enumerated(EnumType.STRING)
    @Column
    private ReferenceType referenceType;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum MovementType {
        GOODS_RECEIPT,      // Wareneingang
        GOODS_ISSUE,        // Warenausgang
        TRANSFER,           // Umbuchung zwischen Lagern
        INVENTORY_ADJUSTMENT, // Inventur-Korrektur
        RETURN,             // Retoure
        SCRAP               // Verschrottung/Entsorgung
    }

    public enum ReferenceType {
        ORDER,              // Bestellung
        PURCHASE_ORDER,     // Einkaufsbestellung
        RETURN_ORDER,       // Retoure
        INVENTORY,          // Inventur
        MANUAL              // Manuelle Buchung
    }

    public StockMovement(MovementType movementType, Product product, Integer quantity) {
        this.movementType = movementType;
        this.product = product;
        this.quantity = quantity;
        this.movementNumber = generateMovementNumber();
    }

    private String generateMovementNumber() {
        return "MOV-" + System.currentTimeMillis();
    }

    @PrePersist
    public void onPersist() {
        this.createdAt = LocalDateTime.now();
        if (this.movementNumber == null) {
            this.movementNumber = generateMovementNumber();
        }
    }
}
