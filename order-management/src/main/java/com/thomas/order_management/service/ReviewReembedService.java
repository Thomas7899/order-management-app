// order-management/src/main/java/com/thomas/order_management/service/ReviewReembedService.java
// order-management/src/main/java/com/thomas/order_management/service/ReviewReembedService.java
package com.thomas.order_management.service;

import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewReembedService {

    private final ProductReviewRepository reviewRepo;
    private final ReviewEmbeddingService embeddingService;

    /**
     * Re-embedet ALLE Reviews (z. B. nach Modellwechsel oder verbessertem Prompt).
     */
    @Transactional
    public int reembedAll() {
        List<ProductReview> reviews = reviewRepo.findAll();

        log.info("Starte Re-Embedding von {} Reviews ...", reviews.size());

        int n = 0;
        for (ProductReview r : reviews) {
            embeddingService.createEmbedding(r);  // erzeugt jetzt upsert + enriched text
            n++;

            if (n % 50 == 0) {
                log.info("{} Reviews neu eingebettet ...", n);
            }
        }

        log.info("Re-Embedding abgeschlossen. Total: {}", n);
        return n;
    }
}
