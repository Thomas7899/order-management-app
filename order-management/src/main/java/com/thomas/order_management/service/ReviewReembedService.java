// src/main/java/com/thomas/order_management/service/ReviewReembedService.java
package com.thomas.order_management.service;

import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewReembedService {

    private final ProductReviewRepository reviewRepo;
    private final ReviewEmbeddingService embeddingService;

    @Transactional
    public int reembedAll() {
        var reviews = reviewRepo.findAll();
        int n = 0;
        for (ProductReview r : reviews) {
            embeddingService.createEmbedding(r);
            n++;
        }
        return n;
    }
}
