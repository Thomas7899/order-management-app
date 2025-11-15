// order-management/src/main/java/com/thomas/order_management/controller/ReviewController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.ReviewDTO;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.OrderItem;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.service.ReviewEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewEmbeddingService reviewEmbeddingService;
    private final ProductReviewRepository reviewRepository;

    @GetMapping("/similar")
    public List<ReviewDTO> findSimilarReviews(
            @RequestParam String query,
            @RequestParam(defaultValue = "50") int limit
    ) {

        var similar = reviewEmbeddingService.findSimilar(query, limit);
        var ids = similar.stream().map(ProductReview::getId).toList();

        var loaded = reviewRepository.findAllByIdWithProductAndCustomer(ids);

        return loaded.stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/product/{productId}")
    public List<ReviewDTO> getByProduct(@PathVariable Long productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/customer/{customerId}")
    public List<ReviewDTO> getByCustomer(@PathVariable Long customerId) {
        return reviewRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/order/{orderId}")
    public List<ReviewDTO> getByOrder(@PathVariable Long orderId) {
        return reviewRepository.findByOrderId(orderId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/order-item/{orderItemId}")
    public List<ReviewDTO> getByOrderItem(@PathVariable Long orderItemId) {
        return reviewRepository.findByOrderItemId(orderItemId)
                .stream()
                .map(this::toDto)
                .toList();
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
