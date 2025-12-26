// order-management/src/main/java/com/thomas/order_management/controller/ReviewSentimentController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.ReviewSentimentDto.*;
import com.thomas.order_management.service.ReviewSentimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST Controller für erweiterte KI-gestützte Sentiment-Analysen.
 */
@RestController
@RequestMapping("/api/reviews/sentiment")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewSentimentController {

    private final ReviewSentimentService sentimentService;

    /**
     * Analysiert eine einzelne Review mit erweitertem Sentiment.
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<EnhancedSentiment> analyzeReview(@PathVariable Long reviewId) {
        return ResponseEntity.ok(sentimentService.analyzeReview(reviewId));
    }

    /**
     * Analysiert mehrere Reviews.
     */
    @PostMapping("/batch")
    public ResponseEntity<List<EnhancedSentiment>> analyzeReviews(@RequestBody List<Long> reviewIds) {
        return ResponseEntity.ok(sentimentService.analyzeReviews(reviewIds));
    }

    /**
     * Generiert einen vollständigen erweiterten Sentiment-Report.
     */
    @GetMapping("/report")
    public ResponseEntity<EnhancedSentimentReport> getEnhancedReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String category) {

        SentimentAnalysisRequest request = new SentimentAnalysisRequest(
                startDate, endDate, productId, category,
                true, true, true
        );

        return ResponseEntity.ok(sentimentService.generateEnhancedReport(request));
    }

    /**
     * Kategorisiert Reviews nach Themen.
     */
    @GetMapping("/categorize")
    public ResponseEntity<List<CategorizedReview>> categorizeReviews(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(sentimentService.categorizeReviews(startDate, endDate));
    }

    /**
     * Generiert Themen-Cluster.
     */
    @GetMapping("/clusters")
    public ResponseEntity<List<ThemeCluster>> getThemeClusters(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(sentimentService.getThemeClusters(startDate, endDate));
    }
}
