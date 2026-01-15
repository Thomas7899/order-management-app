// order-management/src/test/java/com/thomas/order_management/ai/analysis/ReviewTrendAnalysisServiceV2Test.java
package com.thomas.order_management.ai.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.model.ReviewTrendReport;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewTrendReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Unit-Tests für ReviewTrendAnalysisServiceV2.
 * Testet Trend-Analyse mit gemockten LLM-Calls.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewTrendAnalysisServiceV2 Tests")
class ReviewTrendAnalysisServiceV2Test {

    @Mock
    private ProductReviewRepository reviewRepository;
    
    @Mock
    private ReviewTrendReportRepository reportRepository;
    
    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private ReviewTrendAnalysisServiceV2 service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        service = new ReviewTrendAnalysisServiceV2(
                reviewRepository,
                reportRepository,
                chatClient,
                objectMapper
        );
    }

    @Nested
    @DisplayName("analyze Tests")
    class AnalyzeTests {

        @Test
        @DisplayName("sollte Trend-Report für Reviews erstellen")
        void shouldCreateTrendReport() {
            // Given
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 1, 31);
            
            List<ProductReview> reviews = List.of(
                    createReview(1L, 5, "Super Qualität!"),
                    createReview(2L, 4, "Gutes Produkt"),
                    createReview(3L, 2, "Lieferung war langsam"),
                    createReview(4L, 1, "Defektes Produkt erhalten")
            );
            
            String mockLlmResponse = """
                {
                  "summary": "Gemischte Bewertungen: Produktqualität wird gelobt, Lieferung kritisiert.",
                  "positive_trends": ["Produktqualität", "Preis-Leistung"],
                  "negative_trends": ["Lieferzeit", "Defekte Ware"],
                  "neutral_observations": ["Verpackung akzeptabel"]
                }
                """;
            
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);
            mockChatClient(mockLlmResponse);
            when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            ReviewTrendReport result = service.analyze(start, end);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getWindowStart()).isEqualTo(start);
            assertThat(result.getWindowEnd()).isEqualTo(end);
            assertThat(result.getSummary()).contains("Gemischte Bewertungen");
            assertThat(result.getPositiveTrends()).contains("Produktqualität");
            assertThat(result.getNegativeTrends()).contains("Lieferzeit");
        }

        @Test
        @DisplayName("sollte leeren Report erstellen wenn keine Reviews")
        void shouldCreateEmptyReportWhenNoReviews() {
            // Given
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 1, 31);
            
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

            // When
            ReviewTrendReport result = service.analyze(start, end);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSummary()).contains("Keine Bewertungen");
            assertThat(result.getPositiveTrends()).isEmpty();
            assertThat(result.getNegativeTrends()).isEmpty();
            
            verify(chatClient, never()).prompt();
        }

        @Test
        @DisplayName("sollte Fallback verwenden bei JSON-Parsing-Fehler")
        void shouldUseFallbackOnParsingError() {
            // Given
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 1, 31);
            
            List<ProductReview> reviews = List.of(createReview(1L, 5, "Test"));
            String invalidResponse = "Dies ist kein JSON";
            
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);
            mockChatClient(invalidResponse);
            when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            ReviewTrendReport result = service.analyze(start, end);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSummary()).contains("konnte nicht verarbeitet werden");
        }

        @Test
        @DisplayName("sollte JSON-Codeblöcke aus Response entfernen")
        void shouldCleanJsonCodeBlocks() {
            // Given
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 1, 31);
            
            List<ProductReview> reviews = List.of(createReview(1L, 5, "Test"));
            String responseWithCodeBlocks = """
                ```json
                {
                  "summary": "Test Summary",
                  "positive_trends": ["Trend1"],
                  "negative_trends": [],
                  "neutral_observations": []
                }
                ```
                """;
            
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);
            mockChatClient(responseWithCodeBlocks);
            when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            ReviewTrendReport result = service.analyze(start, end);

            // Then
            assertThat(result.getSummary()).isEqualTo("Test Summary");
        }
    }

    @Nested
    @DisplayName("Prompt-Struktur Tests")
    class PromptStructureTests {

        @Test
        @DisplayName("sollte aggregierte Daten im Prompt verwenden")
        void shouldUseAggregatedDataInPrompt() {
            // Given
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 1, 31);
            
            List<ProductReview> reviews = List.of(
                    createReview(1L, 5, "Positiv 1"),
                    createReview(2L, 5, "Positiv 2"),
                    createReview(3L, 1, "Negativ 1")
            );
            
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.user(promptCaptor.capture())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(responseSpec);
            when(responseSpec.content()).thenReturn("""
                {"summary":"", "positive_trends":[], "negative_trends":[], "neutral_observations":[]}
                """);
            when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.analyze(start, end);

            // Then
            String capturedPrompt = promptCaptor.getValue();
            assertThat(capturedPrompt)
                    .contains("Gesamtanzahl Bewertungen: 3")
                    .contains("5★:")
                    .contains("1★:");
        }
    }

    @Nested
    @DisplayName("listAll Tests")
    class ListAllTests {

        @Test
        @DisplayName("sollte alle Reports zurückgeben")
        void shouldReturnAllReports() {
            // Given
            List<ReviewTrendReport> reports = List.of(
                    ReviewTrendReport.builder().id(1L).summary("Report 1").build(),
                    ReviewTrendReport.builder().id(2L).summary("Report 2").build()
            );
            when(reportRepository.findAll()).thenReturn(reports);

            // When
            List<ReviewTrendReport> result = service.listAll();

            // Then
            assertThat(result).hasSize(2);
        }
    }

    // === Helper Methods ===

    private void mockChatClient(String response) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(response);
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
