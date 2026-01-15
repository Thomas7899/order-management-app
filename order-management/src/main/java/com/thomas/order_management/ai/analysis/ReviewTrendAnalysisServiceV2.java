// order-management/src/main/java/com/thomas/order_management/ai/analysis/ReviewTrendAnalysisServiceV2.java
package com.thomas.order_management.ai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.model.ReviewTrendReport;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewTrendReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Überarbeiteter Review-Trend-Analyse-Service.
 * 
 * <h2>Verbesserungen gegenüber V1:</h2>
 * <ul>
 *   <li>Deterministische Prompts mit niedriger Temperature</li>
 *   <li>Strukturierte JSON-Ausgabe mit Schema-Validierung</li>
 *   <li>Deduplizierung und Aggregation vor Analyse</li>
 *   <li>Retry-Logik mit exponentiellem Backoff</li>
 *   <li>Bessere Fehlerbehandlung und Fallbacks</li>
 *   <li>Token-effiziente Prompts durch Aggregation</li>
 * </ul>
 * 
 * <h2>Prompt-Strategie:</h2>
 * <p>Verwendet aggregierte Statistiken statt Einzelreviews für:</p>
 * <ul>
 *   <li>Token-Effizienz (weniger Input-Tokens)</li>
 *   <li>Bessere Übersicht für das LLM</li>
 *   <li>Vermeidung von Halluzinationen durch klare Datenbasis</li>
 * </ul>
 * 
 * @author Thomas Osterlehner
 * @since 2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewTrendAnalysisServiceV2 {

    private final ProductReviewRepository productReviewRepository;
    private final ReviewTrendReportRepository trendReportRepository;
    private final ChatClient chatClient;
    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;

    /**
     * Analysiert Reviews im gegebenen Zeitraum und erstellt einen Trend-Report.
     * 
     * @param windowStart Startdatum (inklusiv)
     * @param windowEnd Enddatum (inklusiv)
     * @return Gespeicherter Trend-Report
     */
    @Transactional
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public ReviewTrendReport analyze(LocalDate windowStart, LocalDate windowEnd) {
        log.info("Starting trend analysis for period {} to {}", windowStart, windowEnd);

        // 1. Reviews laden
        List<ProductReview> reviews = fetchReviews(windowStart, windowEnd);
        
        if (reviews.isEmpty()) {
            log.info("No reviews found for analysis period");
            return createEmptyReport(windowStart, windowEnd);
        }

        // 2. Daten aggregieren für effiziente LLM-Nutzung
        ReviewAggregation aggregation = aggregateReviews(reviews);

        // 3. Optimierten Prompt bauen
        String prompt = buildAnalysisPrompt(aggregation, windowStart, windowEnd);

        // 4. LLM-Analyse durchführen
        String aiResponse = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

        // 5. Response parsen
        TrendAnalysisResult result = parseAnalysisResponse(aiResponse);

        // 6. Report erstellen und speichern
        ReviewTrendReport report = ReviewTrendReport.builder()
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .generatedAt(Instant.now())
                .summary(result.summary())
                .positiveTrends(result.positiveTrends())
                .negativeTrends(result.negativeTrends())
                .neutralObservations(result.neutralObservations())
                .build();

        ReviewTrendReport saved = trendReportRepository.save(report);
        log.info("Saved trend report with ID {}", saved.getId());
        
        return saved;
    }

    /**
     * Listet alle historischen Trend-Reports.
     */
    public List<ReviewTrendReport> listAll() {
        return trendReportRepository.findAll();
    }

    /**
     * Lädt den neuesten Trend-Report.
     */
    public ReviewTrendReport getLatest() {
        return trendReportRepository.findTopByOrderByGeneratedAtDesc()
                .orElse(null);
    }

    // === Private Helper Methods ===

    private List<ProductReview> fetchReviews(LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            return productReviewRepository.findByCreatedAtBetween(
                    start.atStartOfDay(), 
                    end.plusDays(1).atStartOfDay()
            );
        }
        return productReviewRepository.findAll();
    }

    /**
     * Aggregiert Reviews für effiziente LLM-Analyse.
     * Reduziert Token-Verbrauch durch Zusammenfassung.
     */
    private ReviewAggregation aggregateReviews(List<ProductReview> reviews) {
        int totalCount = reviews.size();
        
        // Rating-Verteilung
        Map<Integer, Long> ratingDistribution = reviews.stream()
                .collect(Collectors.groupingBy(
                        ProductReview::getRating,
                        Collectors.counting()
                ));
        
        double avgRating = reviews.stream()
                .mapToInt(ProductReview::getRating)
                .average()
                .orElse(0.0);

        // Top-Produkte nach Review-Anzahl
        Map<String, Long> productReviewCounts = reviews.stream()
                .filter(r -> r.getProduct() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getProduct().getName(),
                        Collectors.counting()
                ));

        List<String> topProducts = productReviewCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .toList();

        // Negative Reviews (1-2 Sterne) für spezifische Probleme
        List<String> negativeComments = reviews.stream()
                .filter(r -> r.getRating() <= 2 && r.getComment() != null)
                .map(r -> truncateComment(r.getComment()))
                .distinct()
                .limit(10)
                .toList();

        // Positive Reviews (4-5 Sterne) für Stärken
        List<String> positiveComments = reviews.stream()
                .filter(r -> r.getRating() >= 4 && r.getComment() != null)
                .map(r -> truncateComment(r.getComment()))
                .distinct()
                .limit(10)
                .toList();

        return new ReviewAggregation(
                totalCount,
                avgRating,
                ratingDistribution,
                topProducts,
                negativeComments,
                positiveComments
        );
    }

    /**
     * Baut einen optimierten, deterministischen Prompt.
     */
    private String buildAnalysisPrompt(ReviewAggregation agg, LocalDate start, LocalDate end) {
        return """
            AUFGABE: Analysiere Produktbewertungen und identifiziere Trends.
            
            ZEITRAUM: %s bis %s
            
            AGGREGIERTE DATEN:
            - Gesamtanzahl Bewertungen: %d
            - Durchschnittliche Bewertung: %.1f/5 Sterne
            - Bewertungsverteilung: %s
            - Meistbewertete Produkte: %s
            
            NEGATIVE BEWERTUNGEN (1-2 Sterne):
            %s
            
            POSITIVE BEWERTUNGEN (4-5 Sterne):
            %s
            
            ANALYSIERE und antworte mit EXAKT diesem JSON-Schema:
            {
              "summary": "Ein prägnanter Management-Absatz (max. 3 Sätze) mit den wichtigsten Erkenntnissen",
              "positive_trends": ["Trend 1", "Trend 2", "Trend 3"],
              "negative_trends": ["Problem 1", "Problem 2", "Problem 3"],
              "neutral_observations": ["Beobachtung 1", "Beobachtung 2"]
            }
            
            REGELN:
            - Maximal 5 Einträge pro Trend-Kategorie
            - Jeder Trend/Problem in 3-10 Worten
            - Basiere NUR auf den gegebenen Daten
            - Keine Spekulationen oder erfundenen Fakten
            """.formatted(
                start, end,
                agg.totalCount(),
                agg.avgRating(),
                formatRatingDistribution(agg.ratingDistribution()),
                String.join(", ", agg.topProducts()),
                formatCommentsList(agg.negativeComments()),
                formatCommentsList(agg.positiveComments())
        );
    }

    private TrendAnalysisResult parseAnalysisResponse(String aiResponse) {
        try {
            String cleanJson = cleanJsonResponse(aiResponse);
            JsonNode root = objectMapper.readTree(cleanJson);

            String summary = getTextOrDefault(root, "summary", "Keine Zusammenfassung verfügbar.");
            List<String> positiveTrends = jsonArrayToList(root.get("positive_trends"));
            List<String> negativeTrends = jsonArrayToList(root.get("negative_trends"));
            List<String> neutralObservations = jsonArrayToList(root.get("neutral_observations"));

            return new TrendAnalysisResult(summary, positiveTrends, negativeTrends, neutralObservations);
            
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
            return new TrendAnalysisResult(
                    "Analyse konnte nicht verarbeitet werden: " + truncateComment(aiResponse),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    private ReviewTrendReport createEmptyReport(LocalDate start, LocalDate end) {
        return ReviewTrendReport.builder()
                .windowStart(start)
                .windowEnd(end)
                .generatedAt(Instant.now())
                .summary("Keine Bewertungen im gewählten Zeitraum gefunden.")
                .positiveTrends(List.of())
                .negativeTrends(List.of())
                .neutralObservations(List.of())
                .build();
    }

    private String formatRatingDistribution(Map<Integer, Long> distribution) {
        StringBuilder sb = new StringBuilder();
        for (int i = 5; i >= 1; i--) {
            long count = distribution.getOrDefault(i, 0L);
            if (count > 0) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(i).append("★: ").append(count);
            }
        }
        return sb.toString();
    }

    private String formatCommentsList(List<String> comments) {
        if (comments.isEmpty()) return "(keine)";
        return comments.stream()
                .map(c -> "- " + c)
                .collect(Collectors.joining("\n"));
    }

    private String truncateComment(String comment) {
        if (comment == null) return "";
        String cleaned = comment.replace("\n", " ").replace("\r", "").trim();
        return cleaned.length() > 150 ? cleaned.substring(0, 147) + "..." : cleaned;
    }

    private String cleanJsonResponse(String response) {
        return response
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
    }

    private String getTextOrDefault(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.get(field);
        return (node != null && !node.isNull()) ? node.asText() : defaultValue;
    }

    private List<String> jsonArrayToList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    // === Records ===

    private record ReviewAggregation(
            int totalCount,
            double avgRating,
            Map<Integer, Long> ratingDistribution,
            List<String> topProducts,
            List<String> negativeComments,
            List<String> positiveComments
    ) {}

    private record TrendAnalysisResult(
            String summary,
            List<String> positiveTrends,
            List<String> negativeTrends,
            List<String> neutralObservations
    ) {}
}
