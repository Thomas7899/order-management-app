// order-management/src/main/java/com/thomas/order_management/service/ReviewEmbeddingService.java
// order-management/src/main/java/com/thomas/order_management/service/ReviewEmbeddingService.java
package com.thomas.order_management.service;

import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewEmbeddingService {

    private final ProductReviewRepository reviewRepository;
    private final ReviewEmbeddingRepository embeddingRepository;
    private final OpenAiEmbeddingModel embeddingModel;

    @Value("${app.similarity.top-k:50}")
    private int defaultTopK;

    /**
     * Erstellt ein Embedding für eine neue Review.
     */
    public ProductReview createEmbedding(ProductReview review) {
        ProductReview saved = reviewRepository.save(review);

        String enrichedText = buildEmbeddingText(saved);
        float[] embedding = embeddingModel.embed(enrichedText);

        List<Double> normalized = normalize(embedding);
        embeddingRepository.upsertEmbedding(saved.getId(), normalized);

        return saved;
    }

    /**
     * Findet ähnliche Reviews basierend auf Embedding-Similarity.
     */
    public List<ProductReview> findSimilar(String query, int limit) {
        float[] queryEmbedding = embeddingModel.embed(query);
        List<Double> vector = normalize(queryEmbedding);

        int topK = limit > 0 ? limit : defaultTopK;

        List<Long> ids = embeddingRepository.findSimilarReviewIds(vector, topK);

        if (ids.isEmpty()) return List.of();

        return reviewRepository.findAllByIdWithProductAndCustomer(ids);
    }

    /**
     * Baut erweiterten Text für verbesserte Embeddings.
     */
    private String buildEmbeddingText(ProductReview review) {
        StringBuilder sb = new StringBuilder();

        if (review.getProduct() != null) {
            sb.append("Produkt: ").append(review.getProduct().getName()).append("\n");

            if (review.getProduct().getCategory() != null) {
                sb.append("Kategorie: ").append(review.getProduct().getCategory()).append("\n");
            }
        }

        sb.append("Rating: ").append(review.getRating()).append("/5").append("\n");
        sb.append("Kommentar: ").append(review.getComment());

        return sb.toString();
    }

    /**
     * Normalisiert Embedding auf Länge 1.
     */
    private List<Double> normalize(float[] arr) {
    double sum = 0;

    for (float v : arr) {
        sum += v * v;
    }

    double norm = Math.sqrt(sum);
    if (norm == 0) norm = 1;

    List<Double> result = new java.util.ArrayList<>(arr.length);
    for (float v : arr) {
        result.add(v / norm);
    }
    return result;
}
}
