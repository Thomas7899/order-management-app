// order-management/src/main/java/com/thomas/order_management/repository/ReviewEmbeddingRepositoryV2.java
package com.thomas.order_management.repository;

import com.thomas.order_management.ai.embedding.ReviewEmbeddingServiceV2.SimilarityResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Optimiertes Repository für Review-Embeddings mit pgvector.
 * 
 * <h2>Verbesserungen gegenüber V1:</h2>
 * <ul>
 *   <li>HNSW-Index Unterstützung für schnellere Suche</li>
 *   <li>Cosine-Similarity (<=>) statt nur L2-Distanz (<->)</li>
 *   <li>Content-Hash für Idempotenz</li>
 *   <li>Source-Text Speicherung für Debugging</li>
 *   <li>Similarity-Score Rückgabe für Ranking</li>
 * </ul>
 * 
 * <h2>Index-Strategie:</h2>
 * <pre>
 * -- HNSW Index für schnelle Approximate Nearest Neighbor Suche
 * CREATE INDEX ON review_embeddings USING hnsw (embedding vector_cosine_ops)
 *   WITH (m = 16, ef_construction = 64);
 * </pre>
 * 
 * @author Thomas Osterlehner
 * @since 2.0
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ReviewEmbeddingRepositoryV2 {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Speichert oder aktualisiert ein Embedding (Upsert).
     * 
     * @param reviewId Review-ID
     * @param embedding Normalisierter Embedding-Vektor
     * @param sourceText Originaltext für Debugging
     * @param contentHash Hash zur Idempotenz-Prüfung
     */
    public void upsertEmbedding(Long reviewId, List<Double> embedding, String sourceText, String contentHash) {
        String vectorStr = embedding.toString();
        
        // Truncate sourceText wenn zu lang
        String truncatedSourceText = sourceText != null && sourceText.length() > 2000 
                ? sourceText.substring(0, 1997) + "..." 
                : sourceText;
        
        String sql = """
            INSERT INTO review_embeddings (review_id, embedding, source_text, content_hash, updated_at)
            VALUES (?, ?::vector, ?, ?, NOW())
            ON CONFLICT (review_id)
            DO UPDATE SET 
                embedding = EXCLUDED.embedding,
                source_text = EXCLUDED.source_text,
                content_hash = EXCLUDED.content_hash,
                updated_at = NOW()
            """;
        
        jdbcTemplate.update(sql, reviewId, vectorStr, truncatedSourceText, contentHash);
        log.trace("Upserted embedding for review {}", reviewId);
    }

    /**
     * Findet ähnliche Review-IDs basierend auf Cosine-Similarity.
     * 
     * <p>Verwendet den Cosine-Distance Operator (<=>) für normalisierte Vektoren.</p>
     * 
     * @param queryVector Normalisierter Query-Vektor
     * @param limit Maximale Anzahl der Ergebnisse
     * @return Liste von Review-IDs, sortiert nach Ähnlichkeit (höchste zuerst)
     */
    public List<Long> findSimilarReviewIds(List<Double> queryVector, int limit) {
        String vectorStr = queryVector.toString();
        
        // Verwende Cosine Distance (1 - cosine_similarity)
        // Kleinere Werte = höhere Ähnlichkeit
        String sql = """
            SELECT review_id
            FROM review_embeddings
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;
        
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("review_id"),
                vectorStr, limit
        );
    }

    /**
     * Findet ähnliche Reviews mit Similarity-Score.
     * 
     * @param queryVector Normalisierter Query-Vektor
     * @param limit Maximale Anzahl der Ergebnisse
     * @param minSimilarity Minimale Ähnlichkeit (0.0 - 1.0)
     * @return Liste von Review-ID und Similarity-Score Paaren
     */
    public List<SimilarityResult> findSimilarWithScores(List<Double> queryVector, int limit, double minSimilarity) {
        String vectorStr = queryVector.toString();
        
        // Cosine Similarity = 1 - Cosine Distance
        String sql = """
            SELECT 
                review_id,
                1 - (embedding <=> ?::vector) as similarity
            FROM review_embeddings
            WHERE 1 - (embedding <=> ?::vector) >= ?
            ORDER BY similarity DESC
            LIMIT ?
            """;
        
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SimilarityResult(
                        rs.getLong("review_id"),
                        rs.getDouble("similarity")
                ),
                vectorStr, vectorStr, minSimilarity, limit
        );
    }

    /**
     * Prüft ob ein Embedding mit dem gegebenen Hash existiert.
     * Ermöglicht idempotente Updates.
     */
    public boolean existsWithHash(Long reviewId, String contentHash) {
        String sql = """
            SELECT EXISTS(
                SELECT 1 FROM review_embeddings 
                WHERE review_id = ? AND content_hash = ?
            )
            """;
        
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(sql, Boolean.class, reviewId, contentHash)
        );
    }

    /**
     * Prüft ob ein Embedding für die Review existiert.
     */
    public boolean existsByReviewId(Long reviewId) {
        String sql = "SELECT EXISTS(SELECT 1 FROM review_embeddings WHERE review_id = ?)";
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(sql, Boolean.class, reviewId)
        );
    }

    /**
     * Löscht ein Embedding anhand der Review-ID.
     */
    public void deleteByReviewId(Long reviewId) {
        String sql = "DELETE FROM review_embeddings WHERE review_id = ?";
        jdbcTemplate.update(sql, reviewId);
    }

    /**
     * Löscht alle Embeddings.
     */
    public void deleteAll() {
        String sql = "DELETE FROM review_embeddings";
        jdbcTemplate.update(sql);
        log.info("Deleted all embeddings");
    }

    /**
     * Zählt alle Embeddings.
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM review_embeddings";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    /**
     * Gibt Reviews ohne Embedding zurück.
     */
    public List<Long> findReviewIdsWithoutEmbedding() {
        String sql = """
            SELECT pr.id 
            FROM product_reviews pr
            LEFT JOIN review_embeddings re ON pr.id = re.review_id
            WHERE re.id IS NULL
            """;
        
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("id")
        );
    }

    /**
     * Erstellt den HNSW-Index für schnelle Similarity-Suche.
     * Sollte einmalig beim Setup ausgeführt werden.
     */
    public void createHnswIndex() {
        String sql = """
            CREATE INDEX IF NOT EXISTS idx_review_embeddings_hnsw 
            ON review_embeddings 
            USING hnsw (embedding vector_cosine_ops)
            WITH (m = 16, ef_construction = 64)
            """;
        
        try {
            jdbcTemplate.execute(sql);
            log.info("Created HNSW index for review_embeddings");
        } catch (Exception e) {
            log.warn("Could not create HNSW index: {}", e.getMessage());
        }
    }

    /**
     * Analysiert die Embedding-Tabelle für Query-Optimierung.
     */
    public void analyzeTable() {
        try {
            jdbcTemplate.execute("ANALYZE review_embeddings");
            log.debug("Analyzed review_embeddings table");
        } catch (Exception e) {
            log.warn("Could not analyze table: {}", e.getMessage());
        }
    }
}
