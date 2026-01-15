// order-management/src/test/java/com/thomas/order_management/ai/embedding/ReviewEmbeddingServiceV2Test.java
package com.thomas.order_management.ai.embedding;

import com.thomas.order_management.ai.config.AiProperties;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewEmbeddingRepositoryV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für ReviewEmbeddingServiceV2.
 * Testet Embedding-Erstellung, Similarity-Suche und Batch-Verarbeitung.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewEmbeddingServiceV2 Tests")
class ReviewEmbeddingServiceV2Test {

    @Mock
    private ProductReviewRepository reviewRepository;
    
    @Mock
    private ReviewEmbeddingRepositoryV2 embeddingRepository;
    
    @Mock
    private OpenAiEmbeddingModel embeddingModel;
    
    @Mock
    private EmbeddingTextBuilder textBuilder;

    private AiProperties aiProperties;
    private ReviewEmbeddingServiceV2 service;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEmbedding(new AiProperties.EmbeddingConfig());
        
        service = new ReviewEmbeddingServiceV2(
                reviewRepository,
                embeddingRepository,
                embeddingModel,
                textBuilder,
                aiProperties
        );
    }

    @Nested
    @DisplayName("createOrUpdateEmbedding Tests")
    class CreateOrUpdateEmbeddingTests {

        @Test
        @DisplayName("sollte neues Embedding erstellen für neue Review")
        void shouldCreateEmbeddingForNewReview() {
            // Given
            ProductReview review = createTestReview(1L, "Tolles Produkt!", 5);
            String embeddingText = "[SENTIMENT: SEHR_POSITIV] Tolles Produkt!";
            float[] mockEmbedding = createMockEmbedding(1536);
            
            when(textBuilder.buildEmbeddingText(any(), anyInt())).thenReturn(embeddingText);
            when(embeddingRepository.existsWithHash(anyLong(), anyString())).thenReturn(false);
            when(embeddingModel.embed(embeddingText)).thenReturn(mockEmbedding);

            // When
            ProductReview result = service.createOrUpdateEmbedding(review);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            
            verify(embeddingRepository).upsertEmbedding(
                    eq(1L),
                    anyList(),
                    eq(embeddingText),
                    anyString()
            );
        }

        @Test
        @DisplayName("sollte Embedding überspringen wenn Hash identisch")
        void shouldSkipEmbeddingWhenHashMatches() {
            // Given
            ProductReview review = createTestReview(1L, "Tolles Produkt!", 5);
            String embeddingText = "[SENTIMENT: SEHR_POSITIV] Tolles Produkt!";
            
            when(textBuilder.buildEmbeddingText(any(), anyInt())).thenReturn(embeddingText);
            when(embeddingRepository.existsWithHash(anyLong(), anyString())).thenReturn(true);

            // When
            ProductReview result = service.createOrUpdateEmbedding(review);

            // Then
            assertThat(result).isNotNull();
            verify(embeddingModel, never()).embed(anyString());
            verify(embeddingRepository, never()).upsertEmbedding(anyLong(), anyList(), anyString(), anyString());
        }

        @Test
        @DisplayName("sollte neue Review zuerst speichern")
        void shouldSaveNewReviewFirst() {
            // Given
            ProductReview newReview = createTestReview(null, "Neue Bewertung", 4);
            ProductReview savedReview = createTestReview(42L, "Neue Bewertung", 4);
            
            when(reviewRepository.save(newReview)).thenReturn(savedReview);
            when(textBuilder.buildEmbeddingText(any(), anyInt())).thenReturn("text");
            when(embeddingRepository.existsWithHash(anyLong(), anyString())).thenReturn(false);
            when(embeddingModel.embed(anyString())).thenReturn(createMockEmbedding(1536));

            // When
            ProductReview result = service.createOrUpdateEmbedding(newReview);

            // Then
            verify(reviewRepository).save(newReview);
            assertThat(result.getId()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("findSimilar Tests")
    class FindSimilarTests {

        @Test
        @DisplayName("sollte ähnliche Reviews finden")
        void shouldFindSimilarReviews() {
            // Given
            String query = "Qualität des Produkts";
            float[] queryEmbedding = createMockEmbedding(1536);
            List<Long> reviewIds = List.of(1L, 2L, 3L);
            List<ProductReview> reviews = List.of(
                    createTestReview(1L, "Gute Qualität", 5),
                    createTestReview(2L, "Qualität ok", 4),
                    createTestReview(3L, "Schlechte Qualität", 2)
            );
            
            when(embeddingModel.embed(query)).thenReturn(queryEmbedding);
            when(embeddingRepository.findSimilarReviewIds(anyList(), eq(50))).thenReturn(reviewIds);
            when(reviewRepository.findAllByIdWithProductAndCustomer(reviewIds)).thenReturn(reviews);

            // When
            List<ProductReview> result = service.findSimilar(query, 50);

            // Then
            assertThat(result).hasSize(3);
            verify(embeddingModel).embed(query);
            verify(embeddingRepository).findSimilarReviewIds(anyList(), eq(50));
        }

        @Test
        @DisplayName("sollte leere Liste bei leerer Query zurückgeben")
        void shouldReturnEmptyListForEmptyQuery() {
            // When
            List<ProductReview> result = service.findSimilar("", 10);

            // Then
            assertThat(result).isEmpty();
            verify(embeddingModel, never()).embed(anyString());
        }

        @Test
        @DisplayName("sollte leere Liste bei null Query zurückgeben")
        void shouldReturnEmptyListForNullQuery() {
            // When
            List<ProductReview> result = service.findSimilar(null, 10);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("sollte Standard Top-K verwenden wenn limit <= 0")
        void shouldUseDefaultTopKWhenLimitIsZeroOrNegative() {
            // Given
            String query = "Test query";
            when(embeddingModel.embed(query)).thenReturn(createMockEmbedding(1536));
            when(embeddingRepository.findSimilarReviewIds(anyList(), anyInt())).thenReturn(List.of());

            // When
            service.findSimilar(query, 0);

            // Then
            verify(embeddingRepository).findSimilarReviewIds(anyList(), eq(50)); // Default from AiProperties
        }
    }

    @Nested
    @DisplayName("createEmbeddingsBatch Tests")
    class BatchEmbeddingTests {

        @Test
        @DisplayName("sollte alle Reviews in Batch verarbeiten")
        void shouldProcessAllReviewsInBatch() {
            // Given
            List<ProductReview> reviews = List.of(
                    createTestReview(1L, "Review 1", 5),
                    createTestReview(2L, "Review 2", 4),
                    createTestReview(3L, "Review 3", 3)
            );
            
            when(textBuilder.buildEmbeddingText(any(), anyInt())).thenReturn("text");
            when(embeddingRepository.existsWithHash(anyLong(), anyString())).thenReturn(false);
            when(embeddingModel.embed(anyString())).thenReturn(createMockEmbedding(1536));

            // When
            int count = service.createEmbeddingsBatch(reviews);

            // Then
            assertThat(count).isEqualTo(3);
            verify(embeddingRepository, times(3)).upsertEmbedding(anyLong(), anyList(), anyString(), anyString());
        }

        @Test
        @DisplayName("sollte bei Fehler weitermachen mit nächster Review")
        void shouldContinueOnError() {
            // Given
            List<ProductReview> reviews = List.of(
                    createTestReview(1L, "Review 1", 5),
                    createTestReview(2L, "Review 2", 4)
            );
            
            when(textBuilder.buildEmbeddingText(any(), anyInt())).thenReturn("text");
            when(embeddingRepository.existsWithHash(anyLong(), anyString())).thenReturn(false);
            when(embeddingModel.embed(anyString()))
                    .thenThrow(new RuntimeException("API Error"))
                    .thenReturn(createMockEmbedding(1536));

            // When
            int count = service.createEmbeddingsBatch(reviews);

            // Then
            assertThat(count).isEqualTo(1); // Nur eine erfolgreich
        }
    }

    @Nested
    @DisplayName("getStats Tests")
    class StatsTests {

        @Test
        @DisplayName("sollte korrekte Statistiken berechnen")
        void shouldCalculateCorrectStats() {
            // Given
            when(embeddingRepository.count()).thenReturn(80L);
            when(reviewRepository.count()).thenReturn(100L);

            // When
            ReviewEmbeddingServiceV2.EmbeddingStats stats = service.getStats();

            // Then
            assertThat(stats.totalEmbeddings()).isEqualTo(80);
            assertThat(stats.totalReviews()).isEqualTo(100);
            assertThat(stats.missingEmbeddings()).isEqualTo(20);
        }
    }

    // === Helper Methods ===

    private ProductReview createTestReview(Long id, String comment, int rating) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Produkt");
        product.setCategory("Test Kategorie");

        ProductReview review = new ProductReview();
        review.setId(id);
        review.setComment(comment);
        review.setRating(rating);
        review.setProduct(product);
        
        return review;
    }

    private float[] createMockEmbedding(int dimensions) {
        float[] embedding = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            embedding[i] = (float) Math.random();
        }
        return embedding;
    }
}
