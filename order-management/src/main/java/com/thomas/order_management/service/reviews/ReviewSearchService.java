package com.thomas.order_management.service.reviews;

import com.thomas.order_management.dto.reviews.ReviewSearchDtos.*;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.model.ReviewInsight;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewEmbeddingRepository.SimilarReview;
import com.thomas.order_management.repository.ReviewInsightRepository;
import com.thomas.order_management.service.ReviewEmbeddingService;
import com.thomas.order_management.service.ai.AiAvailability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Semantische Suche: findet Bewertungen nach Bedeutung. Treffer unter der Mindestähnlichkeit werden
 * verworfen, damit unpassende Anfragen auch keine Treffer liefern.
 */
@Service
@Transactional(readOnly = true)
public class ReviewSearchService {

    private final ReviewEmbeddingService embeddingService;
    private final ProductReviewRepository reviewRepository;
    private final ReviewInsightRepository insightRepository;
    private final AiAvailability ai;
    private final double minSimilarity;
    private final int defaultLimit;

    public ReviewSearchService(ReviewEmbeddingService embeddingService, ProductReviewRepository reviewRepository,
                               ReviewInsightRepository insightRepository, AiAvailability ai,
                               @Value("${app.ai.search.min-similarity:0.42}") double minSimilarity,
                               @Value("${app.ai.search.default-limit:30}") int defaultLimit) {
        this.embeddingService = embeddingService;
        this.reviewRepository = reviewRepository;
        this.insightRepository = insightRepository;
        this.ai = ai;
        this.minSimilarity = minSimilarity;
        this.defaultLimit = defaultLimit;
    }

    public SearchResult search(String query, Integer limit) {
        if (!ai.isAvailable()) {
            throw new BusinessException("ZR", "010", "Die semantische Suche braucht einen OpenAI-Schlüssel (OPENAI_API_KEY)",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        int max = Math.max(1, Math.min(limit == null ? defaultLimit : limit, 100));
        List<SimilarReview> candidates = embeddingService.findSimilar(query.trim(), Math.min(max * 3, 300)).stream()
                .filter(c -> c.similarity() >= minSimilarity)
                .toList();
        if (candidates.isEmpty()) {
            return new SearchResult(query, minSimilarity, 0, List.of(), List.of());
        }

        List<Long> ids = candidates.stream().map(SimilarReview::reviewId).toList();
        Map<Long, ProductReview> reviews = reviewRepository.findAllByIdWithProductAndCustomer(ids).stream()
                .collect(Collectors.toMap(ProductReview::getId, Function.identity()));
        Map<Long, ReviewInsight> insights = insightRepository.findWithAspectsByReviewIdIn(ids).stream()
                .collect(Collectors.toMap(ReviewInsight::getReviewId, Function.identity()));

        // Gleichlautende Bewertungen zusammenfassen; der ähnlichste Treffer bleibt stehen
        LinkedHashMap<String, Hit> byText = new LinkedHashMap<>();
        List<ProductReview> matched = new ArrayList<>();
        for (SimilarReview candidate : candidates) {
            ProductReview r = reviews.get(candidate.reviewId());
            if (r == null) continue;
            matched.add(r);
            String key = r.getComment() == null ? "" : r.getComment().trim().toLowerCase(Locale.GERMAN);
            Hit existing = byText.get(key);
            if (existing != null) {
                byText.put(key, withDuplicate(existing));
                continue;
            }
            if (byText.size() >= max) continue;
            ReviewInsight insight = insights.get(r.getId());
            byText.put(key, new Hit(r.getId(), r.getComment(), r.getRating(), r.getCreatedAt(),
                    r.getProduct().getId(), r.getProduct().getName(),
                    r.getCustomer() != null ? r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName() : null,
                    r.getOrderItem() != null && r.getOrderItem().getOrder() != null ? r.getOrderItem().getOrder().getId() : null,
                    Math.round(candidate.similarity() * 100) / 100.0, 0,
                    insight != null ? insight.getSentiment().name() : null,
                    insight != null ? insight.getAspects().stream().map(a -> a.getTheme().getDisplayName()).distinct().toList() : List.of()));
        }

        List<ProductHits> products = matched.stream()
                .collect(Collectors.groupingBy(r -> r.getProduct().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(list -> new ProductHits(list.get(0).getProduct().getId(), list.get(0).getProduct().getName(),
                        list.size(), Math.round(list.stream().mapToInt(ProductReview::getRating).average().orElse(0) * 10) / 10.0))
                .sorted(Comparator.comparingLong(ProductHits::hits).reversed())
                .limit(10)
                .toList();

        return new SearchResult(query, minSimilarity, matched.size(), List.copyOf(byText.values()), products);
    }

    private static Hit withDuplicate(Hit h) {
        return new Hit(h.reviewId(), h.comment(), h.rating(), h.createdAt(), h.productId(), h.productName(),
                h.customerName(), h.orderId(), h.similarity(), h.duplicates() + 1, h.sentiment(), h.themes());
    }
}
