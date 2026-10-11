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
import com.thomas.order_management.repository.ReviewInsightRepository;
import com.thomas.order_management.model.ReviewInsight;
import com.thomas.order_management.service.reviews.ReviewReplyService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ReviewInsightRepository insightRepository;
    private final ReviewReplyService replyService;

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
        return toDtos(List.of(saved)).get(0);
    }

    /** Kritische Bewertungen (1–2 Sterne) der letzten Tage, die noch keine Antwort haben. */
    @GetMapping("/open-critical")
    public OpenCritical openCritical(@RequestParam(defaultValue = "30") int days) {
        List<ProductReview> open = reviewRepository.findOpenCritical(LocalDateTime.now().minusDays(Math.max(1, days)));
        return new OpenCritical(open.size(), toDtos(open.stream().limit(50).toList()));
    }

    /** Antwortvorschlag (KI, sonst Vorlage); gespeichert wird erst der geprüfte Text. */
    @PostMapping("/{id}/reply-suggestion")
    public ReviewReplyService.Suggestion suggestReply(@PathVariable Long id) {
        return replyService.suggest(id);
    }

    @PutMapping("/{id}/reply")
    public ReviewDTO reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest request) {
        return toDtos(List.of(replyService.reply(id, request.text()))).get(0);
    }

    @GetMapping("/product/{productId}")
    public List<ReviewDTO> getByProduct(@PathVariable Long productId) {
        return toDtos(reviewRepository.findByProductIdOrderByCreatedAtDesc(productId));
    }

    @GetMapping("/customer/{customerId}")
    public List<ReviewDTO> getByCustomer(@PathVariable Long customerId) {
        return toDtos(reviewRepository.findByCustomerIdOrderByCreatedAtDesc(customerId));
    }

    @GetMapping("/order/{orderId}")
    public List<ReviewDTO> getByOrder(@PathVariable Long orderId) {
        return toDtos(reviewRepository.findByOrderId(orderId));
    }

    @GetMapping("/order-item/{orderItemId}")
    public List<ReviewDTO> getByOrderItem(@PathVariable Long orderItemId) {
        return toDtos(reviewRepository.findByOrderItemId(orderItemId));
    }

    public record OpenCritical(long count, List<ReviewDTO> reviews) {
    }

    public record ReplyRequest(@NotBlank @Size(max = 2000) String text) {
    }

    private List<ReviewDTO> toDtos(List<ProductReview> reviews) {
        Map<Long, ReviewInsight> insights = reviews.isEmpty() ? Map.of()
                : insightRepository.findWithAspectsByReviewIdIn(reviews.stream().map(ProductReview::getId).toList()).stream()
                .collect(Collectors.toMap(ReviewInsight::getReviewId, Function.identity()));
        return reviews.stream().map(r -> toDto(r, insights.get(r.getId()))).toList();
    }

    private ReviewDTO toDto(ProductReview r, ReviewInsight insight) {
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
                orderItemId,
                insight != null ? insight.getSentiment().name() : null,
                insight != null ? insight.getAspects().stream().map(a -> a.getTheme().getDisplayName()).distinct().toList() : List.of(),
                r.getReplyText(),
                r.getRepliedAt(),
                r.getRepliedBy()
        );
    }
}
