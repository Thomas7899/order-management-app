package com.thomas.order_management.dto.reviews;

import com.thomas.order_management.dto.sales.CodeText;
import com.thomas.order_management.service.reviews.ReviewInsightService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Antworten der Bewertungsauswertung. Kennzahlen sind immer berechnet; nur Texte in
 * {@code summary}, {@code assessment} und {@code recommendations} stammen bei verfügbarer KI vom
 * Sprachmodell ({@code narrativeSource} = KI, sonst REGELN).
 */
public final class ReviewInsightDtos {

    private ReviewInsightDtos() {
    }

    public record PeriodStats(long reviews, double avgRating, double sentimentScore,
                              double positiveShare, double negativeShare) {
    }

    /** Thema im Zeitraum; direction vergleicht den Anteil negativer Erwähnungen mit der Vorperiode. */
    public record ThemeTrend(CodeText theme, long mentions, long previousMentions, double positiveShare,
                             double negativeShare, double previousNegativeShare, String direction) {
    }

    public record ProductMovement(Long productId, String productName, String category, long reviews, double avgRating,
                                  long previousReviews, Double previousAvgRating, Double delta) {
    }

    public record ReviewQuote(Long reviewId, Long productId, String productName, int rating, LocalDateTime createdAt,
                              String comment, String sentiment, List<String> themes) {
    }

    public record Count(String code, String text, long count) {
    }

    public record Classification(long reviews, long byAi, long byRules, boolean aiAvailable,
                                 ReviewInsightService.JobStatus job) {
    }

    public record TrendReport(String scope, String title, LocalDate windowStart, LocalDate windowEnd,
                              LocalDate previousStart, LocalDate previousEnd, PeriodStats current, PeriodStats previous,
                              List<ThemeTrend> themes, List<ProductMovement> declining, List<ProductMovement> improving,
                              List<String> positiveTrends, List<String> negativeTrends, List<String> observations,
                              String summary, List<String> recommendations, String narrativeSource,
                              List<ReviewQuote> evidence) {
    }

    public record ProductAnomaly(Long productId, String productName, String category, String severity, long reviews,
                                 double avgRating, long baselineReviews, Double baselineAvgRating, double negativeShare,
                                 List<String> topProblems, String assessment, String recommendedAction,
                                 List<ReviewQuote> evidence) {
    }

    public record ThemeAnomaly(CodeText theme, long negativeMentions, double negativePerWeek,
                               double baselineNegativePerWeek, double negativeShare, double baselineNegativeShare,
                               String assessment) {
    }

    public record AnomalyReport(LocalDate windowStart, LocalDate windowEnd, LocalDate baselineStart,
                                List<ProductAnomaly> products, List<ThemeAnomaly> themes, String narrativeSource) {
    }

    public record SentimentPoint(LocalDate periodStart, long reviews, double avgScore, long positive, long neutral,
                                 long negative) {
    }

    public record SentimentReport(LocalDate windowStart, LocalDate windowEnd, LocalDate previousStart,
                                  LocalDate previousEnd, PeriodStats current, PeriodStats previous,
                                  List<Count> sentiments, List<Count> emotions, List<ThemeTrend> themes,
                                  List<SentimentPoint> timeline, String timelineGranularity,
                                  List<ReviewQuote> criticalReviews, Classification classification) {
    }
}
