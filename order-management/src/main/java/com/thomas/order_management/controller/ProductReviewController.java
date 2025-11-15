// order-management/src/main/java/com/thomas/order_management/controller/ProductReviewController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.ReviewDTO;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.OrderItem;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.service.ReviewEmbeddingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reviews")
public class ProductReviewController {

    private final ReviewEmbeddingService embeddingService;

    public ProductReviewController(ReviewEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @GetMapping("/similar")
    public List<ReviewDTO> similar(
            @RequestParam String query,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return embeddingService.findSimilar(query, limit).stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    public ProductReview createReview(@RequestBody ProductReview review) {
        return embeddingService.createEmbedding(review);
    }

    private ReviewDTO toDto(ProductReview r) {
        Product p = r.getProduct();
        Customer c = r.getCustomer();
        OrderItem oi = r.getOrderItem();

        Long orderId = oi != null && oi.getOrder() != null ? oi.getOrder().getId() : null;
        Long orderItemId = oi != null ? oi.getId() : null;

        return new ReviewDTO(
                r.getId(),
                r.getComment(),
                r.getRating(),
                r.getCreatedAt(),
                p != null ? p.getId() : null,
                p != null ? p.getName() : null,
                p != null ? p.getPrice() : null,
                c != null ? c.getId() : null,
                c != null ? c.getFirstName() + " " + c.getLastName() : null,
                orderId,
                orderItemId
        );
    }
}
