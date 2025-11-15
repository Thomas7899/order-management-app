// order-management/src/main/java/com/thomas/order_management/model/ReviewEmbedding.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "review_embeddings")
@Getter
@Setter
public class ReviewEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private ProductReview review;

    @Column(columnDefinition = "vector(1536)")
    private List<Double> embedding;

    @Column(length = 2000)
    private String sourceText;
}
