// order-management/src/main/java/com/thomas/order_management/dto/inventory/InventoryAiDto.java
package com.thomas.order_management.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTOs für KI-gestützte Inventory-Analysen
 */
public class InventoryAiDto {

    /**
     * Bestandsprognose für ein Produkt
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DemandForecast {
        private Long productId;
        private String productName;
        private String category;
        private Integer currentStock;
        private Integer predictedDemand7Days;
        private Integer predictedDemand14Days;
        private Integer predictedDemand30Days;
        private Double confidenceScore;
        private String trendDirection; // "RISING", "STABLE", "FALLING"
        private String aiInsight;
        private LocalDate stockoutRiskDate; // Wann könnte Ware ausgehen?
        private String seasonalityFactor; // "HIGH", "MEDIUM", "LOW", "NONE"
    }

    /**
     * Nachbestellungs-Empfehlung
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReorderRecommendation {
        private Long productId;
        private String productName;
        private String category;
        private Integer currentStock;
        private Integer minStock;
        private Integer recommendedOrderQuantity;
        private String urgency; // "CRITICAL", "HIGH", "MEDIUM", "LOW"
        private String reason;
        private BigDecimal estimatedCost;
        private LocalDate suggestedOrderDate;
        private Integer daysUntilStockout;
        private Double reviewSentimentImpact; // Wie beeinflusst Sentiment die Nachfrage?
    }

    /**
     * Lager-Anomalie
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryAnomaly {
        private Long productId;
        private String productName;
        private String warehouseName;
        private String anomalyType; // "UNUSUAL_MOVEMENT", "STOCK_DISCREPANCY", "DEMAND_SPIKE", "SUDDEN_DROP"
        private String severity; // "HIGH", "MEDIUM", "LOW"
        private String description;
        private String suggestedAction;
        private LocalDate detectedAt;
        private Object anomalyData; // Zusätzliche Daten zur Anomalie
    }

    /**
     * Kompletter KI-Report für Inventory
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryAiReport {
        private LocalDate generatedAt;
        private String executiveSummary;
        private List<DemandForecast> demandForecasts;
        private List<ReorderRecommendation> reorderRecommendations;
        private List<InventoryAnomaly> anomalies;
        private InventoryHealthScore healthScore;
        private List<String> actionItems;
    }

    /**
     * Inventory Health Score
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryHealthScore {
        private Integer overallScore; // 0-100
        private String grade; // "A", "B", "C", "D", "F"
        private Integer stockAvailabilityScore;
        private Integer turnoverScore;
        private Integer accuracyScore;
        private List<String> strengths;
        private List<String> weaknesses;
        private List<String> opportunities;
    }

    /**
     * Request für spezifische Produkt-Prognose
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastRequest {
        private Long productId;
        private Integer forecastDays;
        private Boolean includeSeasonality;
        private Boolean includeReviewSentiment;
    }
}
