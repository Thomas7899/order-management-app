// order-management/src/main/java/com/thomas/order_management/controller/ReviewController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.service.ReviewEmbeddingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewEmbeddingService reviewEmbeddingService;

    public ReviewController(ReviewEmbeddingService reviewEmbeddingService) {
        this.reviewEmbeddingService = reviewEmbeddingService;
    }

    // http://localhost:8080/api/reviews/similar?query=great%20sound%20quality
    @GetMapping("/similar")
    public List<ProductReview> findSimilarReviews(@RequestParam String query) {
        return reviewEmbeddingService.findSimilar(query);
    }
}

