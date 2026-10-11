// order-management/src/test/java/com/thomas/order_management/ai/anomaly/ReviewAnomalyServiceV2Test.java
package com.thomas.order_management.ai.anomaly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.AnomalyReportDTO;
import com.thomas.order_management.dto.ProductAnomalyDTO;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.ProductReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für ReviewAnomalyServiceV2.
 * Testet statistische Vorfilterung, Keyword-Erkennung, LLM-Analyse und Fallback.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewAnomalyServiceV2 Tests")
class ReviewAnomalyServiceV2Test {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 31);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductReviewRepository reviewRepository;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    private ReviewAnomalyServiceV2 service;

    private long nextReviewId = 1;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        service = new ReviewAnomalyServiceV2(productRepository, reviewRepository, chatClient, objectMapper);
    }

    @Nested
    @DisplayName("detectAnomalies Tests")
    class DetectAnomaliesTests {

        @Test
        @DisplayName("sollte leeren Report ohne LLM-Aufruf liefern, wenn keine Reviews vorliegen")
        void shouldReturnEmptyReportWithoutReviews() {
            when(productRepository.findAll()).thenReturn(List.of(product(1L)));
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

            AnomalyReportDTO report = service.detectAnomalies(START, END);

            assertThat(report.anomalies).isEmpty();
            assertThat(report.windowStart).isEqualTo(START);
            assertThat(report.windowEnd).isEqualTo(END);
            verify(chatClient, never()).prompt();
        }

        @Test
        @DisplayName("sollte bei unauffälligen Produkten keine Anomalien melden")
        void shouldNotDetectAnomaliesForInconspicuousProducts() {
            Product p1 = product(1L);
            Product p2 = product(2L);
            List<ProductReview> reviews = new ArrayList<>();
            reviews.addAll(reviews(p1, 4, 10, "Gutes Produkt"));
            reviews.addAll(reviews(p2, 5, 10, "Sehr zufrieden"));

            when(productRepository.findAll()).thenReturn(List.of(p1, p2));
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);

            AnomalyReportDTO report = service.detectAnomalies(START, END);

            assertThat(report.anomalies).isEmpty();
            verify(chatClient, never()).prompt();
        }

        @Test
        @DisplayName("sollte statistischen Ausreißer per LLM bewerten lassen")
        void shouldAnalyzeStatisticalOutlierWithLlm() {
            Product bad = product(1L);
            List<Product> products = new ArrayList<>(List.of(bad));
            List<ProductReview> reviews = new ArrayList<>(reviews(bad, 1, 5, "Enttäuschend"));
            for (long id = 2; id <= 4; id++) {
                Product good = product(id);
                products.add(good);
                reviews.addAll(reviews(good, 5, 10, "Top"));
            }

            when(productRepository.findAll()).thenReturn(products);
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);
            mockChatClient("""
                    ```json
                    [{"productId": 1, "productName": "Test Produkt 1",
                      "reason": "Durchgehend 1-Stern-Bewertungen",
                      "avgRating": 1.0, "reviewCount": 5, "negativeKeywords": []}]
                    ```
                    """);

            AnomalyReportDTO report = service.detectAnomalies(START, END);

            assertThat(report.anomalies)
                    .singleElement()
                    .satisfies(a -> {
                        assertThat(a.productId()).isEqualTo(1L);
                        assertThat(a.reason()).isEqualTo("Durchgehend 1-Stern-Bewertungen");
                    });
            verify(chatClient).prompt();
        }

        @Test
        @DisplayName("sollte kritische Keywords auch bei ordentlicher Bewertung als Kandidat erkennen")
        void shouldTreatCriticalKeywordsAsCandidate() {
            Product p1 = product(1L);
            Product p2 = product(2L);
            List<ProductReview> reviews = new ArrayList<>();
            reviews.addAll(reviews(p1, 4, 9, "Gutes Produkt"));
            reviews.add(review(p1, 4, "Funktioniert, aber das Kabel wurde heiß und es kam Rauch"));
            reviews.addAll(reviews(p2, 4, 10, "Gutes Produkt"));

            when(productRepository.findAll()).thenReturn(List.of(p1, p2));
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);
            mockChatClient("""
                    [{"productId": 1, "productName": "Test Produkt 1",
                      "reason": "Sicherheitsrelevanter Hinweis auf Rauchentwicklung",
                      "avgRating": 4.0, "reviewCount": 10, "negativeKeywords": ["rauch"]}]
                    """);

            AnomalyReportDTO report = service.detectAnomalies(START, END);

            assertThat(report.anomalies).extracting(ProductAnomalyDTO::productId).containsExactly(1L);
            assertThat(report.anomalies.get(0).negativeKeywords()).containsExactly("rauch");
        }

        @Test
        @DisplayName("sollte bei LLM-Fehler regelbasierte Anomalien liefern")
        void shouldFallBackToRuleBasedAnomaliesOnLlmError() {
            Product bad = product(1L);
            List<Product> products = new ArrayList<>(List.of(bad));
            List<ProductReview> reviews = new ArrayList<>(reviews(bad, 1, 5, "Sofort defekt"));
            for (long id = 2; id <= 4; id++) {
                Product good = product(id);
                products.add(good);
                reviews.addAll(reviews(good, 5, 10, "Top"));
            }

            when(productRepository.findAll()).thenReturn(products);
            when(reviewRepository.findByCreatedAtBetween(any(), any())).thenReturn(reviews);
            when(chatClient.prompt()).thenThrow(new RuntimeException("API nicht erreichbar"));

            AnomalyReportDTO report = service.detectAnomalies(START, END);

            assertThat(report.anomalies)
                    .singleElement()
                    .satisfies(a -> {
                        assertThat(a.productId()).isEqualTo(1L);
                        assertThat(a.reason()).startsWith("Niedrige Bewertung");
                        assertThat(a.reviewCount()).isEqualTo(5);
                        assertThat(a.negativeKeywords()).containsExactly("defekt");
                    });
        }
    }

    @Nested
    @DisplayName("analyzeProduct Tests")
    class AnalyzeProductTests {

        @Test
        @DisplayName("sollte leer bleiben für unbekanntes Produkt")
        void shouldReturnEmptyForUnknownProduct() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThat(service.analyzeProduct(99L, START, END)).isEmpty();
            verifyNoInteractions(reviewRepository, chatClient);
        }

        @Test
        @DisplayName("sollte gut bewertetes Produkt ohne kritische Keywords nicht analysieren")
        void shouldSkipWellRatedProductWithoutKeywords() {
            Product p = product(1L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(p));
            when(reviewRepository.findByProductIdAndCreatedAtBetween(eq(1L), any(), any()))
                    .thenReturn(reviews(p, 5, 5, "Super"));

            assertThat(service.analyzeProduct(1L, START, END)).isEmpty();
            verify(chatClient, never()).prompt();
        }

        @Test
        @DisplayName("sollte schlecht bewertetes Produkt per LLM analysieren")
        void shouldAnalyzePoorlyRatedProduct() {
            Product p = product(1L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(p));
            when(reviewRepository.findByProductIdAndCreatedAtBetween(eq(1L), any(), any()))
                    .thenReturn(reviews(p, 2, 4, "Nicht gut"));
            mockChatClient("""
                    [{"productId": 1, "productName": "Test Produkt 1", "reason": "Schwache Bewertungen",
                      "avgRating": 2.0, "reviewCount": 4, "negativeKeywords": []}]
                    """);

            Optional<ProductAnomalyDTO> result = service.analyzeProduct(1L, START, END);

            assertThat(result).isPresent();
            assertThat(result.get().reason()).isEqualTo("Schwache Bewertungen");
        }
    }

    // === Helper Methods ===

    private void mockChatClient(String response) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(response);
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Test Produkt " + id);
        return product;
    }

    private List<ProductReview> reviews(Product product, int rating, int count, String comment) {
        List<ProductReview> reviews = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            reviews.add(review(product, rating, comment));
        }
        return reviews;
    }

    private ProductReview review(Product product, int rating, String comment) {
        ProductReview review = new ProductReview();
        review.setId(nextReviewId++);
        review.setProduct(product);
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.of(2026, 1, 15, 12, 0));
        return review;
    }
}
