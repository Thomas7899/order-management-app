package com.thomas.order_management.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bestandsanalyse. Alle Zahlen sind berechnet (Warenausgänge, Bestände, Disposition, Rückstand);
 * nur {@code executiveSummary} und {@code actionItems} formuliert bei verfügbarer KI das Sprachmodell.
 */
public final class InventoryAnalysisDto {

    private InventoryAnalysisDto() {
    }

    public record DemandForecast(Long productId, String productName, String category, int currentStock,
                                 double dailyDemand, int predictedDemand7Days, int predictedDemand14Days,
                                 int predictedDemand30Days, String trendDirection, double confidence,
                                 Integer reachDays, LocalDate stockoutRiskDate, int backlog) {
    }

    public record ReorderRecommendation(Long productId, String productName, String warehouseName, int currentStock,
                                        int minStock, int maxStock, double dailyDemand, int backlog,
                                        int recommendedOrderQuantity, String urgency, String reason,
                                        BigDecimal estimatedCost, LocalDate suggestedOrderDate,
                                        Integer daysUntilStockout) {
    }

    public record InventoryAnomaly(Long productId, String productName, String warehouseName, String anomalyType,
                                   String severity, String description, String suggestedAction) {
    }

    public record HealthScore(int overallScore, String grade, int stockAvailabilityScore, int turnoverScore,
                              int accuracyScore, double turnoverRate, List<String> strengths,
                              List<String> weaknesses) {
    }

    public record Report(LocalDateTime generatedAt, String executiveSummary, List<String> actionItems,
                         String narrativeSource, String method, List<DemandForecast> demandForecasts,
                         List<ReorderRecommendation> reorderRecommendations, List<InventoryAnomaly> anomalies,
                         HealthScore healthScore) {
    }
}
