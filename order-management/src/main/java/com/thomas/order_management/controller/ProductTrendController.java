// order-management/src/main/java/com/thomas/order_management/controller/ProductTrendController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.ProductTrendReportDTO;
import com.thomas.order_management.service.ReviewProductTrendService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reviews/trends/product")
public class ProductTrendController {

    private final ReviewProductTrendService service;

    public ProductTrendController(ReviewProductTrendService service) {
        this.service = service;
    }

    @GetMapping("/{productId}")
    public ProductTrendReportDTO analyze(
            @PathVariable Long productId,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end
    ) {
        return service.analyzeProduct(productId, start, end);
    }
}
