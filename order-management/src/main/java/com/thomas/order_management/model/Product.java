// order-management/src/main/java/com/thomas/order_management/model/Product.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter @Setter
@NoArgsConstructor
@NamedQueries({
    @NamedQuery(
        name = "Product.findByPriceRange",
        query = "SELECT p FROM Product p WHERE p.price BETWEEN :minPrice AND :maxPrice ORDER BY p.price"
    ),
    @NamedQuery(
        name = "Product.findTopSellingByCategory",
        query = "SELECT p FROM Product p WHERE p.category = :category ORDER BY p.stockQuantity DESC"
    ),
    @NamedQuery(
        name = "Product.findProductsAboveCategoryAverage",
        query = "SELECT p FROM Product p WHERE p.price > (SELECT AVG(pr.price) FROM Product pr WHERE pr.category = p.category)"
    )
})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column
    private String category;

    @Column
    private String imageUrl;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<OrderItem> orderItems = new ArrayList<>();

    public Product(String name, String description, BigDecimal price, Integer stockQuantity) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = (stockQuantity != null) ? stockQuantity : 0;
    }

    public boolean isInStock() {
        return stockQuantity != null && stockQuantity > 0;
    }

    public boolean isInStock(int quantity) {
        return stockQuantity != null && stockQuantity >= quantity;
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
