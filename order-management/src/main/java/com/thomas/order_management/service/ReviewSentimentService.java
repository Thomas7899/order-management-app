// order-management/src/main/java/com/thomas/order_management/service/ReviewSentimentService.java
package com.thomas.order_management.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.ReviewSentimentDto.*;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Erweiterter KI-Service für tiefgehende Sentiment-Analyse von Reviews.
 * Erkennt Emotionen, kategorisiert Themen und generiert actionable Insights.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewSentimentService {

    private final ProductReviewRepository reviewRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Analysiert eine einzelne Review mit erweitertem Sentiment.
     */
    public EnhancedSentiment analyzeReview(Long reviewId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review nicht gefunden: " + reviewId));

        return analyzeReviewSentiment(review);
    }

    /**
     * Analysiert mehrere Reviews und liefert erweiterte Sentiments.
     */
    public List<EnhancedSentiment> analyzeReviews(List<Long> reviewIds) {
        List<ProductReview> reviews = reviewRepository.findAllById(reviewIds);
        return reviews.stream()
                .map(this::analyzeReviewSentiment)
                .toList();
    }

    /**
     * Generiert einen vollständigen erweiterten Sentiment-Report.
     */
    public EnhancedSentimentReport generateEnhancedReport(SentimentAnalysisRequest request) {
        LocalDateTime start = request.getStartDate().atStartOfDay();
        LocalDateTime end = request.getEndDate().plusDays(1).atStartOfDay();

        List<ProductReview> reviews;
        if (request.getProductId() != null) {
            reviews = reviewRepository.findByProductIdAndCreatedAtBetween(
                    request.getProductId(), start, end);
        } else if (request.getCategory() != null && !request.getCategory().isBlank()) {
            reviews = reviewRepository.findByProductCategoryAndCreatedAtBetween(
                    request.getCategory(), start, end);
        } else {
            reviews = reviewRepository.findByCreatedAtBetween(start, end);
        }

        if (reviews.isEmpty()) {
            return createEmptyReport(request);
        }

        // Emotionsanalyse durchführen
        Map<String, Integer> emotionDistribution = new HashMap<>();
        Map<String, Integer> themeDistribution = new HashMap<>();
        List<CategorizedReview> categorizedReviews = new ArrayList<>();
        List<CategorizedReview> criticalReviews = new ArrayList<>();

        // Batch-Analyse für Effizienz
        List<EnhancedSentiment> sentiments = batchAnalyzeSentiments(reviews);
        List<CategorizedReview> categorizations = batchCategorizeReviews(reviews);

        for (EnhancedSentiment sentiment : sentiments) {
            emotionDistribution.merge(sentiment.getPrimaryEmotion(), 1, Integer::sum);
        }

        for (CategorizedReview cat : categorizations) {
            themeDistribution.merge(cat.getPrimaryCategory(), 1, Integer::sum);
            categorizedReviews.add(cat);

            // Kritische Reviews identifizieren
            if (cat.getRating() <= 2) {
                criticalReviews.add(cat);
            }
        }

        // Theme Clusters generieren
        List<ThemeCluster> clusters = generateThemeClusters(categorizedReviews);

        // Sentiment Trends berechnen
        List<SentimentTrend> trends = calculateSentimentTrends(reviews, sentiments);

        // Executive Summary und Actions generieren
        String summary = generateExecutiveSummary(reviews, sentiments, clusters);
        List<String> priorityActions = generatePriorityActions(criticalReviews, clusters);
        List<String> highlights = extractPositiveHighlights(sentiments, categorizations);
        List<String> improvements = extractAreasForImprovement(clusters, criticalReviews);

        // Overall Sentiment Score berechnen
        double overallScore = sentiments.stream()
                .mapToDouble(EnhancedSentiment::getSentimentScore)
                .average()
                .orElse(0.0);

        return EnhancedSentimentReport.builder()
                .windowStart(request.getStartDate())
                .windowEnd(request.getEndDate())
                .executiveSummary(summary)
                .overallSentimentScore(overallScore)
                .emotionDistribution(emotionDistribution)
                .themeDistribution(themeDistribution)
                .themeClusters(clusters)
                .sentimentTrends(trends)
                .criticalReviews(criticalReviews.stream().limit(10).toList())
                .priorityActions(priorityActions)
                .positiveHighlights(highlights)
                .areasForImprovement(improvements)
                .sentimentChangeVsPreviousPeriod(0.0) // TODO: Vergleich implementieren
                .performanceTrend(calculatePerformanceTrend(overallScore))
                .build();
    }

    /**
     * Kategorisiert Reviews automatisch nach Themen.
     */
    public List<CategorizedReview> categorizeReviews(LocalDate start, LocalDate end) {
        List<ProductReview> reviews = reviewRepository.findByCreatedAtBetween(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        return batchCategorizeReviews(reviews);
    }

    /**
     * Generiert Themen-Cluster für einen Zeitraum.
     */
    public List<ThemeCluster> getThemeClusters(LocalDate start, LocalDate end) {
        List<ProductReview> reviews = reviewRepository.findByCreatedAtBetween(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        List<CategorizedReview> categorized = batchCategorizeReviews(reviews);
        return generateThemeClusters(categorized);
    }

    // === Private Helper Methods ===

    private EnhancedSentiment analyzeReviewSentiment(ProductReview review) {
        String prompt = buildSentimentPrompt(review);
        String aiResponse = chatClient.prompt().user(prompt).call().content();
        return parseSentimentResponse(aiResponse, review.getId());
    }

    private List<EnhancedSentiment> batchAnalyzeSentiments(List<ProductReview> reviews) {
        if (reviews.isEmpty()) return List.of();

        // Batch in Gruppen von 10 für Effizienz
        List<EnhancedSentiment> results = new ArrayList<>();
        List<List<ProductReview>> batches = partition(reviews, 10);

        for (List<ProductReview> batch : batches) {
            String prompt = buildBatchSentimentPrompt(batch);
            String aiResponse = chatClient.prompt().user(prompt).call().content();
            results.addAll(parseBatchSentimentResponse(aiResponse, batch));
        }

        return results;
    }

    private List<CategorizedReview> batchCategorizeReviews(List<ProductReview> reviews) {
        if (reviews.isEmpty()) return List.of();

        List<CategorizedReview> results = new ArrayList<>();
        List<List<ProductReview>> batches = partition(reviews, 10);

        for (List<ProductReview> batch : batches) {
            String prompt = buildBatchCategorizationPrompt(batch);
            String aiResponse = chatClient.prompt().user(prompt).call().content();
            results.addAll(parseBatchCategorizationResponse(aiResponse, batch));
        }

        return results;
    }

    private String buildSentimentPrompt(ProductReview review) {
        return """
            Analysiere diese Produktbewertung und extrahiere erweiterte Sentiment-Informationen.
            
            Produkt: %s
            Rating: %d/5
            Kommentar: %s
            
            Antworte mit JSON:
            {
              "sentiment": "POSITIVE" | "NEUTRAL" | "NEGATIVE",
              "sentimentScore": <-1.0 bis 1.0>,
              "primaryEmotion": "JOY" | "SATISFACTION" | "SURPRISE" | "FRUSTRATION" | "DISAPPOINTMENT" | "ANGER",
              "emotionIntensity": <0.0 bis 1.0>,
              "detectedThemes": ["<Thema1>", "<Thema2>"],
              "urgencyLevel": "URGENT" | "NORMAL" | "LOW",
              "requiresFollowUp": <boolean>,
              "suggestedResponse": "<kurze Antwort-Empfehlung oder null>"
            }
            
            Antworte auf DEUTSCH und NUR mit dem JSON.
            """.formatted(
                review.getProduct() != null ? review.getProduct().getName() : "Unbekannt",
                review.getRating(),
                review.getComment()
        );
    }

    private String buildBatchSentimentPrompt(List<ProductReview> reviews) {
        StringBuilder reviewsJson = new StringBuilder("[");
        for (int i = 0; i < reviews.size(); i++) {
            ProductReview r = reviews.get(i);
            if (i > 0) reviewsJson.append(",");
            reviewsJson.append(String.format(
                    "{\"id\":%d,\"product\":\"%s\",\"rating\":%d,\"comment\":\"%s\"}",
                    r.getId(),
                    r.getProduct() != null ? escapeJson(r.getProduct().getName()) : "Unbekannt",
                    r.getRating(),
                    escapeJson(r.getComment())
            ));
        }
        reviewsJson.append("]");

        return """
            Analysiere diese Produktbewertungen und extrahiere Sentiment-Informationen.
            
            Bewertungen:
            %s
            
            Antworte mit einem JSON-Array. Für jede Bewertung:
            {
              "id": <review-id>,
              "sentiment": "POSITIVE" | "NEUTRAL" | "NEGATIVE",
              "sentimentScore": <-1.0 bis 1.0>,
              "primaryEmotion": "JOY" | "SATISFACTION" | "SURPRISE" | "FRUSTRATION" | "DISAPPOINTMENT" | "ANGER",
              "emotionIntensity": <0.0 bis 1.0>,
              "detectedThemes": ["<Thema>"],
              "urgencyLevel": "URGENT" | "NORMAL" | "LOW",
              "requiresFollowUp": <boolean>
            }
            
            Antworte NUR mit dem JSON-Array.
            """.formatted(reviewsJson.toString());
    }

    private String buildBatchCategorizationPrompt(List<ProductReview> reviews) {
        StringBuilder reviewsJson = new StringBuilder("[");
        for (int i = 0; i < reviews.size(); i++) {
            ProductReview r = reviews.get(i);
            if (i > 0) reviewsJson.append(",");
            reviewsJson.append(String.format(
                    "{\"id\":%d,\"product\":\"%s\",\"rating\":%d,\"comment\":\"%s\"}",
                    r.getId(),
                    r.getProduct() != null ? escapeJson(r.getProduct().getName()) : "Unbekannt",
                    r.getRating(),
                    escapeJson(r.getComment())
            ));
        }
        reviewsJson.append("]");

        return """
            Kategorisiere diese Produktbewertungen nach Themen.
            
            Mögliche Kategorien:
            - QUALITÄT: Produktqualität, Material, Verarbeitung
            - LIEFERUNG: Versand, Lieferzeit, Verpackung
            - PREIS: Preis-Leistung, Kosten
            - SERVICE: Kundenservice, Support
            - BENUTZERFREUNDLICHKEIT: Bedienung, Anleitung
            - DESIGN: Aussehen, Optik
            - FUNKTIONALITÄT: Features, Leistung
            - SONSTIGES: Andere Themen
            
            Bewertungen:
            %s
            
            Antworte mit einem JSON-Array. Für jede Bewertung:
            {
              "id": <review-id>,
              "primaryCategory": "<KATEGORIE>",
              "secondaryCategories": ["<KATEGORIE>"],
              "extractedKeywords": ["<keyword1>", "<keyword2>"],
              "actionableInsight": "<kurze Handlungsempfehlung oder null>"
            }
            
            Antworte NUR mit dem JSON-Array.
            """.formatted(reviewsJson.toString());
    }

    private EnhancedSentiment parseSentimentResponse(String response, Long reviewId) {
        try {
            String cleanJson = cleanJsonResponse(response);
            JsonNode root = objectMapper.readTree(cleanJson);

            return EnhancedSentiment.builder()
                    .reviewId(reviewId)
                    .sentiment(root.get("sentiment").asText())
                    .sentimentScore(root.get("sentimentScore").asDouble())
                    .primaryEmotion(root.get("primaryEmotion").asText())
                    .emotionIntensity(root.get("emotionIntensity").asDouble())
                    .detectedThemes(jsonArrayToList(root.get("detectedThemes")))
                    .urgencyLevel(root.get("urgencyLevel").asText())
                    .requiresFollowUp(root.get("requiresFollowUp").asBoolean())
                    .suggestedResponse(root.has("suggestedResponse") && !root.get("suggestedResponse").isNull()
                            ? root.get("suggestedResponse").asText() : null)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing sentiment response: {}", e.getMessage());
            return EnhancedSentiment.builder()
                    .reviewId(reviewId)
                    .sentiment("NEUTRAL")
                    .sentimentScore(0.0)
                    .primaryEmotion("SATISFACTION")
                    .emotionIntensity(0.5)
                    .detectedThemes(List.of())
                    .urgencyLevel("NORMAL")
                    .requiresFollowUp(false)
                    .build();
        }
    }

    private List<EnhancedSentiment> parseBatchSentimentResponse(String response, List<ProductReview> reviews) {
        try {
            String cleanJson = cleanJsonResponse(response);
            List<Map<String, Object>> results = objectMapper.readValue(cleanJson, new TypeReference<>() {});

            Map<Long, ProductReview> reviewMap = reviews.stream()
                    .collect(Collectors.toMap(ProductReview::getId, r -> r));

            return results.stream().map(r -> {
                Long id = ((Number) r.get("id")).longValue();
                return EnhancedSentiment.builder()
                        .reviewId(id)
                        .sentiment((String) r.get("sentiment"))
                        .sentimentScore(((Number) r.get("sentimentScore")).doubleValue())
                        .primaryEmotion((String) r.get("primaryEmotion"))
                        .emotionIntensity(((Number) r.get("emotionIntensity")).doubleValue())
                        .detectedThemes((List<String>) r.get("detectedThemes"))
                        .urgencyLevel((String) r.get("urgencyLevel"))
                        .requiresFollowUp((Boolean) r.get("requiresFollowUp"))
                        .build();
            }).toList();
        } catch (Exception e) {
            log.error("Error parsing batch sentiment response: {}", e.getMessage());
            return reviews.stream().map(r -> EnhancedSentiment.builder()
                    .reviewId(r.getId())
                    .sentiment("NEUTRAL")
                    .sentimentScore(0.0)
                    .primaryEmotion("SATISFACTION")
                    .emotionIntensity(0.5)
                    .detectedThemes(List.of())
                    .urgencyLevel("NORMAL")
                    .requiresFollowUp(false)
                    .build()
            ).toList();
        }
    }

    private List<CategorizedReview> parseBatchCategorizationResponse(String response, List<ProductReview> reviews) {
        try {
            String cleanJson = cleanJsonResponse(response);
            List<Map<String, Object>> results = objectMapper.readValue(cleanJson, new TypeReference<>() {});

            Map<Long, ProductReview> reviewMap = reviews.stream()
                    .collect(Collectors.toMap(ProductReview::getId, r -> r));

            return results.stream().map(r -> {
                Long id = ((Number) r.get("id")).longValue();
                ProductReview review = reviewMap.get(id);

                return CategorizedReview.builder()
                        .reviewId(id)
                        .comment(review != null ? review.getComment() : "")
                        .rating(review != null ? review.getRating() : 0)
                        .productName(review != null && review.getProduct() != null
                                ? review.getProduct().getName() : "Unbekannt")
                        .primaryCategory((String) r.get("primaryCategory"))
                        .secondaryCategories((List<String>) r.get("secondaryCategories"))
                        .extractedKeywords((List<String>) r.get("extractedKeywords"))
                        .actionableInsight((String) r.get("actionableInsight"))
                        .build();
            }).toList();
        } catch (Exception e) {
            log.error("Error parsing batch categorization response: {}", e.getMessage());
            return reviews.stream().map(r -> CategorizedReview.builder()
                    .reviewId(r.getId())
                    .comment(r.getComment())
                    .rating(r.getRating())
                    .productName(r.getProduct() != null ? r.getProduct().getName() : "Unbekannt")
                    .primaryCategory("SONSTIGES")
                    .secondaryCategories(List.of())
                    .extractedKeywords(List.of())
                    .build()
            ).toList();
        }
    }

    private List<ThemeCluster> generateThemeClusters(List<CategorizedReview> categorizedReviews) {
        Map<String, List<CategorizedReview>> byCategory = categorizedReviews.stream()
                .collect(Collectors.groupingBy(CategorizedReview::getPrimaryCategory));

        return byCategory.entrySet().stream().map(entry -> {
            String category = entry.getKey();
            List<CategorizedReview> reviews = entry.getValue();

            double avgRating = reviews.stream()
                    .mapToInt(CategorizedReview::getRating)
                    .average()
                    .orElse(0.0);

            String sentiment = avgRating >= 4 ? "POSITIV" : avgRating >= 3 ? "NEUTRAL" : "NEGATIV";

            List<String> allKeywords = reviews.stream()
                    .flatMap(r -> r.getExtractedKeywords().stream())
                    .collect(Collectors.groupingBy(k -> k, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .toList();

            return ThemeCluster.builder()
                    .themeName(category)
                    .themeDescription(getCategoryDescription(category))
                    .reviewCount(reviews.size())
                    .averageRating(Math.round(avgRating * 10) / 10.0)
                    .overallSentiment(sentiment)
                    .topKeywords(allKeywords)
                    .sampleReviewIds(reviews.stream().map(CategorizedReview::getReviewId).limit(5).toList())
                    .trendDirection("STABLE") // TODO: Berechnung implementieren
                    .businessImpact(calculateBusinessImpact(reviews.size(), avgRating))
                    .build();
        }).sorted(Comparator.comparingInt(ThemeCluster::getReviewCount).reversed())
                .toList();
    }

    private List<SentimentTrend> calculateSentimentTrends(List<ProductReview> reviews, List<EnhancedSentiment> sentiments) {
        Map<Long, EnhancedSentiment> sentimentMap = sentiments.stream()
                .collect(Collectors.toMap(EnhancedSentiment::getReviewId, s -> s));

        Map<LocalDate, List<ProductReview>> byDate = reviews.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate()));

        return byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<ProductReview> dayReviews = entry.getValue();

                    List<EnhancedSentiment> daySentiments = dayReviews.stream()
                            .map(r -> sentimentMap.get(r.getId()))
                            .filter(Objects::nonNull)
                            .toList();

                    double avgSentiment = daySentiments.stream()
                            .mapToDouble(EnhancedSentiment::getSentimentScore)
                            .average()
                            .orElse(0.0);

                    Map<String, Long> emotionCounts = daySentiments.stream()
                            .collect(Collectors.groupingBy(EnhancedSentiment::getPrimaryEmotion, Collectors.counting()));

                    String dominantEmotion = emotionCounts.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("NEUTRAL");

                    return SentimentTrend.builder()
                            .date(date)
                            .averageSentiment(Math.round(avgSentiment * 100) / 100.0)
                            .positiveCount((int) daySentiments.stream().filter(s -> "POSITIVE".equals(s.getSentiment())).count())
                            .neutralCount((int) daySentiments.stream().filter(s -> "NEUTRAL".equals(s.getSentiment())).count())
                            .negativeCount((int) daySentiments.stream().filter(s -> "NEGATIVE".equals(s.getSentiment())).count())
                            .dominantEmotion(dominantEmotion)
                            .emergingThemes(List.of())
                            .build();
                }).toList();
    }

    private String generateExecutiveSummary(List<ProductReview> reviews, List<EnhancedSentiment> sentiments,
                                            List<ThemeCluster> clusters) {
        int total = reviews.size();
        long positive = sentiments.stream().filter(s -> "POSITIVE".equals(s.getSentiment())).count();
        long negative = sentiments.stream().filter(s -> "NEGATIVE".equals(s.getSentiment())).count();

        String topTheme = clusters.isEmpty() ? "keine" : clusters.get(0).getThemeName();

        return String.format(
                "Analyse von %d Bewertungen: %d%% positiv, %d%% negativ. " +
                        "Häufigstes Thema: %s. %d Bewertungen erfordern Aufmerksamkeit.",
                total,
                total > 0 ? (positive * 100 / total) : 0,
                total > 0 ? (negative * 100 / total) : 0,
                topTheme,
                sentiments.stream().filter(EnhancedSentiment::getRequiresFollowUp).count()
        );
    }

    private List<String> generatePriorityActions(List<CategorizedReview> criticalReviews, List<ThemeCluster> clusters) {
        List<String> actions = new ArrayList<>();

        // Kritische Themen
        clusters.stream()
                .filter(c -> c.getAverageRating() < 3.0 && c.getReviewCount() > 2)
                .limit(2)
                .forEach(c -> actions.add("⚠️ " + c.getThemeName() + " verbessern: " +
                        c.getReviewCount() + " negative Bewertungen"));

        // Kritische Reviews
        criticalReviews.stream()
                .filter(r -> r.getActionableInsight() != null)
                .limit(3)
                .forEach(r -> actions.add("🔴 " + r.getProductName() + ": " + r.getActionableInsight()));

        return actions;
    }

    private List<String> extractPositiveHighlights(List<EnhancedSentiment> sentiments,
                                                   List<CategorizedReview> categorizations) {
        List<String> highlights = new ArrayList<>();

        long joyCount = sentiments.stream().filter(s -> "JOY".equals(s.getPrimaryEmotion())).count();
        if (joyCount > 0) {
            highlights.add("✨ " + joyCount + " Kunden drücken Begeisterung aus");
        }

        Map<String, Long> positiveByCategory = categorizations.stream()
                .filter(c -> c.getRating() >= 4)
                .collect(Collectors.groupingBy(CategorizedReview::getPrimaryCategory, Collectors.counting()));

        positiveByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> highlights.add("👍 " + e.getKey() + " wird besonders gelobt (" + e.getValue() + "x)"));

        return highlights;
    }

    private List<String> extractAreasForImprovement(List<ThemeCluster> clusters, List<CategorizedReview> criticalReviews) {
        List<String> improvements = new ArrayList<>();

        clusters.stream()
                .filter(c -> c.getAverageRating() < 3.5)
                .limit(3)
                .forEach(c -> improvements.add(c.getThemeName() + " (Ø " + c.getAverageRating() + ")"));

        return improvements;
    }

    private EnhancedSentimentReport createEmptyReport(SentimentAnalysisRequest request) {
        return EnhancedSentimentReport.builder()
                .windowStart(request.getStartDate())
                .windowEnd(request.getEndDate())
                .executiveSummary("Keine Bewertungen im gewählten Zeitraum gefunden.")
                .overallSentimentScore(0.0)
                .emotionDistribution(Map.of())
                .themeDistribution(Map.of())
                .themeClusters(List.of())
                .sentimentTrends(List.of())
                .criticalReviews(List.of())
                .priorityActions(List.of())
                .positiveHighlights(List.of())
                .areasForImprovement(List.of())
                .sentimentChangeVsPreviousPeriod(0.0)
                .performanceTrend("KEINE DATEN")
                .build();
    }

    private String getCategoryDescription(String category) {
        return switch (category) {
            case "QUALITÄT" -> "Bewertungen zur Produktqualität und Verarbeitung";
            case "LIEFERUNG" -> "Feedback zu Versand und Lieferung";
            case "PREIS" -> "Kommentare zum Preis-Leistungs-Verhältnis";
            case "SERVICE" -> "Erfahrungen mit dem Kundenservice";
            case "BENUTZERFREUNDLICHKEIT" -> "Feedback zur Bedienung";
            case "DESIGN" -> "Kommentare zum Aussehen";
            case "FUNKTIONALITÄT" -> "Bewertungen der Features";
            default -> "Sonstige Themen";
        };
    }

    private String calculateBusinessImpact(int reviewCount, double avgRating) {
        if (reviewCount > 10 && avgRating < 2.5) return "HIGH";
        if (reviewCount > 5 && avgRating < 3.0) return "MEDIUM";
        return "LOW";
    }

    private String calculatePerformanceTrend(double score) {
        if (score > 0.3) return "POSITIV";
        if (score < -0.3) return "NEGATIV";
        return "STABIL";
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

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "")
                .replace("\t", " ");
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
