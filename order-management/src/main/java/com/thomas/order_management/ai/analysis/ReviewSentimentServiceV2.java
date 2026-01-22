// order-management/src/main/java/com/thomas/order_management/ai/analysis/ReviewSentimentServiceV2.java
package com.thomas.order_management.ai.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.ai.config.AiProperties;
import com.thomas.order_management.dto.ReviewSentimentDto.*;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Überarbeiteter Sentiment-Analyse-Service mit optimierten Prompts.
 * 
 * <h2>Verbesserungen gegenüber V1:</h2>
 * <ul>
 *   <li>Hybrid-Ansatz: Regelbasiert + LLM</li>
 *   <li>Batch-Processing mit ObjectMapper statt String-Concatenation</li>
 *   <li>Deterministische Prompts</li>
 *   <li>Retry-Logik und Fallbacks</li>
 *   <li>Caching-freundliche Struktur</li>
 * </ul>
 * 
 * <h2>Hybrid-Sentiment-Strategie:</h2>
 * <ol>
 *   <li>Regelbasiertes Vor-Scoring aus Rating (schnell, kostenlos)</li>
 *   <li>LLM-Verfeinerung für Emotion und Themen (genau, kostenpflichtig)</li>
 *   <li>Aggregation und Konsistenzprüfung</li>
 * </ol>
 * 
 * @author Thomas Osterlehner
 * @since 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewSentimentServiceV2 {

    private final ProductReviewRepository reviewRepository;
    private final ChatClient chatClient;
    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    // Batch-Größe für LLM-Anfragen
    private static final int BATCH_SIZE = 10;

    /**
     * Analysiert eine einzelne Review mit erweitertem Sentiment.
     * Verwendet Hybrid-Ansatz: Regelbasiert + LLM.
     */
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public EnhancedSentiment analyzeReview(Long reviewId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review nicht gefunden: " + reviewId));

        return analyzeReviewSentiment(review);
    }

    /**
     * Analysiert mehrere Reviews in Batches.
     */
    public List<EnhancedSentiment> analyzeReviews(List<Long> reviewIds) {
        List<ProductReview> reviews = reviewRepository.findAllById(reviewIds);
        return batchAnalyzeSentiments(reviews);
    }

    /**
     * Generiert einen vollständigen Sentiment-Report für einen Zeitraum.
     */
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 2000)
    )
    public EnhancedSentimentReport generateEnhancedReport(SentimentAnalysisRequest request) {
        log.info("Generating enhanced sentiment report for {} to {}", 
                request.getStartDate(), request.getEndDate());

        LocalDateTime start = request.getStartDate().atStartOfDay();
        LocalDateTime end = request.getEndDate().plusDays(1).atStartOfDay();

        // Reviews laden
        List<ProductReview> reviews = loadReviews(request, start, end);

        if (reviews.isEmpty()) {
            return createEmptyReport(request);
        }

        // Batch-Analyse
        List<EnhancedSentiment> sentiments = batchAnalyzeSentiments(reviews);
        List<CategorizedReview> categorizations = batchCategorizeReviews(reviews);

        // Aggregation
        Map<String, Integer> emotionDistribution = aggregateEmotions(sentiments);
        Map<String, Integer> themeDistribution = aggregateThemes(categorizations);
        List<ThemeCluster> clusters = generateThemeClusters(categorizations);
        List<SentimentTrend> trends = calculateSentimentTrends(reviews, sentiments);
        
        // Kritische Reviews identifizieren
        List<CategorizedReview> criticalReviews = categorizations.stream()
                .filter(c -> c.getRating() <= 2)
                .sorted(Comparator.comparingInt(CategorizedReview::getRating))
                .limit(10)
                .toList();

        // Insights generieren
        double overallScore = sentiments.stream()
                .mapToDouble(EnhancedSentiment::getSentimentScore)
                .average()
                .orElse(0.0);

        String summary = generateSummary(reviews.size(), sentiments, clusters);
        List<String> priorityActions = generatePriorityActions(criticalReviews, clusters);
        List<String> highlights = extractHighlights(sentiments, categorizations);
        List<String> improvements = extractImprovements(clusters);

        return EnhancedSentimentReport.builder()
                .windowStart(request.getStartDate())
                .windowEnd(request.getEndDate())
                .executiveSummary(summary)
                .overallSentimentScore(Math.round(overallScore * 100) / 100.0)
                .emotionDistribution(emotionDistribution)
                .themeDistribution(themeDistribution)
                .themeClusters(clusters)
                .sentimentTrends(trends)
                .criticalReviews(criticalReviews)
                .priorityActions(priorityActions)
                .positiveHighlights(highlights)
                .areasForImprovement(improvements)
                .sentimentChangeVsPreviousPeriod(0.0)
                .performanceTrend(overallScore > 0.3 ? "POSITIV" : overallScore < -0.3 ? "NEGATIV" : "STABIL")
                .build();
    }

    // === Private Analysis Methods ===

    private EnhancedSentiment analyzeReviewSentiment(ProductReview review) {
        // 1. Regelbasiertes Vor-Scoring
        RuleBasedScore ruleScore = calculateRuleBasedScore(review);
        
        // 2. LLM-Analyse für Details
        String prompt = buildSentimentPrompt(review, ruleScore);
        String aiResponse = chatClient.prompt().user(prompt).call().content();
        
        return parseSentimentResponse(aiResponse, review.getId(), ruleScore);
    }

    private List<EnhancedSentiment> batchAnalyzeSentiments(List<ProductReview> reviews) {
        if (reviews.isEmpty()) return List.of();

        List<EnhancedSentiment> results = new ArrayList<>();
        
        for (int i = 0; i < reviews.size(); i += BATCH_SIZE) {
            List<ProductReview> batch = reviews.subList(i, 
                    Math.min(i + BATCH_SIZE, reviews.size()));
            
            try {
                String prompt = buildBatchSentimentPrompt(batch);
                String aiResponse = chatClient.prompt().user(prompt).call().content();
                results.addAll(parseBatchSentimentResponse(aiResponse, batch));
            } catch (Exception e) {
                log.warn("Batch sentiment analysis failed, using fallback: {}", e.getMessage());
                // Fallback: Regelbasiertes Scoring
                results.addAll(batch.stream()
                        .map(this::createFallbackSentiment)
                        .toList());
            }
        }
        
        return results;
    }

    private List<CategorizedReview> batchCategorizeReviews(List<ProductReview> reviews) {
        if (reviews.isEmpty()) return List.of();

        List<CategorizedReview> results = new ArrayList<>();
        
        for (int i = 0; i < reviews.size(); i += BATCH_SIZE) {
            List<ProductReview> batch = reviews.subList(i, 
                    Math.min(i + BATCH_SIZE, reviews.size()));
            
            try {
                String prompt = buildCategorizationPrompt(batch);
                String aiResponse = chatClient.prompt().user(prompt).call().content();
                results.addAll(parseCategorizationResponse(aiResponse, batch));
            } catch (Exception e) {
                log.warn("Batch categorization failed, using fallback: {}", e.getMessage());
                results.addAll(batch.stream()
                        .map(this::createFallbackCategorization)
                        .toList());
            }
        }
        
        return results;
    }

    // === Prompt Builders ===

    private String buildSentimentPrompt(ProductReview review, RuleBasedScore ruleScore) {
        return """
            AUFGABE: Analysiere das Sentiment dieser Produktbewertung.
            
            KONTEXT:
            - Produkt: %s
            - Numerisches Rating: %d/5 Sterne
            - Regelbasierter Sentiment-Score: %.2f (von -1 bis +1)
            
            BEWERTUNGSTEXT:
            "%s"
            
            ANALYSIERE und antworte mit EXAKT diesem JSON-Schema:
            {
              "sentiment": "POSITIVE" | "NEUTRAL" | "NEGATIVE",
              "sentimentScore": <Zahl von -1.0 bis 1.0>,
              "primaryEmotion": "JOY" | "SATISFACTION" | "SURPRISE" | "FRUSTRATION" | "DISAPPOINTMENT" | "ANGER",
              "emotionIntensity": <Zahl von 0.0 bis 1.0>,
              "detectedThemes": ["<Thema1>", "<Thema2>"],
              "urgencyLevel": "URGENT" | "NORMAL" | "LOW",
              "requiresFollowUp": <true/false>
            }
            
            REGELN:
            - Berücksichtige den regelbasierten Score als Ausgangspunkt
            - Passe den Score an basierend auf Textanalyse
            - URGENT nur bei expliziten Beschwerden oder Sicherheitsbedenken
            """.formatted(
                review.getProduct() != null ? review.getProduct().getName() : "Unbekannt",
                review.getRating(),
                ruleScore.score(),
                sanitizeForPrompt(review.getComment())
        );
    }

    private String buildBatchSentimentPrompt(List<ProductReview> reviews) {
        try {
            List<Map<String, Object>> reviewData = reviews.stream()
                    .map(r -> Map.<String, Object>of(
                            "id", r.getId(),
                            "product", r.getProduct() != null ? r.getProduct().getName() : "Unbekannt",
                            "rating", r.getRating(),
                            "comment", sanitizeForPrompt(r.getComment())
                    ))
                    .toList();

            String dataJson = objectMapper.writeValueAsString(reviewData);

            return """
                AUFGABE: Analysiere das Sentiment dieser Produktbewertungen.
                
                BEWERTUNGEN:
                %s
                
                ANTWORTE mit einem JSON-Array. Für JEDE Bewertung:
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
                """.formatted(dataJson);
        } catch (Exception e) {
            log.error("Failed to build batch prompt", e);
            return "";
        }
    }

    private String buildCategorizationPrompt(List<ProductReview> reviews) {
        try {
            List<Map<String, Object>> reviewData = reviews.stream()
                    .map(r -> Map.<String, Object>of(
                            "id", r.getId(),
                            "product", r.getProduct() != null ? r.getProduct().getName() : "Unbekannt",
                            "rating", r.getRating(),
                            "comment", sanitizeForPrompt(r.getComment())
                    ))
                    .toList();

            String dataJson = objectMapper.writeValueAsString(reviewData);

            return """
                AUFGABE: Kategorisiere diese Produktbewertungen nach Themen.
                
                KATEGORIEN (wähle die passendste):
                - QUALITÄT: Produktqualität, Material, Verarbeitung
                - LIEFERUNG: Versand, Lieferzeit, Verpackung
                - PREIS: Preis-Leistung, Kosten
                - SERVICE: Kundenservice, Support
                - BENUTZERFREUNDLICHKEIT: Bedienung, Anleitung
                - DESIGN: Aussehen, Optik
                - FUNKTIONALITÄT: Features, Leistung
                - SONSTIGES: Andere Themen
                
                BEWERTUNGEN:
                %s
                
                ANTWORTE mit einem JSON-Array. Für JEDE Bewertung:
                {
                  "id": <review-id>,
                  "primaryCategory": "<KATEGORIE>",
                  "secondaryCategories": ["<KATEGORIE>"],
                  "extractedKeywords": ["<keyword1>", "<keyword2>"],
                  "actionableInsight": "<kurze Empfehlung oder null>"
                }
                """.formatted(dataJson);
        } catch (Exception e) {
            log.error("Failed to build categorization prompt", e);
            return "";
        }
    }

    // === Response Parsers ===

    private EnhancedSentiment parseSentimentResponse(String response, Long reviewId, RuleBasedScore ruleScore) {
        try {
            String cleanJson = cleanJsonResponse(response);
            JsonNode root = objectMapper.readTree(cleanJson);

            return EnhancedSentiment.builder()
                    .reviewId(reviewId)
                    .sentiment(getTextOrDefault(root, "sentiment", ruleScore.sentiment()))
                    .sentimentScore(getDoubleOrDefault(root, "sentimentScore", ruleScore.score()))
                    .primaryEmotion(getTextOrDefault(root, "primaryEmotion", ruleScore.emotion()))
                    .emotionIntensity(getDoubleOrDefault(root, "emotionIntensity", 0.5))
                    .detectedThemes(jsonArrayToList(root.get("detectedThemes")))
                    .urgencyLevel(getTextOrDefault(root, "urgencyLevel", "NORMAL"))
                    .requiresFollowUp(getBooleanOrDefault(root, "requiresFollowUp", false))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse sentiment response, using fallback: {}", e.getMessage());
            return createFallbackSentiment(reviewId, ruleScore);
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
                        .detectedThemes(toStringList(r.get("detectedThemes")))
                        .urgencyLevel((String) r.get("urgencyLevel"))
                        .requiresFollowUp((Boolean) r.get("requiresFollowUp"))
                        .build();
            }).toList();
        } catch (Exception e) {
            log.warn("Failed to parse batch response: {}", e.getMessage());
            return reviews.stream().map(this::createFallbackSentiment).toList();
        }
    }

    private List<CategorizedReview> parseCategorizationResponse(String response, List<ProductReview> reviews) {
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
                        .secondaryCategories(toStringList(r.get("secondaryCategories")))
                        .extractedKeywords(toStringList(r.get("extractedKeywords")))
                        .actionableInsight((String) r.get("actionableInsight"))
                        .build();
            }).toList();
        } catch (Exception e) {
            log.warn("Failed to parse categorization response: {}", e.getMessage());
            return reviews.stream().map(this::createFallbackCategorization).toList();
        }
    }

    // === Rule-Based Scoring ===

    private RuleBasedScore calculateRuleBasedScore(ProductReview review) {
        int rating = review.getRating();
        
        // Score von -1 bis +1 basierend auf Rating
        double score = (rating - 3) / 2.0;
        
        String sentiment = rating >= 4 ? "POSITIVE" : rating <= 2 ? "NEGATIVE" : "NEUTRAL";
        
        String emotion = switch (rating) {
            case 5 -> "JOY";
            case 4 -> "SATISFACTION";
            case 3 -> "SURPRISE";
            case 2 -> "DISAPPOINTMENT";
            case 1 -> "FRUSTRATION";
            default -> "SATISFACTION";
        };
        
        return new RuleBasedScore(score, sentiment, emotion);
    }

    private record RuleBasedScore(double score, String sentiment, String emotion) {}

    // === Fallback Methods ===

    private EnhancedSentiment createFallbackSentiment(ProductReview review) {
        RuleBasedScore ruleScore = calculateRuleBasedScore(review);
        return createFallbackSentiment(review.getId(), ruleScore);
    }

    private EnhancedSentiment createFallbackSentiment(Long reviewId, RuleBasedScore ruleScore) {
        return EnhancedSentiment.builder()
                .reviewId(reviewId)
                .sentiment(ruleScore.sentiment())
                .sentimentScore(ruleScore.score())
                .primaryEmotion(ruleScore.emotion())
                .emotionIntensity(0.5)
                .detectedThemes(List.of())
                .urgencyLevel("NORMAL")
                .requiresFollowUp(false)
                .build();
    }

    private CategorizedReview createFallbackCategorization(ProductReview review) {
        return CategorizedReview.builder()
                .reviewId(review.getId())
                .comment(review.getComment())
                .rating(review.getRating())
                .productName(review.getProduct() != null ? review.getProduct().getName() : "Unbekannt")
                .primaryCategory("SONSTIGES")
                .secondaryCategories(List.of())
                .extractedKeywords(List.of())
                .build();
    }

    // === Aggregation & Report Generation ===

    private List<ProductReview> loadReviews(SentimentAnalysisRequest request, 
            LocalDateTime start, LocalDateTime end) {
        if (request.getProductId() != null) {
            return reviewRepository.findByProductIdAndCreatedAtBetween(
                    request.getProductId(), start, end);
        } else if (request.getCategory() != null && !request.getCategory().isBlank()) {
            return reviewRepository.findByProductCategoryAndCreatedAtBetween(
                    request.getCategory(), start, end);
        } else {
            return reviewRepository.findByCreatedAtBetween(start, end);
        }
    }

    private Map<String, Integer> aggregateEmotions(List<EnhancedSentiment> sentiments) {
        return sentiments.stream()
                .collect(Collectors.groupingBy(
                        EnhancedSentiment::getPrimaryEmotion,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }

    private Map<String, Integer> aggregateThemes(List<CategorizedReview> categorizations) {
        return categorizations.stream()
                .collect(Collectors.groupingBy(
                        CategorizedReview::getPrimaryCategory,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }

    private List<ThemeCluster> generateThemeClusters(List<CategorizedReview> categorizedReviews) {
        Map<String, List<CategorizedReview>> byCategory = categorizedReviews.stream()
                .collect(Collectors.groupingBy(CategorizedReview::getPrimaryCategory));

        return byCategory.entrySet().stream()
                .map(entry -> {
                    String category = entry.getKey();
                    List<CategorizedReview> reviews = entry.getValue();

                    double avgRating = reviews.stream()
                            .mapToInt(CategorizedReview::getRating)
                            .average()
                            .orElse(0.0);

                    List<String> topKeywords = reviews.stream()
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
                            .overallSentiment(avgRating >= 4 ? "POSITIV" : avgRating >= 3 ? "NEUTRAL" : "NEGATIV")
                            .topKeywords(topKeywords)
                            .sampleReviewIds(reviews.stream().map(CategorizedReview::getReviewId).limit(5).toList())
                            .trendDirection("STABLE")
                            .businessImpact(calculateBusinessImpact(reviews.size(), avgRating))
                            .build();
                })
                .sorted(Comparator.comparingInt(ThemeCluster::getReviewCount).reversed())
                .toList();
    }

    private List<SentimentTrend> calculateSentimentTrends(List<ProductReview> reviews, 
            List<EnhancedSentiment> sentiments) {
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

                    String dominantEmotion = daySentiments.stream()
                            .collect(Collectors.groupingBy(EnhancedSentiment::getPrimaryEmotion, Collectors.counting()))
                            .entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("NEUTRAL");

                    return SentimentTrend.builder()
                            .date(date)
                            .averageSentiment(Math.round(avgSentiment * 100) / 100.0)
                            .positiveCount((int) daySentiments.stream()
                                    .filter(s -> "POSITIVE".equals(s.getSentiment())).count())
                            .neutralCount((int) daySentiments.stream()
                                    .filter(s -> "NEUTRAL".equals(s.getSentiment())).count())
                            .negativeCount((int) daySentiments.stream()
                                    .filter(s -> "NEGATIVE".equals(s.getSentiment())).count())
                            .dominantEmotion(dominantEmotion)
                            .emergingThemes(List.of())
                            .build();
                })
                .toList();
    }

    private String generateSummary(int totalReviews, List<EnhancedSentiment> sentiments, 
            List<ThemeCluster> clusters) {
        long positive = sentiments.stream().filter(s -> "POSITIVE".equals(s.getSentiment())).count();
        long negative = sentiments.stream().filter(s -> "NEGATIVE".equals(s.getSentiment())).count();
        String topTheme = clusters.isEmpty() ? "keine" : clusters.get(0).getThemeName();
        long followUps = sentiments.stream().filter(EnhancedSentiment::getRequiresFollowUp).count();

        return String.format(
                "Analyse von %d Bewertungen: %d%% positiv, %d%% negativ. " +
                        "Häufigstes Thema: %s. %d Bewertungen erfordern Aufmerksamkeit.",
                totalReviews,
                totalReviews > 0 ? (positive * 100 / totalReviews) : 0,
                totalReviews > 0 ? (negative * 100 / totalReviews) : 0,
                topTheme,
                followUps
        );
    }

    private List<String> generatePriorityActions(List<CategorizedReview> criticalReviews, 
            List<ThemeCluster> clusters) {
        List<String> actions = new ArrayList<>();

        clusters.stream()
                .filter(c -> c.getAverageRating() < 3.0 && c.getReviewCount() > 2)
                .limit(2)
                .forEach(c -> actions.add("⚠️ " + c.getThemeName() + " verbessern: " +
                        c.getReviewCount() + " negative Bewertungen"));

        criticalReviews.stream()
                .filter(r -> r.getActionableInsight() != null && !r.getActionableInsight().isEmpty())
                .limit(3)
                .forEach(r -> actions.add("🔴 " + r.getProductName() + ": " + r.getActionableInsight()));

        return actions;
    }

    private List<String> extractHighlights(List<EnhancedSentiment> sentiments, 
            List<CategorizedReview> categorizations) {
        List<String> highlights = new ArrayList<>();

        long joyCount = sentiments.stream().filter(s -> "JOY".equals(s.getPrimaryEmotion())).count();
        if (joyCount > 0) {
            highlights.add("✨ " + joyCount + " Kunden drücken Begeisterung aus");
        }

        categorizations.stream()
                .filter(c -> c.getRating() >= 4)
                .collect(Collectors.groupingBy(CategorizedReview::getPrimaryCategory, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> highlights.add("👍 " + e.getKey() + " wird besonders gelobt (" + e.getValue() + "x)"));

        return highlights;
    }

    private List<String> extractImprovements(List<ThemeCluster> clusters) {
        return clusters.stream()
                .filter(c -> c.getAverageRating() < 3.5)
                .limit(3)
                .map(c -> c.getThemeName() + " (Ø " + c.getAverageRating() + ")")
                .toList();
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

    // === Utility Methods ===

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

    private String sanitizeForPrompt(String text) {
        if (text == null) return "(leer)";
        return text.replace("\"", "'")
                .replace("\n", " ")
                .replace("\r", "")
                .trim();
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

    private double getDoubleOrDefault(JsonNode root, String field, double defaultValue) {
        JsonNode node = root.get(field);
        return (node != null && node.isNumber()) ? node.asDouble() : defaultValue;
    }

    private boolean getBooleanOrDefault(JsonNode root, String field, boolean defaultValue) {
        JsonNode node = root.get(field);
        return (node != null && node.isBoolean()) ? node.asBoolean() : defaultValue;
    }

    private List<String> jsonArrayToList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj instanceof List) {
            return ((List<Object>) obj).stream()
                    .map(Object::toString)
                    .toList();
        }
        return List.of();
    }
}
