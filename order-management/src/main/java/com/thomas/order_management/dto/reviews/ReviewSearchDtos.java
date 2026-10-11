package com.thomas.order_management.dto.reviews;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ReviewSearchDtos {

    private ReviewSearchDtos() {
    }

    /** Treffer der semantischen Suche; gleichlautende Bewertungen sind zu einem Treffer zusammengefasst. */
    public record Hit(Long reviewId, String comment, int rating, LocalDateTime createdAt, Long productId,
                      String productName, String customerName, Long orderId, double similarity, int duplicates,
                      String sentiment, List<String> themes) {
    }

    /** Wie oft ein Material unter den Treffern vorkommt. */
    public record ProductHits(Long productId, String productName, long hits, double avgRating) {
    }

    public record SearchResult(String query, double minSimilarity, int totalHits, List<Hit> hits,
                               List<ProductHits> products) {
    }

    public record CreateReview(@NotNull Long productId, @NotNull Long customerId, Long orderItemId,
                               @NotNull @Min(1) @Max(5) Integer rating,
                               @NotBlank @Size(max = 2000) String comment) {
    }
}
