// order-management/src/test/java/com/thomas/order_management/ai/analysis/ReviewSentimentServiceV2Test.java
package com.thomas.order_management.ai.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.ai.config.AiProperties;
import com.thomas.order_management.dto.ReviewSentimentDto.*;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für ReviewSentimentServiceV2.
 * Testet Sentiment-Analyse mit Hybrid-Ansatz (Regel + LLM).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewSentimentServiceV2 Tests")
class ReviewSentimentServiceV2Test {

    @Mock
    private ProductReviewRepository reviewRepository;
    
    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private AiProperties aiProperties;
    private ReviewSentimentServiceV2 service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        aiProperties = new AiProperties();
        
        service = new ReviewSentimentServiceV2(
                reviewRepository,
                chatClient,
                objectMapper,
                aiProperties
        );
    }

    @Nested
    @DisplayName("analyzeReview Tests")
    class AnalyzeReviewTests {

        @Test
        @DisplayName("sollte Sentiment für einzelne Review analysieren")
        void shouldAnalyzeSingleReview() {
            // Given
            ProductReview review = createReview(1L, 5, "Absolut fantastisch!");
            String mockLlmResponse = """
                {
                  "sentiment": "POSITIVE",
                  "sentimentScore": 0.9,
                  "primaryEmotion": "JOY",
                  "emotionIntensity": 0.85,
                  "detectedThemes": ["Qualität"],
                  "urgencyLevel": "LOW",
                  "requiresFollowUp": false
                }
                """;
            
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
            mockChatClient(mockLlmResponse);

            // When
            EnhancedSentiment result = service.analyzeReview(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getReviewId()).isEqualTo(1L);
            assertThat(result.getSentiment()).isEqualTo("POSITIVE");
            assertThat(result.getSentimentScore()).isEqualTo(0.9);
            assertThat(result.getPrimaryEmotion()).isEqualTo("JOY");
        }

        @Test
        @DisplayName("sollte Fallback verwenden bei LLM-Fehler")
        void shouldUseFallbackOnLlmError() {
            // Given
            ProductReview review = createReview(1L, 5, "Test");
            
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
            mockChatClient("invalid json");

            // When
            EnhancedSentiment result = service.analyzeReview(1L);

            // Then
            assertThat(result).isNotNull();
            // Fallback basiert auf regelbasiertem Score
            assertThat(result.getSentiment()).isEqualTo("POSITIVE"); // Rating 5 = POSITIVE
        }
    }

    @Nested
    @DisplayName("Regelbasiertes Scoring Tests")
    class RuleBasedScoringTests {

        @ParameterizedTest
        @CsvSource({
            "1, NEGATIVE, FRUSTRATION",
            "2, NEGATIVE, DISAPPOINTMENT",
            "3, NEUTRAL, SURPRISE",
            "4, POSITIVE, SATISFACTION",
            "5, POSITIVE, JOY"
        })
        @DisplayName("sollte korrektes regelbasiertes Sentiment für Rating erzeugen")
        void shouldGenerateCorrectRuleBasedSentiment(int rating, String expectedSentiment, String expectedEmotion) {
            // Given
            ProductReview review = createReview(1L, rating, "Test");
            
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
            // LLM wirft Fehler -> Fallback auf regelbasiertes Scoring
            when(chatClient.prompt()).thenThrow(new RuntimeException("API Error"));

            // When
            EnhancedSentiment result = service.analyzeReview(1L);

            // Then
            assertThat(result.getSentiment()).isEqualTo(expectedSentiment);
            assertThat(result.getPrimaryEmotion()).isEqualTo(expectedEmotion);
        }
    }

    @Nested
    @DisplayName("generateEnhancedReport Tests")
    class GenerateEnhancedReportTests {

        @Test
        @DisplayName("sollte vollständigen Report für Zeitraum erstellen")
        void shouldCreateFullReportForPeriod() {
            // Given
            SentimentAnalysisRequest request = new SentimentAnalysisRequest();
            request.setStartDate(LocalDate.of(2025, 1, 1));
            request.setEndDate(LocalDate.of(2025, 1, 31));
            
            List<ProductReview> reviews = List.of(
                    createReview(1L, 5, "Super!"),
                    createReview(2L, 4, "Gut"),
                    createReview(3L, 2, "Enttäuscht"),
                    createReview(4L, 1, "Defekt")
            );
            
            String sentimentResponse = """
                [
                  {"id": 1, "sentiment": "POSITIVE", "sentimentScore": 0.9, "primaryEmotion": "JOY", "emotionIntensity": 0.8, "detectedThemes": ["Qualität"], "urgencyLevel": "LOW", "requiresFollowUp": false},
                  {"id": 2, "sentiment": "POSITIVE", "sentimentScore": 0.5, "primaryEmotion": "SATISFACTION", "emotionIntensity": 0.6, "detectedThemes": ["Preis"], "urgencyLevel": "LOW", "requiresFollowUp": false},
                  {"id": 3, "sentiment": "NEGATIVE", "sentimentScore": -0.5, "primaryEmotion": "DISAPPOINTMENT", "emotionIntensity": 0.7, "detectedThemes": ["Qualität"], "urgencyLevel": "NORMAL", "requiresFollowUp": true},
                  {"id": 4, "sentiment": "NEGATIVE", "sentimentScore": -0.9, "primaryEmotion": "ANGER", "emotionIntensity": 0.9, "detectedThemes": ["Defekt"], "urgencyLevel": "URGENT", "requiresFollowUp": true}
                ]
                """;
            
            String categorizationResponse = """
                [
                  {"id": 1, "primaryCategory": "QUALITÄT", "secondaryCategories": [], "extractedKeywords": ["super"], "actionableInsight": null},
                  {"id": 2, "primaryCategory": "PREIS", "secondaryCategories": [], "extractedKeywords": ["gut"], "actionableInsight": null},
                  {"id": 3, "primaryCategory": "QUALITÄT", "secondaryCategories": [], "extractedKeywords": ["enttäuscht"], "actionableInsight": "Qualität prüfen"},
                  {"id": 4, "primaryCategory": "QUALITÄT", "secondaryCategories": [], "extractedKeywords": ["defekt"], "actionableInsight": "Defekte Charge untersuchen"}
                ]
                """;
            
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);
            
            // Mock für zwei aufeinanderfolgende Calls
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(responseSpec);
            when(responseSpec.content())
                    .thenReturn(sentimentResponse)
                    .thenReturn(categorizationResponse);

            // When
            EnhancedSentimentReport result = service.generateEnhancedReport(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getWindowStart()).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(result.getWindowEnd()).isEqualTo(LocalDate.of(2025, 1, 31));
            assertThat(result.getEmotionDistribution()).isNotEmpty();
            assertThat(result.getThemeDistribution()).isNotEmpty();
        }

        @Test
        @DisplayName("sollte leeren Report erstellen wenn keine Reviews")
        void shouldCreateEmptyReportWhenNoReviews() {
            // Given
            SentimentAnalysisRequest request = new SentimentAnalysisRequest();
            request.setStartDate(LocalDate.of(2025, 1, 1));
            request.setEndDate(LocalDate.of(2025, 1, 31));
            
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

            // When
            EnhancedSentimentReport result = service.generateEnhancedReport(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getExecutiveSummary()).contains("Keine Bewertungen");
            assertThat(result.getOverallSentimentScore()).isEqualTo(0.0);
            assertThat(result.getPerformanceTrend()).isEqualTo("KEINE DATEN");
            
            verify(chatClient, never()).prompt();
        }

        @Test
        @DisplayName("sollte produktspezifische Analyse durchführen")
        void shouldPerformProductSpecificAnalysis() {
            // Given
            SentimentAnalysisRequest request = new SentimentAnalysisRequest();
            request.setStartDate(LocalDate.of(2025, 1, 1));
            request.setEndDate(LocalDate.of(2025, 1, 31));
            request.setProductId(42L);
            
            when(reviewRepository.findByProductIdAndCreatedAtBetween(eq(42L), any(), any()))
                    .thenReturn(List.of());

            // When
            service.generateEnhancedReport(request);

            // Then
            verify(reviewRepository).findByProductIdAndCreatedAtBetween(eq(42L), any(), any());
            verify(reviewRepository, never()).findByCreatedAtBetween(any(), any());
        }
    }

    @Nested
    @DisplayName("ThemeCluster Generation Tests")
    class ThemeClusterTests {

        @Test
        @DisplayName("sollte Kategorien korrekt clustern")
        void shouldClusterCategoriesCorrectly() {
            // Given
            SentimentAnalysisRequest request = new SentimentAnalysisRequest();
            request.setStartDate(LocalDate.of(2025, 1, 1));
            request.setEndDate(LocalDate.of(2025, 1, 31));
            
            List<ProductReview> reviews = List.of(
                    createReview(1L, 5, "Super Qualität"),
                    createReview(2L, 4, "Gute Qualität"),
                    createReview(3L, 2, "Schlechte Qualität")
            );
            
            String categorizationResponse = """
                [
                  {"id": 1, "primaryCategory": "QUALITÄT", "secondaryCategories": [], "extractedKeywords": ["super"], "actionableInsight": null},
                  {"id": 2, "primaryCategory": "QUALITÄT", "secondaryCategories": [], "extractedKeywords": ["gut"], "actionableInsight": null},
                  {"id": 3, "primaryCategory": "QUALITÄT", "secondaryCategories": [], "extractedKeywords": ["schlecht"], "actionableInsight": null}
                ]
                """;
            
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);
            mockChatClientMultiple("""
                [{"id":1,"sentiment":"POSITIVE","sentimentScore":0.9,"primaryEmotion":"JOY","emotionIntensity":0.8,"detectedThemes":[],"urgencyLevel":"LOW","requiresFollowUp":false},
                 {"id":2,"sentiment":"POSITIVE","sentimentScore":0.5,"primaryEmotion":"SATISFACTION","emotionIntensity":0.6,"detectedThemes":[],"urgencyLevel":"LOW","requiresFollowUp":false},
                 {"id":3,"sentiment":"NEGATIVE","sentimentScore":-0.5,"primaryEmotion":"DISAPPOINTMENT","emotionIntensity":0.7,"detectedThemes":[],"urgencyLevel":"NORMAL","requiresFollowUp":true}]
                """, categorizationResponse);

            // When
            EnhancedSentimentReport result = service.generateEnhancedReport(request);

            // Then
            assertThat(result.getThemeClusters()).isNotEmpty();
            ThemeCluster qualityCluster = result.getThemeClusters().stream()
                    .filter(c -> "QUALITÄT".equals(c.getThemeName()))
                    .findFirst()
                    .orElse(null);
            
            assertThat(qualityCluster).isNotNull();
            assertThat(qualityCluster.getReviewCount()).isEqualTo(3);
        }
    }

    // === Helper Methods ===

    private void mockChatClient(String response) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(response);
    }

    private void mockChatClientMultiple(String... responses) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        
        if (responses.length == 1) {
            when(responseSpec.content()).thenReturn(responses[0]);
        } else {
            when(responseSpec.content()).thenReturn(responses[0], 
                    java.util.Arrays.copyOfRange(responses, 1, responses.length));
        }
    }

    private ProductReview createReview(Long id, int rating, String comment) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Produkt");

        ProductReview review = new ProductReview();
        review.setId(id);
        review.setProduct(product);
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.now());

        return review;
    }
}
