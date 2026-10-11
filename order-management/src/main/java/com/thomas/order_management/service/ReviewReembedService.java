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

    /** Bettet alle Bewertungen neu ein (z. B. nach einem Modellwechsel). */
    @Transactional(readOnly = true)
    public int reembedAll() {
        List<ProductReview> reviews = reviewRepo.findAll();
        log.info("Starte Re-Embedding von {} Bewertungen ...", reviews.size());
        int done = embeddingService.embed(reviews);
        log.info("Re-Embedding abgeschlossen: {} von {}", done, reviews.size());
        return done;
    }
}
