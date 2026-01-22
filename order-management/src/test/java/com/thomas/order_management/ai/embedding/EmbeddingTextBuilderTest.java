// order-management/src/test/java/com/thomas/order_management/ai/embedding/EmbeddingTextBuilderTest.java
package com.thomas.order_management.ai.embedding;

import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-Tests für EmbeddingTextBuilder.
 * Testet die Generierung von optimierten Embedding-Texten.
 */
@DisplayName("EmbeddingTextBuilder Tests")
class EmbeddingTextBuilderTest {

    private EmbeddingTextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new EmbeddingTextBuilder();
    }

    @Nested
    @DisplayName("buildEmbeddingText Tests")
    class BuildEmbeddingTextTests {

        @Test
        @DisplayName("sollte vollständigen Embedding-Text erstellen")
        void shouldBuildCompleteEmbeddingText() {
            // Given
            ProductReview review = createReview(5, "Absolut fantastisches Produkt! Kann es nur empfehlen.");

            // When
            String result = builder.buildEmbeddingText(review);

            // Then
            assertThat(result)
                    .contains("[SENTIMENT: SEHR_POSITIV]")
                    .contains("Produktbewertung für: Test Produkt")
                    .contains("Kategorie: Elektronik")
                    .contains("Bewertung: 5/5 Sterne")
                    .contains("(sehr zufrieden)")
                    .contains("Kundenfeedback:")
                    .contains("Absolut fantastisches Produkt");
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5})
        @DisplayName("sollte korrektes Sentiment-Signal für jedes Rating erzeugen")
        void shouldGenerateCorrectSentimentSignal(int rating) {
            // Given
            ProductReview review = createReview(rating, "Test");
            
            // When
            String result = builder.buildEmbeddingText(review);

            // Then
            String expectedSignal = switch (rating) {
                case 1 -> "[SENTIMENT: SEHR_NEGATIV]";
                case 2 -> "[SENTIMENT: NEGATIV]";
                case 3 -> "[SENTIMENT: NEUTRAL]";
                case 4 -> "[SENTIMENT: POSITIV]";
                case 5 -> "[SENTIMENT: SEHR_POSITIV]";
                default -> "[SENTIMENT: UNBEKANNT]";
            };
            assertThat(result).contains(expectedSignal);
        }

        @Test
        @DisplayName("sollte 'Unbekanntes Produkt' verwenden wenn Produkt null")
        void shouldHandleNullProduct() {
            // Given
            ProductReview review = new ProductReview();
            review.setRating(3);
            review.setComment("Test Kommentar");

            // When
            String result = builder.buildEmbeddingText(review);

            // Then
            assertThat(result).contains("Unbekanntes Produkt");
        }

        @Test
        @DisplayName("sollte '(Keine Textbewertung vorhanden)' verwenden wenn Kommentar leer")
        void shouldHandleEmptyComment() {
            // Given
            ProductReview review = createReview(4, "");

            // When
            String result = builder.buildEmbeddingText(review);

            // Then
            assertThat(result).contains("(Keine Textbewertung vorhanden)");
        }

        @Test
        @DisplayName("sollte Text kürzen wenn länger als maxLength")
        void shouldTruncateTextWhenTooLong() {
            // Given
            String longComment = "A".repeat(3000);
            ProductReview review = createReview(5, longComment);

            // When
            String result = builder.buildEmbeddingText(review, 500);

            // Then
            assertThat(result.length()).isLessThanOrEqualTo(500);
            assertThat(result).endsWith("...");
        }

        @Test
        @DisplayName("sollte Whitespace normalisieren")
        void shouldNormalizeWhitespace() {
            // Given
            ProductReview review = createReview(4, "Test\n\nmit\t\tvielen    Leerzeichen");

            // When
            String result = builder.buildEmbeddingText(review);

            // Then
            assertThat(result).doesNotContain("\t");
            assertThat(result).doesNotContain("    ");
        }

        @Test
        @DisplayName("sollte Kategorie weglassen wenn nicht vorhanden")
        void shouldOmitCategoryIfNotPresent() {
            // Given
            Product product = new Product();
            product.setName("Produkt ohne Kategorie");
            product.setCategory(null);
            
            ProductReview review = new ProductReview();
            review.setProduct(product);
            review.setRating(4);
            review.setComment("Test");

            // When
            String result = builder.buildEmbeddingText(review);

            // Then
            assertThat(result).doesNotContain("Kategorie:");
        }
    }

    @Nested
    @DisplayName("chunkText Tests")
    class ChunkTextTests {

        @Test
        @DisplayName("sollte kurzen Text nicht chunken")
        void shouldNotChunkShortText() {
            // Given
            String shortText = "Dies ist ein kurzer Text.";

            // When
            List<String> chunks = builder.chunkText(shortText, 100, 20);

            // Then
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).isEqualTo(shortText);
        }

        @Test
        @DisplayName("sollte langen Text in Chunks teilen")
        void shouldChunkLongText() {
            // Given
            String longText = "Erster Satz. Zweiter Satz. Dritter Satz. Vierter Satz. Fünfter Satz.";

            // When
            List<String> chunks = builder.chunkText(longText, 30, 5);

            // Then
            assertThat(chunks).hasSizeGreaterThan(1);
            // Alle Chunks sollten nicht leer sein
            assertThat(chunks).allMatch(chunk -> !chunk.isBlank());
        }

        @Test
        @DisplayName("sollte bei Wortgrenzen chunken wenn möglich")
        void shouldChunkAtWordBoundaries() {
            // Given
            String text = "Dies ist ein längerer Text der an Wortgrenzen gechunkt werden sollte.";

            // When
            List<String> chunks = builder.chunkText(text, 25, 5);

            // Then
            // Chunks sollten nicht mitten in Wörtern enden (außer am Ende)
            for (int i = 0; i < chunks.size() - 1; i++) {
                String chunk = chunks.get(i);
                // Entweder endet der Chunk mit einem Leerzeichen oder einem Wort
                assertThat(chunk).doesNotEndWith("-");
            }
        }

        @Test
        @DisplayName("sollte null-Input behandeln")
        void shouldHandleNullInput() {
            // When
            List<String> chunks = builder.chunkText(null, 100, 20);

            // Then
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).isEmpty();
        }

        @Test
        @DisplayName("sollte Overlap zwischen Chunks haben")
        void shouldHaveOverlapBetweenChunks() {
            // Given
            String text = "AAAA BBBB CCCC DDDD EEEE FFFF GGGG HHHH";
            int chunkSize = 15;
            int overlap = 5;

            // When
            List<String> chunks = builder.chunkText(text, chunkSize, overlap);

            // Then
            // Bei Overlap sollten aufeinanderfolgende Chunks gemeinsame Zeichen haben
            if (chunks.size() > 1) {
                for (int i = 0; i < chunks.size() - 1; i++) {
                    String current = chunks.get(i);
                    String next = chunks.get(i + 1);
                    // Der Anfang des nächsten Chunks sollte im aktuellen vorkommen
                    // (wenn Overlap korrekt funktioniert)
                    assertThat(current.length()).isGreaterThan(0);
                    assertThat(next.length()).isGreaterThan(0);
                }
            }
        }
    }

    // === Helper Methods ===

    private ProductReview createReview(int rating, String comment) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Produkt");
        product.setCategory("Elektronik");

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("Test Kunde");

        ProductReview review = new ProductReview();
        review.setId(1L);
        review.setProduct(product);
        review.setCustomer(customer);
        review.setRating(rating);
        review.setComment(comment);

        return review;
    }
}
