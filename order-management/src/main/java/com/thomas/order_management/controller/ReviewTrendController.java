// order-management/src/main/java/com/thomas/order_management/controller/ReviewTrendController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.model.ReviewTrendReport;
import com.thomas.order_management.service.ReviewTrendAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/reviews/trends")
@RequiredArgsConstructor
public class ReviewTrendController {

    private final ReviewTrendAnalysisService analysisService;

    @GetMapping("/latest")
    public ResponseEntity<ReviewTrendReport> latest() {
        List<ReviewTrendReport> all = analysisService.listAll();
        return all.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(all.stream()
                .max(Comparator.comparing(ReviewTrendReport::getGeneratedAt))
                .orElseThrow());
    }

    @GetMapping
    public List<ReviewTrendReport> list() {
        return analysisService.listAll().stream()
                .sorted(Comparator.comparing(ReviewTrendReport::getGeneratedAt).reversed())
                .toList();
    }

    @PostMapping("/analyze")
    public ReviewTrendReport analyze(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate windowStart,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate windowEnd) {
        return analysisService.analyze(windowStart, windowEnd);
    }
}
