// order-management/src/main/java/com/thomas/order_management/dto/ReviewSentimentDto.java
package com.thomas.order_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTOs für erweiterte KI-gestützte Sentiment-Analyse
 */
public class ReviewSentimentDto {

    /**
     * Erweitertes Sentiment mit Emotionen
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnhancedSentiment {
        private Long reviewId;
        private String sentiment; // "POSITIVE", "NEUTRAL", "NEGATIVE"
        private Double sentimentScore; // -1.0 bis 1.0
        private String primaryEmotion; // "JOY", "FRUSTRATION", "ANGER", "DISAPPOINTMENT", "SATISFACTION", "SURPRISE"
        private Double emotionIntensity; // 0.0 bis 1.0
        private List<String> detectedThemes; // ["Lieferung", "Qualität", "Preis", "Service"]
        private String urgencyLevel; // "URGENT", "NORMAL", "LOW"
        private Boolean requiresFollowUp;
        private String suggestedResponse;
    }

    /**
     * Automatisch kategorisierte Review
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorizedReview {
        private Long reviewId;
        private String comment;
        private Integer rating;
        private String productName;
        private String primaryCategory; // Hauptthema
        private List<String> secondaryCategories; // Weitere Themen
        private Map<String, Double> categoryConfidence; // Kategorie -> Konfidenz
        private List<String> extractedKeywords;
        private String actionableInsight;
    }

    /**
     * Themen-Cluster Analyse
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThemeCluster {
        private String themeName;
        private String themeDescription;
        private Integer reviewCount;
        private Double averageRating;
        private String overallSentiment;
        private List<String> topKeywords;
        private List<Long> sampleReviewIds;
        private String trendDirection; // "IMPROVING", "STABLE", "DECLINING"
        private String businessImpact; // "HIGH", "MEDIUM", "LOW"
    }

    /**
     * Sentiment-Trend über Zeit
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentTrend {
        private LocalDate date;
        private Double averageSentiment;
        private Integer positiveCount;
        private Integer neutralCount;
        private Integer negativeCount;
        private String dominantEmotion;
        private List<String> emergingThemes;
    }

    /**
     * Kompletter erweiterter Sentiment-Report
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnhancedSentimentReport {
        private LocalDate windowStart;
        private LocalDate windowEnd;
        private String executiveSummary;
        
        // Aggregierte Metriken
        private Double overallSentimentScore;
        private Map<String, Integer> emotionDistribution;
        private Map<String, Integer> themeDistribution;
        
        // Detaillierte Analysen
        private List<ThemeCluster> themeClusters;
        private List<SentimentTrend> sentimentTrends;
        private List<CategorizedReview> criticalReviews; // Reviews die Aufmerksamkeit brauchen
        
        // Handlungsempfehlungen
        private List<String> priorityActions;
        private List<String> positiveHighlights;
        private List<String> areasForImprovement;
        
        // Vergleich
        private Double sentimentChangeVsPreviousPeriod;
        private String performanceTrend;
    }

    /**
     * Request für Sentiment-Analyse
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentAnalysisRequest {
        private LocalDate startDate;
        private LocalDate endDate;
        private Long productId; // Optional: für produktspezifische Analyse
        private String category; // Optional: für kategoriesspezifische Analyse
        private Boolean includeEmotions;
        private Boolean includeThemeClustering;
        private Boolean includeActionableInsights;
    }
}
