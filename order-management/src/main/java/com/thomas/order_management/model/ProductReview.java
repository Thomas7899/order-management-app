// order-management/src/main/java/com/thomas/order_management/model/ProductReview.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@NoArgsConstructor
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Produkt, das bewertet wird
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    // CUSTOMER schreibt die Bewertung (nicht User!)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    // Optional: Welche konkrete bestellte Position bewertet wurde
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private OrderItem orderItem;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private int rating;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Vollständiger Konstruktor für Bewertungen zu einem gekauften Produkt
    public ProductReview(Product product,
                         Customer customer,
                         OrderItem orderItem,
                         String comment,
                         int rating) {
        this.product = product;
        this.customer = customer;
        this.orderItem = orderItem;
        this.comment = comment;
        this.rating = rating;
        this.createdAt = LocalDateTime.now();
    }

    // Vereinfachter Konstruktor (falls ohne OrderItem)
    public ProductReview(Product product,
                         Customer customer,
                         String comment,
                         int rating) {
        this(product, customer, null, comment, rating);
    }

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
