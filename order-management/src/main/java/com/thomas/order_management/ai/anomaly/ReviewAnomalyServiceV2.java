// order-management/src/main/java/com/thomas/order_management/ai/anomaly/ReviewAnomalyServiceV2.java
package com.thomas.order_management.ai.anomaly;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.AnomalyReportDTO;
import com.thomas.order_management.dto.ProductAnomalyDTO;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Überarbeiteter Anomalie-Erkennungs-Service für Produktbewertungen.
 * 
 * <h2>Verbesserungen gegenüber V1:</h2>
 * <ul>
 *   <li>Statistisch fundierte Anomalie-Erkennung (Z-Score)</li>
 *   <li>Hybrid-Ansatz: Statistische Vorfilterung + LLM-Analyse</li>
 *   <li>Deterministische Prompts</li>
 *   <li>Bessere Fehlerbehandlung</li>
 * </ul>
 * 
 * <h2>Anomalie-Typen:</h2>
 * <ul>
 *   <li>RATING_ANOMALY: Signifikant niedrige Bewertung (Z-Score < -2)</li>
 *   <li>VOLUME_ANOMALY: Ungewöhnlich viele negative Reviews</li>
 *   <li>SENTIMENT_SHIFT: Plötzliche Verschlechterung</li>
 *   <li>KEYWORD_ALERT: Kritische Keywords (defekt, gefährlich, etc.)</li>
 * </ul>
 * 
 * @author Thomas Osterlehner
 * @since 2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewAnomalyServiceV2 {

    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final ChatClient chatClient;
    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;

    // Kritische Keywords für Alert-Erkennung
    private static final Set<String> CRITICAL_KEYWORDS = Set.of(
            "defekt", "kaputt", "gefährlich", "verletzt", "brennt", "explodiert",
            "betrug", "fake", "gestohlen", "schimmel", "vergiftet", "allergie",
            "rückruf", "warnung", "gefahr", "feuer", "rauch"
    );

    // Z-Score Schwellenwert für statistische Anomalien
    private static final double Z_SCORE_THRESHOLD = -1.5;

    /**
     * Erkennt Anomalien in Produktbewertungen für einen Zeitraum.
     * Verwendet einen zweistufigen Ansatz:
     * 1. Statistische Vorfilterung
     * 2. LLM-basierte Detailanalyse
     */
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public AnomalyReportDTO detectAnomalies(LocalDate start, LocalDate end) {
        log.info("Starting anomaly detection for period {} to {}", start, end);

        // 1. Daten laden
        List<Product> products = productRepository.findAll();
        List<ProductReview> reviews = reviewRepository.findByCreatedAtBetween(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        if (reviews.isEmpty()) {
            return createEmptyReport(start, end);
        }

        // 2. Reviews nach Produkt gruppieren
        Map<Long, List<ProductReview>> reviewsByProduct = reviews.stream()
                .filter(r -> r.getProduct() != null)
                .collect(Collectors.groupingBy(r -> r.getProduct().getId()));

        // 3. Produkt-Statistiken berechnen
        List<ProductStatistics> productStats = calculateProductStatistics(products, reviewsByProduct);

        // 4. Statistische Anomalien identifizieren
        List<ProductStatistics> statisticalAnomalies = identifyStatisticalAnomalies(productStats);

        // 5. Keyword-basierte Anomalien
        List<ProductStatistics> keywordAnomalies = identifyKeywordAnomalies(productStats, reviewsByProduct);

        // 6. Alle Kandidaten zusammenführen
        Set<Long> anomalyProductIds = new HashSet<>();
        statisticalAnomalies.forEach(s -> anomalyProductIds.add(s.productId()));
        keywordAnomalies.forEach(s -> anomalyProductIds.add(s.productId()));

        // 7. LLM-Analyse für Top-Kandidaten
        List<ProductStatistics> topCandidates = productStats.stream()
                .filter(s -> anomalyProductIds.contains(s.productId()))
                .sorted(Comparator.comparingDouble(ProductStatistics::zScore))
                .limit(10)
                .toList();

        List<ProductAnomalyDTO> anomalies;
        if (topCandidates.isEmpty()) {
            anomalies = List.of();
        } else {
            anomalies = analyzeCandidatesWithLlm(topCandidates, reviewsByProduct);
        }

        // 8. Report erstellen
        AnomalyReportDTO report = new AnomalyReportDTO();
        report.anomalies = anomalies;
        report.windowStart = start;
        report.windowEnd = end;

        log.info("Anomaly detection completed. Found {} anomalies", anomalies.size());
        return report;
    }

    /**
     * Erkennt Anomalien mit Fokus auf ein spezifisches Produkt.
     */
    public Optional<ProductAnomalyDTO> analyzeProduct(Long productId, LocalDate start, LocalDate end) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return Optional.empty();
        }

        List<ProductReview> reviews = reviewRepository.findByProductIdAndCreatedAtBetween(
                productId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        if (reviews.isEmpty()) {
            return Optional.empty();
        }

        ProductStatistics stats = calculateStats(product, reviews);
        
        // Prüfe ob Anomalie vorliegt
        if (stats.avgRating() > 3.0 && !hasKeywordAnomalies(reviews)) {
            return Optional.empty();
        }

        // LLM-Analyse
        List<ProductAnomalyDTO> anomalies = analyzeCandidatesWithLlm(
                List.of(stats), 
                Map.of(productId, reviews)
        );

        return anomalies.isEmpty() ? Optional.empty() : Optional.of(anomalies.get(0));
    }

    // === Private Methods ===

    private List<ProductStatistics> calculateProductStatistics(
            List<Product> products, 
            Map<Long, List<ProductReview>> reviewsByProduct) {
        
        // Globale Statistiken für Z-Score
        double globalMeanRating = reviewsByProduct.values().stream()
                .flatMap(List::stream)
                .mapToInt(ProductReview::getRating)
                .average()
                .orElse(3.0);

        double globalStdDev = calculateStandardDeviation(
                reviewsByProduct.values().stream()
                        .flatMap(List::stream)
                        .mapToDouble(ProductReview::getRating)
                        .boxed()
                        .toList(),
                globalMeanRating
        );

        return products.stream()
                .filter(p -> reviewsByProduct.containsKey(p.getId()))
                .map(product -> {
                    List<ProductReview> productReviews = reviewsByProduct.get(product.getId());
                    double avgRating = productReviews.stream()
                            .mapToInt(ProductReview::getRating)
                            .average()
                            .orElse(0.0);

                    double zScore = globalStdDev > 0 
                            ? (avgRating - globalMeanRating) / globalStdDev 
                            : 0.0;

                    int negativeCount = (int) productReviews.stream()
                            .filter(r -> r.getRating() <= 2)
                            .count();

                    List<String> negativeSnippets = productReviews.stream()
                            .filter(r -> r.getRating() <= 2 && r.getComment() != null)
                            .map(r -> truncate(r.getComment(), 100))
                            .limit(5)
                            .toList();

                    return new ProductStatistics(
                            product.getId(),
                            product.getName(),
                            avgRating,
                            productReviews.size(),
                            negativeCount,
                            zScore,
                            negativeSnippets
                    );
                })
                .filter(s -> s.reviewCount() > 0)
                .toList();
    }

    private ProductStatistics calculateStats(Product product, List<ProductReview> reviews) {
        double avgRating = reviews.stream()
                .mapToInt(ProductReview::getRating)
                .average()
                .orElse(0.0);

        int negativeCount = (int) reviews.stream()
                .filter(r -> r.getRating() <= 2)
                .count();

        List<String> negativeSnippets = reviews.stream()
                .filter(r -> r.getRating() <= 2 && r.getComment() != null)
                .map(r -> truncate(r.getComment(), 100))
                .limit(5)
                .toList();

        return new ProductStatistics(
                product.getId(),
                product.getName(),
                avgRating,
                reviews.size(),
                negativeCount,
                0.0, // Z-Score nicht relevant für Einzelanalyse
                negativeSnippets
        );
    }

    private List<ProductStatistics> identifyStatisticalAnomalies(List<ProductStatistics> stats) {
        return stats.stream()
                .filter(s -> s.zScore() < Z_SCORE_THRESHOLD)
                .filter(s -> s.reviewCount() >= 3) // Mindestanzahl für Relevanz
                .toList();
    }

    private List<ProductStatistics> identifyKeywordAnomalies(
            List<ProductStatistics> stats,
            Map<Long, List<ProductReview>> reviewsByProduct) {
        
        return stats.stream()
                .filter(s -> hasKeywordAnomalies(reviewsByProduct.get(s.productId())))
                .toList();
    }

    private boolean hasKeywordAnomalies(List<ProductReview> reviews) {
        if (reviews == null) return false;
        
        return reviews.stream()
                .map(ProductReview::getComment)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .anyMatch(comment -> CRITICAL_KEYWORDS.stream().anyMatch(comment::contains));
    }

    private List<ProductAnomalyDTO> analyzeCandidatesWithLlm(
            List<ProductStatistics> candidates,
            Map<Long, List<ProductReview>> reviewsByProduct) {
        
        if (candidates.isEmpty()) {
            return List.of();
        }

        try {
            String dataJson = objectMapper.writeValueAsString(candidates);
            
            String prompt = """
                AUFGABE: Analysiere diese Produkte auf Anomalien und priorisiere sie.
                
                PRODUKTDATEN:
                %s
                
                ANOMALIE-KRITERIEN:
                - Signifikant niedrige Bewertung (< 2.5) bei mehreren Reviews
                - Kritische Keywords in Kommentaren (defekt, gefährlich, etc.)
                - Hohe Anzahl negativer Reviews relativ zur Gesamtzahl
                
                ANALYSIERE die Top 3-5 problematischsten Produkte.
                
                ANTWORTE mit einem JSON-Array:
                [
                  {
                    "productId": <number>,
                    "productName": "<name>",
                    "reason": "<prägnante Begründung in einem Satz>",
                    "avgRating": <number>,
                    "reviewCount": <number>,
                    "negativeKeywords": ["<keyword1>", "<keyword2>"]
                  }
                ]
                
                REGELN:
                - Maximal 5 Anomalien
                - Nur echte Probleme, keine Spekulation
                - Negativ-Keywords aus den Snippets extrahieren
                """.formatted(dataJson);

            String aiResponse = chatClient.prompt().user(prompt).call().content();
            String cleanJson = cleanJsonResponse(aiResponse);
            
            return objectMapper.readValue(cleanJson, new TypeReference<List<ProductAnomalyDTO>>() {});
            
        } catch (Exception e) {
            log.error("LLM analysis failed: {}", e.getMessage());
            
            // Fallback: Regelbasierte Anomalien
            return candidates.stream()
                    .filter(s -> s.avgRating() < 2.5 || s.zScore() < -2)
                    .limit(5)
                    .map(s -> new ProductAnomalyDTO(
                            s.productId(),
                            s.productName(),
                            "Niedrige Bewertung (%.1f) bei %d Reviews".formatted(s.avgRating(), s.reviewCount()),
                            s.avgRating(),
                            s.reviewCount(),
                            extractKeywordsFromSnippets(s.negativeSnippets())
                    ))
                    .toList();
        }
    }

    private List<String> extractKeywordsFromSnippets(List<String> snippets) {
        return snippets.stream()
                .flatMap(s -> Arrays.stream(s.toLowerCase().split("\\s+")))
                .filter(CRITICAL_KEYWORDS::contains)
                .distinct()
                .limit(5)
                .toList();
    }

    private double calculateStandardDeviation(List<Double> values, double mean) {
        if (values.size() < 2) return 0.0;
        
        double sumSquaredDiff = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();
        
        return Math.sqrt(sumSquaredDiff / (values.size() - 1));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        String cleaned = text.replace("\n", " ").replace("\r", "").trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength - 3) + "..." : cleaned;
    }

    private String cleanJsonResponse(String response) {
        return response
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
    }

    private AnomalyReportDTO createEmptyReport(LocalDate start, LocalDate end) {
        AnomalyReportDTO report = new AnomalyReportDTO();
        report.anomalies = List.of();
        report.windowStart = start;
        report.windowEnd = end;
        return report;
    }

    // === Records ===

    private record ProductStatistics(
            Long productId,
            String productName,
            double avgRating,
            int reviewCount,
            int negativeCount,
            double zScore,
            List<String> negativeSnippets
    ) {}
}
