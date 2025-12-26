// order-management/src/main/java/com/thomas/order_management/service/InventoryAiService.java
package com.thomas.order_management.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.inventory.InventoryAiDto.*;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.model.WarehouseStock;
import com.thomas.order_management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KI-gestützter Service für intelligente Inventory-Analysen.
 * Nutzt OpenAI für Bestandsprognosen, Nachbestellempfehlungen und Anomalie-Erkennung.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryAiService {

    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generiert einen kompletten KI-Report für das gesamte Inventar.
     */
    public InventoryAiReport generateFullReport() {
        log.info("Generating full inventory AI report...");

        List<DemandForecast> forecasts = generateDemandForecasts(null, 30);
        List<ReorderRecommendation> recommendations = generateReorderRecommendations();
        List<InventoryAnomaly> anomalies = detectInventoryAnomalies();
        InventoryHealthScore healthScore = calculateHealthScore();
        String summary = generateExecutiveSummary(forecasts, recommendations, anomalies, healthScore);
        List<String> actionItems = generateActionItems(recommendations, anomalies);

        return InventoryAiReport.builder()
                .generatedAt(LocalDate.now())
                .executiveSummary(summary)
                .demandForecasts(forecasts)
                .reorderRecommendations(recommendations)
                .anomalies(anomalies)
                .healthScore(healthScore)
                .actionItems(actionItems)
                .build();
    }

    /**
     * Generiert Bestandsprognosen für alle oder ein spezifisches Produkt.
     */
    public List<DemandForecast> generateDemandForecasts(Long productId, int forecastDays) {
        List<Product> products = productId != null
                ? productRepository.findById(productId).map(List::of).orElse(List.of())
                : productRepository.findByActiveTrue();

        if (products.isEmpty()) {
            return List.of();
        }

        // Sammle historische Daten
        LocalDateTime startDate = LocalDateTime.now().minusMonths(3);
        List<Object[]> salesData = orderItemRepository.getProductSalesData();
        Map<Long, Integer> salesByProduct = salesData.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[5]).intValue(),
                        (a, b) -> a + b
                ));

        // Sammle Review-Sentiment für Nachfrage-Einschätzung
        Map<Long, Double> sentimentByProduct = calculateProductSentiments();

        // Bereite Daten für KI-Analyse vor
        List<Map<String, Object>> productDataList = new ArrayList<>();
        for (Product product : products) {
            Integer totalStock = stockRepository.getTotalStockByProduct(product.getId());
            Integer soldQuantity = salesByProduct.getOrDefault(product.getId(), 0);
            Double sentiment = sentimentByProduct.getOrDefault(product.getId(), 0.0);

            Map<String, Object> data = new HashMap<>();
            data.put("productId", product.getId());
            data.put("productName", product.getName());
            data.put("category", product.getCategory());
            data.put("currentStock", totalStock != null ? totalStock : 0);
            data.put("soldLast90Days", soldQuantity);
            data.put("averageDailySales", soldQuantity / 90.0);
            data.put("reviewSentiment", sentiment);
            data.put("price", product.getPrice());
            productDataList.add(data);
        }

        // KI-Analyse anfordern
        String prompt = buildDemandForecastPrompt(productDataList, forecastDays);
        String aiResponse = chatClient.prompt().user(prompt).call().content();

        return parseDemandForecasts(aiResponse, productDataList);
    }

    /**
     * Generiert intelligente Nachbestellungs-Empfehlungen.
     */
    public List<ReorderRecommendation> generateReorderRecommendations() {
        List<WarehouseStock> lowStockItems = stockRepository.findLowStockItems();
        List<WarehouseStock> allStock = stockRepository.findAll();

        if (allStock.isEmpty()) {
            return List.of();
        }

        // Berechne Verkaufsgeschwindigkeit und Sentiment
        Map<Long, Double> velocityByProduct = calculateSalesVelocity();
        Map<Long, Double> sentimentByProduct = calculateProductSentiments();

        List<Map<String, Object>> stockDataList = new ArrayList<>();
        for (WarehouseStock stock : allStock) {
            Product product = stock.getProduct();
            Double velocity = velocityByProduct.getOrDefault(product.getId(), 0.0);
            Double sentiment = sentimentByProduct.getOrDefault(product.getId(), 0.0);

            // Berechne Tage bis Stockout
            int daysUntilStockout = velocity > 0
                    ? (int) Math.floor(stock.getQuantity() / velocity)
                    : 999;

            Map<String, Object> data = new HashMap<>();
            data.put("productId", product.getId());
            data.put("productName", product.getName());
            data.put("category", product.getCategory());
            data.put("currentStock", stock.getQuantity());
            data.put("minStock", stock.getMinStock());
            data.put("maxStock", stock.getMaxStock());
            data.put("dailySalesVelocity", velocity);
            data.put("daysUntilStockout", daysUntilStockout);
            data.put("reviewSentiment", sentiment);
            data.put("unitPrice", product.getPrice());
            data.put("isLowStock", stock.getQuantity() <= stock.getMinStock());
            stockDataList.add(data);
        }

        String prompt = buildReorderPrompt(stockDataList);
        String aiResponse = chatClient.prompt().user(prompt).call().content();

        return parseReorderRecommendations(aiResponse, stockDataList);
    }

    /**
     * Erkennt Anomalien im Lagerbestand und bei Bewegungen.
     */
    public List<InventoryAnomaly> detectInventoryAnomalies() {
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);

        // Sammle Bewegungsdaten
        List<Object[]> movementStats = movementRepository.getMovementStatistics(startDate);
        List<Object[]> dailyTrends = movementRepository.getDailyMovementTrend(startDate);
        List<WarehouseStock> overStock = stockRepository.findOverStockItems();
        List<WarehouseStock> lowStock = stockRepository.findLowStockItems();

        // Bereite Daten für KI vor
        Map<String, Object> anomalyData = new HashMap<>();
        anomalyData.put("movementStats", formatMovementStats(movementStats));
        anomalyData.put("dailyTrends", formatDailyTrends(dailyTrends));
        anomalyData.put("overStockCount", overStock.size());
        anomalyData.put("lowStockCount", lowStock.size());
        anomalyData.put("overStockProducts", overStock.stream()
                .map(s -> Map.of(
                        "product", s.getProduct().getName(),
                        "quantity", s.getQuantity(),
                        "maxStock", s.getMaxStock()
                ))
                .limit(10)
                .toList());
        anomalyData.put("lowStockProducts", lowStock.stream()
                .map(s -> Map.of(
                        "product", s.getProduct().getName(),
                        "quantity", s.getQuantity(),
                        "minStock", s.getMinStock()
                ))
                .limit(10)
                .toList());

        String prompt = buildAnomalyDetectionPrompt(anomalyData);
        String aiResponse = chatClient.prompt().user(prompt).call().content();

        return parseAnomalies(aiResponse);
    }

    /**
     * Berechnet einen Gesundheitsscore für das Inventar.
     */
    public InventoryHealthScore calculateHealthScore() {
        List<WarehouseStock> allStock = stockRepository.findAll();
        List<WarehouseStock> lowStock = stockRepository.findLowStockItems();
        List<WarehouseStock> overStock = stockRepository.findOverStockItems();

        if (allStock.isEmpty()) {
            return InventoryHealthScore.builder()
                    .overallScore(0)
                    .grade("N/A")
                    .stockAvailabilityScore(0)
                    .turnoverScore(0)
                    .accuracyScore(0)
                    .strengths(List.of())
                    .weaknesses(List.of("Keine Bestandsdaten vorhanden"))
                    .opportunities(List.of("Lagerbestand aufbauen"))
                    .build();
        }

        // Berechne Teilscores
        int totalItems = allStock.size();
        int lowStockItems = lowStock.size();
        int overStockItems = overStock.size();
        int healthyItems = totalItems - lowStockItems - overStockItems;

        int availabilityScore = (int) ((double) healthyItems / totalItems * 100);
        
        // Turnover Score basierend auf Bewegungen
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        List<Object[]> movements = movementRepository.getMovementStatistics(startDate);
        int totalMovements = movements.stream()
                .mapToInt(m -> ((Number) m[1]).intValue())
                .sum();
        int turnoverScore = Math.min(100, totalMovements * 2); // Mehr Bewegungen = besser

        // Accuracy Score (simuliert - in Produktion würde man echte Inventurdaten nutzen)
        int accuracyScore = 85 + new Random().nextInt(10);

        int overallScore = (availabilityScore + turnoverScore + accuracyScore) / 3;
        String grade = calculateGrade(overallScore);

        // KI für Insights nutzen
        String prompt = buildHealthScoreInsightsPrompt(availabilityScore, turnoverScore, accuracyScore, lowStockItems, overStockItems);
        String aiResponse = chatClient.prompt().user(prompt).call().content();
        
        return parseHealthScoreInsights(aiResponse, overallScore, grade, availabilityScore, turnoverScore, accuracyScore);
    }

    // === Private Helper Methods ===

    private Map<Long, Double> calculateProductSentiments() {
        List<ProductReview> recentReviews = reviewRepository
                .findByCreatedAtBetween(
                        LocalDateTime.now().minusMonths(3),
                        LocalDateTime.now()
                );

        return recentReviews.stream()
                .filter(r -> r.getProduct() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getProduct().getId(),
                        Collectors.averagingInt(r -> r.getRating() - 3) // -2 bis +2 Skala
                ));
    }

    private Map<Long, Double> calculateSalesVelocity() {
        List<Object[]> salesData = orderItemRepository.getProductSalesData();
        return salesData.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[5]).doubleValue() / 90.0, // Durchschnitt pro Tag
                        (a, b) -> a + b
                ));
    }

    private String buildDemandForecastPrompt(List<Map<String, Object>> productData, int forecastDays) {
        try {
            String dataJson = objectMapper.writeValueAsString(productData.stream().limit(20).toList());
            return """
                Du bist ein Experte für Bestandsmanagement und Nachfrageprognosen.
                Analysiere die folgenden Produktdaten und erstelle Prognosen für die nächsten %d Tage.
                
                Berücksichtige:
                - Aktuelle Verkaufsgeschwindigkeit (averageDailySales)
                - Review-Sentiment (positiv = höhere Nachfrage erwartet)
                - Kategorie-typische Muster
                
                Produktdaten:
                %s
                
                Antworte ausschließlich mit einem JSON-Array. Für jedes Produkt:
                {
                  "productId": <number>,
                  "predictedDemand7Days": <number>,
                  "predictedDemand14Days": <number>,
                  "predictedDemand30Days": <number>,
                  "confidenceScore": <0.0-1.0>,
                  "trendDirection": "RISING" | "STABLE" | "FALLING",
                  "aiInsight": "<kurze Erklärung>",
                  "seasonalityFactor": "HIGH" | "MEDIUM" | "LOW" | "NONE"
                }
                
                Antworte NUR mit dem JSON-Array, keine Erklärungen.
                """.formatted(forecastDays, dataJson);
        } catch (Exception e) {
            log.error("Error building forecast prompt", e);
            return "";
        }
    }

    private String buildReorderPrompt(List<Map<String, Object>> stockData) {
        try {
            // Nur Produkte mit Handlungsbedarf
            List<Map<String, Object>> relevantData = stockData.stream()
                    .filter(d -> (Boolean) d.get("isLowStock") || (Integer) d.get("daysUntilStockout") < 14)
                    .limit(15)
                    .toList();

            if (relevantData.isEmpty()) {
                relevantData = stockData.stream().limit(10).toList();
            }

            String dataJson = objectMapper.writeValueAsString(relevantData);
            return """
                Du bist ein Einkaufsberater für Lagerbestände.
                Analysiere die folgenden Bestandsdaten und erstelle Nachbestellungs-Empfehlungen.
                
                Berücksichtige:
                - Aktuelle Bestände vs. Mindestbestände
                - Verkaufsgeschwindigkeit (dailySalesVelocity)
                - Tage bis zum Stockout
                - Review-Sentiment (negative Reviews = vorsichtigere Bestellung)
                
                Bestandsdaten:
                %s
                
                Antworte ausschließlich mit einem JSON-Array. Für jedes Produkt mit Handlungsbedarf:
                {
                  "productId": <number>,
                  "recommendedOrderQuantity": <number>,
                  "urgency": "CRITICAL" | "HIGH" | "MEDIUM" | "LOW",
                  "reason": "<kurze Begründung>",
                  "suggestedOrderDate": "<YYYY-MM-DD>",
                  "reviewSentimentImpact": <-1.0 bis 1.0>
                }
                
                Sortiere nach Dringlichkeit. Antworte NUR mit dem JSON-Array.
                """.formatted(dataJson);
        } catch (Exception e) {
            log.error("Error building reorder prompt", e);
            return "";
        }
    }

    private String buildAnomalyDetectionPrompt(Map<String, Object> anomalyData) {
        try {
            String dataJson = objectMapper.writeValueAsString(anomalyData);
            return """
                Du bist ein Analyst für Lageranomalien.
                Analysiere die folgenden Bewegungs- und Bestandsdaten auf ungewöhnliche Muster.
                
                Daten:
                %s
                
                Identifiziere Anomalien wie:
                - Ungewöhnliche Bewegungsmuster
                - Bestandsdiskrepanzen
                - Plötzliche Nachfragespitzen oder -einbrüche
                - Produkte mit zu hohem oder zu niedrigem Bestand
                
                Antworte ausschließlich mit einem JSON-Array (max 5 Anomalien):
                {
                  "productName": "<name oder 'Allgemein'>",
                  "warehouseName": "<name oder 'Alle'>",
                  "anomalyType": "UNUSUAL_MOVEMENT" | "STOCK_DISCREPANCY" | "DEMAND_SPIKE" | "SUDDEN_DROP",
                  "severity": "HIGH" | "MEDIUM" | "LOW",
                  "description": "<was wurde erkannt>",
                  "suggestedAction": "<empfohlene Maßnahme>"
                }
                
                Antworte NUR mit dem JSON-Array.
                """.formatted(dataJson);
        } catch (Exception e) {
            log.error("Error building anomaly prompt", e);
            return "";
        }
    }

    private String buildHealthScoreInsightsPrompt(int availability, int turnover, int accuracy, int lowStock, int overStock) {
        return """
            Du bist ein Lagerberater. Basierend auf diesen Metriken, erstelle kurze Insights:
            
            - Verfügbarkeits-Score: %d%%
            - Umschlag-Score: %d%%
            - Genauigkeits-Score: %d%%
            - Artikel mit niedrigem Bestand: %d
            - Artikel mit Überbestand: %d
            
            Antworte mit JSON:
            {
              "strengths": ["<Stärke 1>", "<Stärke 2>"],
              "weaknesses": ["<Schwäche 1>", "<Schwäche 2>"],
              "opportunities": ["<Chance 1>", "<Chance 2>"]
            }
            
            Antworte auf DEUTSCH und NUR mit dem JSON.
            """.formatted(availability, turnover, accuracy, lowStock, overStock);
    }

    private List<DemandForecast> parseDemandForecasts(String aiResponse, List<Map<String, Object>> productData) {
        try {
            String cleanJson = cleanJsonResponse(aiResponse);
            List<Map<String, Object>> forecasts = objectMapper.readValue(cleanJson, new TypeReference<>() {});

            Map<Long, Map<String, Object>> productDataMap = productData.stream()
                    .collect(Collectors.toMap(
                            d -> ((Number) d.get("productId")).longValue(),
                            d -> d
                    ));

            return forecasts.stream().map(f -> {
                Long productId = ((Number) f.get("productId")).longValue();
                Map<String, Object> original = productDataMap.get(productId);

                return DemandForecast.builder()
                        .productId(productId)
                        .productName(original != null ? (String) original.get("productName") : "Unknown")
                        .category(original != null ? (String) original.get("category") : "Unknown")
                        .currentStock(original != null ? (Integer) original.get("currentStock") : 0)
                        .predictedDemand7Days(((Number) f.get("predictedDemand7Days")).intValue())
                        .predictedDemand14Days(((Number) f.get("predictedDemand14Days")).intValue())
                        .predictedDemand30Days(((Number) f.get("predictedDemand30Days")).intValue())
                        .confidenceScore(((Number) f.get("confidenceScore")).doubleValue())
                        .trendDirection((String) f.get("trendDirection"))
                        .aiInsight((String) f.get("aiInsight"))
                        .seasonalityFactor((String) f.get("seasonalityFactor"))
                        .stockoutRiskDate(calculateStockoutDate(original, f))
                        .build();
            }).toList();
        } catch (Exception e) {
            log.error("Error parsing demand forecasts: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ReorderRecommendation> parseReorderRecommendations(String aiResponse, List<Map<String, Object>> stockData) {
        try {
            String cleanJson = cleanJsonResponse(aiResponse);
            List<Map<String, Object>> recommendations = objectMapper.readValue(cleanJson, new TypeReference<>() {});

            Map<Long, Map<String, Object>> stockDataMap = stockData.stream()
                    .collect(Collectors.toMap(
                            d -> ((Number) d.get("productId")).longValue(),
                            d -> d
                    ));

            return recommendations.stream().map(r -> {
                Long productId = ((Number) r.get("productId")).longValue();
                Map<String, Object> original = stockDataMap.get(productId);
                BigDecimal unitPrice = original != null ? (BigDecimal) original.get("unitPrice") : BigDecimal.ZERO;
                int quantity = ((Number) r.get("recommendedOrderQuantity")).intValue();

                return ReorderRecommendation.builder()
                        .productId(productId)
                        .productName(original != null ? (String) original.get("productName") : "Unknown")
                        .category(original != null ? (String) original.get("category") : "Unknown")
                        .currentStock(original != null ? (Integer) original.get("currentStock") : 0)
                        .minStock(original != null ? (Integer) original.get("minStock") : 0)
                        .recommendedOrderQuantity(quantity)
                        .urgency((String) r.get("urgency"))
                        .reason((String) r.get("reason"))
                        .estimatedCost(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                        .suggestedOrderDate(LocalDate.parse((String) r.get("suggestedOrderDate")))
                        .daysUntilStockout(original != null ? (Integer) original.get("daysUntilStockout") : 0)
                        .reviewSentimentImpact(((Number) r.get("reviewSentimentImpact")).doubleValue())
                        .build();
            }).toList();
        } catch (Exception e) {
            log.error("Error parsing reorder recommendations: {}", e.getMessage());
            return List.of();
        }
    }

    private List<InventoryAnomaly> parseAnomalies(String aiResponse) {
        try {
            String cleanJson = cleanJsonResponse(aiResponse);
            List<Map<String, Object>> anomalies = objectMapper.readValue(cleanJson, new TypeReference<>() {});

            return anomalies.stream().map(a -> InventoryAnomaly.builder()
                    .productName((String) a.get("productName"))
                    .warehouseName((String) a.get("warehouseName"))
                    .anomalyType((String) a.get("anomalyType"))
                    .severity((String) a.get("severity"))
                    .description((String) a.get("description"))
                    .suggestedAction((String) a.get("suggestedAction"))
                    .detectedAt(LocalDate.now())
                    .build()
            ).toList();
        } catch (Exception e) {
            log.error("Error parsing anomalies: {}", e.getMessage());
            return List.of();
        }
    }

    private InventoryHealthScore parseHealthScoreInsights(String aiResponse, int overall, String grade,
                                                          int availability, int turnover, int accuracy) {
        try {
            String cleanJson = cleanJsonResponse(aiResponse);
            JsonNode root = objectMapper.readTree(cleanJson);

            return InventoryHealthScore.builder()
                    .overallScore(overall)
                    .grade(grade)
                    .stockAvailabilityScore(availability)
                    .turnoverScore(turnover)
                    .accuracyScore(accuracy)
                    .strengths(jsonArrayToList(root.get("strengths")))
                    .weaknesses(jsonArrayToList(root.get("weaknesses")))
                    .opportunities(jsonArrayToList(root.get("opportunities")))
                    .build();
        } catch (Exception e) {
            log.error("Error parsing health score insights: {}", e.getMessage());
            return InventoryHealthScore.builder()
                    .overallScore(overall)
                    .grade(grade)
                    .stockAvailabilityScore(availability)
                    .turnoverScore(turnover)
                    .accuracyScore(accuracy)
                    .strengths(List.of())
                    .weaknesses(List.of())
                    .opportunities(List.of())
                    .build();
        }
    }

    private String generateExecutiveSummary(List<DemandForecast> forecasts, List<ReorderRecommendation> recommendations,
                                            List<InventoryAnomaly> anomalies, InventoryHealthScore healthScore) {
        int criticalItems = (int) recommendations.stream().filter(r -> "CRITICAL".equals(r.getUrgency())).count();
        int highSeverityAnomalies = (int) anomalies.stream().filter(a -> "HIGH".equals(a.getSeverity())).count();

        return String.format(
                "Lager-Gesundheit: %s (%d/100). %d Produkte benötigen dringende Nachbestellung. " +
                        "%d Anomalien mit hoher Priorität erkannt. %d Produkte mit steigender Nachfrage identifiziert.",
                healthScore.getGrade(),
                healthScore.getOverallScore(),
                criticalItems,
                highSeverityAnomalies,
                forecasts.stream().filter(f -> "RISING".equals(f.getTrendDirection())).count()
        );
    }

    private List<String> generateActionItems(List<ReorderRecommendation> recommendations, List<InventoryAnomaly> anomalies) {
        List<String> actions = new ArrayList<>();

        recommendations.stream()
                .filter(r -> "CRITICAL".equals(r.getUrgency()) || "HIGH".equals(r.getUrgency()))
                .limit(3)
                .forEach(r -> actions.add("🔴 " + r.getProductName() + " nachbestellen: " + r.getReason()));

        anomalies.stream()
                .filter(a -> "HIGH".equals(a.getSeverity()))
                .limit(2)
                .forEach(a -> actions.add("⚠️ " + a.getDescription() + " - " + a.getSuggestedAction()));

        return actions;
    }

    private LocalDate calculateStockoutDate(Map<String, Object> original, Map<String, Object> forecast) {
        if (original == null) return null;
        Integer currentStock = (Integer) original.get("currentStock");
        Double avgDailySales = (Double) original.get("averageDailySales");

        if (avgDailySales == null || avgDailySales <= 0 || currentStock == null) return null;

        int daysUntilStockout = (int) Math.floor(currentStock / avgDailySales);
        return LocalDate.now().plusDays(daysUntilStockout);
    }

    private String calculateGrade(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    private String cleanJsonResponse(String response) {
        return response.replaceAll("```json", "").replaceAll("```", "").trim();
    }

    private List<String> jsonArrayToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> list.add(n.asText()));
        }
        return list;
    }

    private List<Map<String, Object>> formatMovementStats(List<Object[]> stats) {
        return stats.stream().map(s -> Map.<String, Object>of(
                "type", s[0],
                "count", s[1],
                "totalQuantity", s[2]
        )).toList();
    }

    private List<Map<String, Object>> formatDailyTrends(List<Object[]> trends) {
        return trends.stream().limit(14).map(t -> Map.<String, Object>of(
                "date", t[0].toString(),
                "type", t[1],
                "quantity", t[2]
        )).toList();
    }
}
