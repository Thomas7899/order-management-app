// order-management/src/main/java/com/thomas/order_management/controller/ReviewController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.ReviewDTO;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.OrderItem;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.dto.reviews.ReviewSearchDtos.CreateReview;
import com.thomas.order_management.dto.reviews.ReviewSearchDtos.SearchResult;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.OrderItemRepository;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.service.ReviewEmbeddingService;
import com.thomas.order_management.service.ai.AiAvailability;
import com.thomas.order_management.service.reviews.ReviewInsightService;
import com.thomas.order_management.service.reviews.ReviewSearchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewSearchService searchService;
    private final ReviewEmbeddingService embeddingService;
    private final ReviewInsightService insightService;
    private final AiAvailability aiAvailability;
    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderItemRepository orderItemRepository;

    @GetMapping("/similar")
    public SearchResult findSimilarReviews(@RequestParam String query,
                                           @RequestParam(required = false) Integer limit) {
        return searchService.search(query, limit);
    }

    /** Neue Bewertung anlegen; sie wird sofort eingeordnet und (mit KI) für die Suche eingebettet. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReviewDTO create(@Valid @RequestBody CreateReview request) {
        ProductReview review = new ProductReview();
        review.setProduct(productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Material " + request.productId() + " nicht gefunden")));
        review.setCustomer(customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Debitor " + request.customerId() + " nicht gefunden")));
        if (request.orderItemId() != null) {
            review.setOrderItem(orderItemRepository.findById(request.orderItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Auftragsposition " + request.orderItemId() + " nicht gefunden")));
        }
        review.setRating(request.rating());
        review.setComment(request.comment().trim());
        review.setCreatedAt(LocalDateTime.now());
        ProductReview saved = reviewRepository.save(review);

        insightService.classify(saved);
        if (aiAvailability.isAvailable()) {
            embeddingService.embed(List.of(saved));
        }
        return toDto(saved);
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
