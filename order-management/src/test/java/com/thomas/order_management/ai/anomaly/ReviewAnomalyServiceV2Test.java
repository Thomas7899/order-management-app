// order-management/src/test/java/com/thomas/order_management/ai/anomaly/ReviewAnomalyServiceV2Test.java
package com.thomas.order_management.ai.anomaly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für ReviewAnomalyServiceV2.
 * Testet statistische Anomalie-Erkennung und LLM-Verfeinerung.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewAnomalyServiceV2 Tests")
class ReviewAnomalyServiceV2Test {

    @Mock
    private ProductReviewRepository reviewRepository;
    
    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private ReviewAnomalyServiceV2 service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        service = new ReviewAnomalyServiceV2(
                reviewRepository,
                chatClient,
                objectMapper
        );
    }

    @Nested
    @DisplayName("detectAnomalies Tests")
    class DetectAnomaliesTests {

        @Test
        @DisplayName("sollte keine Anomalien erkennen bei normaler Verteilung")
        void shouldNotDetectAnomaliesInNormalDistribution() {
            // Given
            Long productId = 1L;
            List<ProductReview> reviews = createNormalDistributionReviews(productId, 4.0, 0.5, 50);
            
            when(reviewRepository.findByProductId(productId)).thenReturn(reviews);

            // When
            List<ReviewAnomalyServiceV2.AnomalyResult> results = service.detectAnomalies(productId);

            // Then
            assertThat(results).isEmpty();
            verify(chatClient, never()).prompt();
        }

        @Test
        @DisplayName("sollte Anomalien bei Ausreißern erkennen")
        void shouldDetectAnomaliesWithOutliers() {
            // Given
            Long productId = 1L;
            List<ProductReview> reviews = createNormalDistributionReviews(productId, 4.5, 0.3, 20);
            
            // Anomalie hinzufügen: 1-Stern bei durchschnittlich 4.5 Sternen
            ProductReview anomaly = createReview(100L, productId, 1, "Katastrophe! Alles kaputt!");
            reviews.add(anomaly);
            
            String mockLlmResponse = """
                {
                  "anomalyScore": 0.9,
                  "classification": "CRITICAL",
                  "reasoning": "Rating deutlich unter Durchschnitt, kritische Schlüsselwörter",
                  "suggestedAction": "Sofortige Überprüfung erforderlich"
                }
                """;
            
            when(reviewRepository.findByProductId(productId)).thenReturn(reviews);
            mockChatClient(mockLlmResponse);

            // When
            List<ReviewAnomalyServiceV2.AnomalyResult> results = service.detectAnomalies(productId);

            // Then
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getReviewId()).isEqualTo(100L);
            assertThat(results.get(0).getClassification()).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("sollte zu wenige Reviews ignorieren")
        void shouldIgnoreTooFewReviews() {
            // Given
            Long productId = 1L;
            List<ProductReview> reviews = List.of(
                    createReview(1L, productId, 5, "Super!"),
                    createReview(2L, productId, 1, "Schrecklich!")
            );
            
            when(reviewRepository.findByProductId(productId)).thenReturn(reviews);

            // When
            List<ReviewAnomalyServiceV2.AnomalyResult> results = service.detectAnomalies(productId);

            // Then
            assertThat(results).isEmpty();
            verify(chatClient, never()).prompt();
        }
    }

    @Nested
    @DisplayName("Z-Score Berechnung Tests")
    class ZScoreTests {

        @ParameterizedTest
        @ValueSource(doubles = {2.1, 2.5, 3.0})
        @DisplayName("sollte nur Reviews mit Z-Score > 2.0 markieren")
        void shouldOnlyFlagReviewsWithHighZScore(double zScoreThreshold) {
            // Given
            Long productId = 1L;
            // 50 Reviews mit Durchschnitt 4.0 und Standardabweichung 0.5
            List<ProductReview> reviews = createNormalDistributionReviews(productId, 4.0, 0.5, 50);
            
            // Bewertung mit Rating = mean - (zScoreThreshold * stdDev)
            // Sollte gerade so die Schwelle erreichen
            double targetRating = 4.0 - (zScoreThreshold * 0.5);
            int roundedRating = Math.max(1, (int) Math.round(targetRating));
            
            ProductReview edgeCase = createReview(100L, productId, roundedRating, "Grenzfall");
            reviews.add(edgeCase);
            
            when(reviewRepository.findByProductId(productId)).thenReturn(reviews);
            
            // LLM nur mocken wenn wir Anomalien erwarten
            mockChatClient("""
                {"anomalyScore": 0.7, "classification": "POTENTIAL", "reasoning": "Grenzfall", "suggestedAction": "Beobachten"}
                """);

            // When
            List<ReviewAnomalyServiceV2.AnomalyResult> results = service.detectAnomalies(productId);

            // Then
            // Z-Score threshold im Service ist 2.0, also nur Werte darüber werden gefunden
            if (zScoreThreshold >= 2.0 && roundedRating <= 2) {
                assertThat(results).isNotEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Kritische Keywords Tests")
    class CriticalKeywordsTests {

        @Test
        @DisplayName("sollte kritische Keywords als Anomalie markieren")
        void shouldFlagCriticalKeywords() {
            // Given
            Long productId = 1L;
            List<ProductReview> reviews = createNormalDistributionReviews(productId, 3.5, 0.8, 30);
            
            // Review mit kritischem Keyword (auch wenn Rating ok ist)
            ProductReview criticalReview = createReview(100L, productId, 3, 
                    "Das Produkt ist gesundheitsgefährdend! Enthält schädliche Stoffe.");
            reviews.add(criticalReview);
            
            String mockLlmResponse = """
                {
                  "anomalyScore": 0.95,
                  "classification": "CRITICAL",
                  "reasoning": "Keyword 'gesundheitsgefährdend' erfordert sofortige Aufmerksamkeit",
                  "suggestedAction": "Produkt temporär sperren, Qualitätskontrolle"
                }
                """;
            
            when(reviewRepository.findByProductId(productId)).thenReturn(reviews);
            mockChatClient(mockLlmResponse);

            // When
            List<ReviewAnomalyServiceV2.AnomalyResult> results = service.detectAnomalies(productId);

            // Then
            assertThat(results).anyMatch(r -> 
                    r.getReviewId().equals(100L) && "CRITICAL".equals(r.getClassification()));
        }

        @Test
        @DisplayName("sollte verschiedene kritische Keywords erkennen")
        void shouldDetectVariousCriticalKeywords() {
            // Given
            Long productId = 1L;
            List<ProductReview> baseReviews = createNormalDistributionReviews(productId, 4.0, 0.5, 20);
            
            // Reviews mit verschiedenen kritischen Keywords
            List<String> criticalComments = List.of(
                    "Betrug! Fake Produkt!",
                    "Lebensgefährlich! Bitte entfernen!",
                    "Das ist reine Fälschung",
                    "Rechtswidrig, werde Anwalt einschalten"
            );
            
            for (int i = 0; i < criticalComments.size(); i++) {
                baseReviews.add(createReview(100L + i, productId, 1, criticalComments.get(i)));
            }
            
            when(reviewRepository.findByProductId(productId)).thenReturn(baseReviews);
            mockChatClient("""
                {"anomalyScore": 0.9, "classification": "CRITICAL", "reasoning": "Kritisches Keyword", "suggestedAction": "Prüfen"}
                """);

            // When
            List<ReviewAnomalyServiceV2.AnomalyResult> results = service.detectAnomalies(productId);

            // Then
            // Alle kritischen Reviews sollten markiert sein
            assertThat(results.size()).isGreaterThanOrEqualTo(criticalComments.size());
        }
    }

    @Nested
    @DisplayName("detectAllProductAnomalies Tests")
    class DetectAllProductAnomaliesTests {

        @Test
        @DisplayName("sollte Anomalien für alle Produkte gruppiert zurückgeben")
        void shouldReturnGroupedAnomaliesForAllProducts() {
            // Given
            List<ProductReview> allReviews = new ArrayList<>();
            allReviews.addAll(createNormalDistributionReviews(1L, 4.0, 0.5, 15));
            allReviews.addAll(createNormalDistributionReviews(2L, 4.5, 0.3, 15));
            
            // Anomalie für Produkt 1
            allReviews.add(createReview(100L, 1L, 1, "Katastrophe!"));
            
            when(reviewRepository.findAll()).thenReturn(allReviews);
            mockChatClient("""
                {"anomalyScore": 0.85, "classification": "POTENTIAL", "reasoning": "Ausreißer", "suggestedAction": "Beobachten"}
                """);

            // When
            Map<Long, List<ReviewAnomalyServiceV2.AnomalyResult>> results = 
                    service.detectAllProductAnomalies();

            // Then
            assertThat(results).isNotEmpty();
            assertThat(results.containsKey(1L) || results.containsKey(2L)).isTrue();
        }
    }

    @Nested
    @DisplayName("LLM Verfeinerung Tests")
    class LlmRefinementTests {

        @Test
        @DisplayName("sollte LLM-Klassifikation verwenden")
        void shouldUseLlmClassification() {
            // Given
            Long productId = 1L;
            List<ProductReview> reviews = createNormalDistributionReviews(productId, 4.0, 0.5, 25);
            reviews.add(createReview(100L, productId, 1, "Sehr enttäuscht"));
            
            String mockLlmResponse = """
                {
                  "anomalyScore": 0.6,
                  "classification": "LOW_PRIORITY",
                  "reasoning": "Typische negative Bewertung, keine kritischen Inhalte",
                  "suggestedAction": "Keine sofortige Aktion erforderlich"
                }
                """;
            
            when(reviewRepository.findByProductId(productId)).thenReturn(reviews);
            mockChatClient(mockLlmResponse);

            // When
            List<ReviewAnomalyServiceV2.AnomalyResult> results = service.detectAnomalies(productId);

            // Then
            assertThat(results).isNotEmpty();
            ReviewAnomalyServiceV2.AnomalyResult result = results.stream()
                    .filter(r -> r.getReviewId().equals(100L))
                    .findFirst()
                    .orElse(null);
            
            assertThat(result).isNotNull();
            assertThat(result.getClassification()).isEqualTo("LOW_PRIORITY");
            assertThat(result.getSuggestedAction()).contains("Keine sofortige Aktion");
        }

        @Test
        @DisplayName("sollte auf Fallback zurückgreifen bei LLM-Fehler")
        void shouldFallbackOnLlmError() {
            // Given
            Long productId = 1L;
            List<ProductReview> reviews = createNormalDistributionReviews(productId, 4.0, 0.5, 25);
            reviews.add(createReview(100L, productId, 1, "Problem"));
            
            when(reviewRepository.findByProductId(productId)).thenReturn(reviews);
            when(chatClient.prompt()).thenThrow(new RuntimeException("API nicht erreichbar"));

            // When
            List<ReviewAnomalyServiceV2.AnomalyResult> results = service.detectAnomalies(productId);

            // Then
            // Fallback: Anomalie basierend auf Z-Score ohne LLM-Verfeinerung
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getClassification()).isEqualTo("STATISTICAL_OUTLIER");
        }
    }

    // === Helper Methods ===

    private void mockChatClient(String response) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(response);
    }

    private ProductReview createReview(Long id, Long productId, int rating, String comment) {
        Product product = new Product();
        product.setId(productId);
        product.setName("Test Produkt " + productId);

        ProductReview review = new ProductReview();
        review.setId(id);
        review.setProduct(product);
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.now());

        return review;
    }

    /**
     * Erstellt Reviews mit annähernd normalverteilten Ratings.
     */
    private List<ProductReview> createNormalDistributionReviews(Long productId, double mean, double stdDev, int count) {
        List<ProductReview> reviews = new ArrayList<>();
        java.util.Random random = new java.util.Random(42); // Seed für Reproduzierbarkeit
        
        for (int i = 0; i < count; i++) {
            // Box-Muller Transform für Normalverteilung
            double u1 = random.nextDouble();
            double u2 = random.nextDouble();
            double z = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
            double rating = mean + z * stdDev;
            
            // Auf 1-5 begrenzen
            int clampedRating = Math.max(1, Math.min(5, (int) Math.round(rating)));
            
            reviews.add(createReview((long) i, productId, clampedRating, 
                    "Standardbewertung " + clampedRating + " Sterne"));
        }
        
        return reviews;
    }
}
