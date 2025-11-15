// order-management/src/main/java/com/thomas/order_management/controller/CategoryTrendController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.CategoryTrendReportDTO;
import com.thomas.order_management.service.ReviewCategoryTrendService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reviews/trends/category")
public class CategoryTrendController {

    private final ReviewCategoryTrendService service;

    public CategoryTrendController(ReviewCategoryTrendService service) {
        this.service = service;
    }

    @GetMapping("/{category}")
    public CategoryTrendReportDTO analyze(
            @PathVariable String category,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end
    ) {
        return service.analyzeCategory(category, start, end);
    }
}
