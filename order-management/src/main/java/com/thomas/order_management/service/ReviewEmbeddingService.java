// order-management/src/main/java/com/thomas/order_management/service/ReviewEmbeddingService.java
package com.thomas.order_management.service;

import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ReviewEmbeddingService {

    private final ReviewEmbeddingRepository embeddingRepository;
    private final ProductReviewRepository reviewRepository;
    private final OpenAiEmbeddingModel embeddingModel;

    @Transactional
    public ProductReview createEmbedding(ProductReview review) {
        ProductReview saved = reviewRepository.save(review);

        float[] embedding = embeddingModel.embed(review.getComment());
        List<Double> vector = IntStream.range(0, embedding.length)
                .mapToObj(i -> (double) embedding[i])
                .collect(Collectors.toList());

        embeddingRepository.saveEmbedding(saved.getId(), vector);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ProductReview> findSimilar(String query) {
        float[] queryEmbedding = embeddingModel.embed(query);
        List<Double> vector = IntStream.range(0, queryEmbedding.length)
                .mapToObj(i -> (double) queryEmbedding[i])
                .collect(Collectors.toList());

        List<Long> ids = embeddingRepository.findSimilarReviewIds(vector);
        if (ids.isEmpty()) return List.of();

        return reviewRepository.findAllByIdWithProduct(ids);
    }
}

