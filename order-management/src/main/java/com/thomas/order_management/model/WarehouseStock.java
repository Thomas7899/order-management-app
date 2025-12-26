// order-management/src/main/java/com/thomas/order_management/model/WarehouseStock.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse_stocks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"warehouse_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class WarehouseStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(nullable = false)
    private Integer minStock = 5;

    @Column(nullable = false)
    private Integer maxStock = 1000;

    @Column
    private String binLocation; // z.B. "A-12-3" (Gang-Regal-Fach)

    @Column(name = "last_counted_at")
    private LocalDateTime lastCountedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public WarehouseStock(Warehouse warehouse, Product product, Integer quantity) {
        this.warehouse = warehouse;
        this.product = product;
        this.quantity = quantity;
    }

    public boolean isLowStock() {
        return quantity <= minStock;
    }

    public boolean isOverStock() {
        return quantity > maxStock;
    }

    @PrePersist
    public void onPersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
