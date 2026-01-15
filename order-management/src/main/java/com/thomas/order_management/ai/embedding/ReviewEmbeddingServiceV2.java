// order-management/src/main/java/com/thomas/order_management/ai/embedding/ReviewEmbeddingServiceV2.java
package com.thomas.order_management.ai.embedding;

import com.thomas.order_management.ai.config.AiProperties;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewEmbeddingRepositoryV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Überarbeiteter Review-Embedding-Service nach Clean-Architecture-Prinzipien.
 * 
 * <h2>Verbesserungen gegenüber V1:</h2>
 * <ul>
 *   <li>Retry-Logik für API-Aufrufe mit exponentiellem Backoff</li>
 *   <li>Idempotente Embedding-Erstellung (Upsert mit Hash-Check)</li>
 *   <li>Optimierter Embedding-Text mit Kontext und Sentiment-Signal</li>
 *   <li>Source-Text-Speicherung für Debugging und Re-Embedding</li>
 *   <li>Batch-Processing für effiziente Bulk-Operationen</li>
 *   <li>Detailliertes Logging und Metriken</li>
 * </ul>
 * 
 * <h2>Embedding-Strategie:</h2>
 * <p>Verwendet text-embedding-3-small (1536 Dimensionen) mit:</p>
 * <ul>
 *   <li>L2-Normalisierung für Cosine-Similarity</li>
 *   <li>Kontext-angereicherten Text (Produkt, Rating, Sentiment)</li>
 *   <li>Idempotenz durch Content-Hash</li>
 * </ul>
 * 
 * @author Thomas Osterlehner
 * @since 2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReviewEmbeddingServiceV2 {

    private final ProductReviewRepository reviewRepository;
    private final ReviewEmbeddingRepositoryV2 embeddingRepository;
    private final OpenAiEmbeddingModel embeddingModel;
    private final EmbeddingTextBuilder textBuilder;
    private final AiProperties aiProperties;

    /**
     * Erstellt oder aktualisiert ein Embedding für eine Review.
     * 
     * <p>Idempotent: Überprüft via Hash, ob das Embedding bereits existiert.</p>
     * 
     * @param review Die Review, für die ein Embedding erstellt werden soll
     * @return Die gespeicherte Review
     */
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public ProductReview createOrUpdateEmbedding(ProductReview review) {
        log.debug("Creating embedding for review ID: {}", review.getId());
        
        // 1. Review speichern falls neu
        ProductReview saved = review.getId() == null 
                ? reviewRepository.save(review) 
                : review;

        // 2. Embedding-Text bauen
        String embeddingText = textBuilder.buildEmbeddingText(saved, 
                aiProperties.getEmbedding().getMaxTextLength());
        
        // 3. Hash für Idempotenz-Check
        String contentHash = computeHash(embeddingText);
        
        // 4. Prüfe ob Embedding bereits existiert und aktuell ist
        if (embeddingRepository.existsWithHash(saved.getId(), contentHash)) {
            log.debug("Embedding for review {} is up-to-date, skipping", saved.getId());
            return saved;
        }

        // 5. Embedding generieren
        float[] rawEmbedding = embeddingModel.embed(embeddingText);
        List<Double> normalizedEmbedding = normalizeL2(rawEmbedding);

        // 6. Speichern mit Metadaten
        embeddingRepository.upsertEmbedding(
                saved.getId(), 
                normalizedEmbedding, 
                embeddingText, 
                contentHash
        );

        log.info("Created/updated embedding for review ID: {}", saved.getId());
        return saved;
    }

    /**
     * Findet semantisch ähnliche Reviews basierend auf einer Suchanfrage.
     * 
     * @param query Die Suchanfrage
     * @param limit Maximale Anzahl der Ergebnisse
     * @return Liste ähnlicher Reviews, sortiert nach Ähnlichkeit
     */
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<ProductReview> findSimilar(String query, int limit) {
        log.debug("Finding similar reviews for query: '{}', limit: {}", 
                truncateForLog(query), limit);

        if (query == null || query.isBlank()) {
            log.warn("Empty query provided, returning empty result");
            return List.of();
        }

        // Query-Embedding generieren
        float[] queryEmbedding = embeddingModel.embed(query);
        List<Double> normalizedQuery = normalizeL2(queryEmbedding);

        int topK = limit > 0 ? limit : aiProperties.getEmbedding().getDefaultTopK();

        // Ähnliche Review-IDs finden
        List<Long> reviewIds = embeddingRepository.findSimilarReviewIds(normalizedQuery, topK);

        if (reviewIds.isEmpty()) {
            log.debug("No similar reviews found");
            return List.of();
        }

        // Reviews laden mit Eager-Fetch für Product und Customer
        List<ProductReview> reviews = reviewRepository.findAllByIdWithProductAndCustomer(reviewIds);
        
        log.debug("Found {} similar reviews", reviews.size());
        return reviews;
    }

    /**
     * Findet ähnliche Reviews mit Similarity-Score.
     *
     * @param query Die Suchanfrage
     * @param limit Maximale Anzahl der Ergebnisse
     * @param minSimilarity Minimale Ähnlichkeit (0.0 - 1.0)
     * @return Liste von Review-ID und Similarity-Score Paaren
     */
    public List<SimilarityResult> findSimilarWithScores(String query, int limit, double minSimilarity) {
        log.debug("Finding similar reviews with scores for query: '{}'", truncateForLog(query));

        if (query == null || query.isBlank()) {
            return List.of();
        }

        float[] queryEmbedding = embeddingModel.embed(query);
        List<Double> normalizedQuery = normalizeL2(queryEmbedding);

        return embeddingRepository.findSimilarWithScores(normalizedQuery, limit, minSimilarity);
    }

    /**
     * Batch-Erstellung von Embeddings für mehrere Reviews.
     * 
     * @param reviews Liste der zu verarbeitenden Reviews
     * @return Anzahl erfolgreich verarbeiteter Reviews
     */
    public int createEmbeddingsBatch(List<ProductReview> reviews) {
        log.info("Starting batch embedding for {} reviews", reviews.size());
        
        int successCount = 0;
        int batchSize = aiProperties.getEmbedding().getBatchSize();
        
        for (int i = 0; i < reviews.size(); i += batchSize) {
            List<ProductReview> batch = reviews.subList(i, 
                    Math.min(i + batchSize, reviews.size()));
            
            for (ProductReview review : batch) {
                try {
                    createOrUpdateEmbedding(review);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to create embedding for review {}: {}", 
                            review.getId(), e.getMessage());
                }
            }
            
            log.debug("Processed batch {}-{} of {}", i, i + batch.size(), reviews.size());
        }
        
        log.info("Batch embedding completed: {}/{} successful", successCount, reviews.size());
        return successCount;
    }

    /**
     * Re-Embedded alle Reviews (z.B. nach Modellwechsel).
     *
     * @param forceUpdate Wenn true, werden auch aktuelle Embeddings neu erstellt
     * @return Anzahl aktualisierter Embeddings
     */
    @Transactional
    public int reembedAll(boolean forceUpdate) {
        log.info("Starting re-embedding of all reviews (forceUpdate={})", forceUpdate);
        
        if (forceUpdate) {
            // Lösche alle bestehenden Embeddings für Clean-Slate
            embeddingRepository.deleteAll();
        }
        
        List<ProductReview> allReviews = reviewRepository.findAll();
        return createEmbeddingsBatch(allReviews);
    }

    /**
     * Löscht das Embedding für eine Review.
     *
     * @param reviewId ID der Review
     */
    public void deleteEmbedding(Long reviewId) {
        embeddingRepository.deleteByReviewId(reviewId);
        log.debug("Deleted embedding for review {}", reviewId);
    }

    /**
     * Prüft ob ein Embedding für eine Review existiert.
     */
    public boolean hasEmbedding(Long reviewId) {
        return embeddingRepository.existsByReviewId(reviewId);
    }

    /**
     * Gibt Statistiken über die Embeddings zurück.
     */
    public EmbeddingStats getStats() {
        long totalEmbeddings = embeddingRepository.count();
        long totalReviews = reviewRepository.count();
        long missingEmbeddings = totalReviews - totalEmbeddings;
        
        return new EmbeddingStats(totalEmbeddings, totalReviews, missingEmbeddings);
    }

    // === Private Helper Methods ===

    /**
     * L2-Normalisierung des Embedding-Vektors auf Einheitslänge.
     * Erforderlich für Cosine-Similarity mit pgvector.
     */
    private List<Double> normalizeL2(float[] embedding) {
        double sumSquares = 0.0;
        for (float v : embedding) {
            sumSquares += v * v;
        }
        
        double norm = Math.sqrt(sumSquares);
        if (norm == 0) norm = 1.0; // Verhindere Division durch 0
        
        List<Double> normalized = new ArrayList<>(embedding.length);
        for (float v : embedding) {
            normalized.add(v / norm);
        }
        
        return normalized;
    }

    /**
     * Berechnet einen Hash für Idempotenz-Prüfung.
     */
    private String computeHash(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // Verkürzt auf 16 Zeichen
        } catch (java.security.NoSuchAlgorithmException e) {
            // Fallback: Einfacher Hash
            return String.valueOf(content.hashCode());
        }
    }

    /**
     * Trunciert Text für Log-Ausgaben.
     */
    private String truncateForLog(String text) {
        if (text == null) return "(null)";
        return text.length() > 50 ? text.substring(0, 47) + "..." : text;
    }

    // === DTOs ===

    public record SimilarityResult(Long reviewId, double similarity) {}
    
    public record EmbeddingStats(long totalEmbeddings, long totalReviews, long missingEmbeddings) {}
}
