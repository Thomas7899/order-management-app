// order-management/src/main/java/com/thomas/order_management/model/ReviewEmbedding.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.List;

@Entity
@Table(name = "review_embeddings")
@Getter
@Setter
public class ReviewEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Jede Review hat genau ein Embedding.
     * review_id ist der korrekte Foreign Key auf product_reviews.id.
     * ON DELETE CASCADE löscht Embeddings automatisch mit, wenn Reviews gelöscht werden.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "review_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_review_embedding_review")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProductReview review;

    /**
     * OpenAI Embedding (1536 Dimensionen bei text-embedding-3-small).
     * Wird als pgvector gespeichert.
     */
    @Column(columnDefinition = "vector(1536)")
    private List<Double> embedding;

    /**
     * Optional: Originaltext, aus dem das Embedding erzeugt wurde.
     */
    @Column(length = 2000)
    private String sourceText;
}
