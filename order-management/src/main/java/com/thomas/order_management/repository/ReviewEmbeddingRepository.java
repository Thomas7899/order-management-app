package com.thomas.order_management.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Zugriff auf die pgvector-Tabelle review_embeddings (Kosinus-Abstand, passend zum HNSW-Index). */
@Repository
@RequiredArgsConstructor
public class ReviewEmbeddingRepository {

    /** Treffer der Vektorsuche mit Kosinus-Ähnlichkeit (1 = identisch, 0 = ohne Bezug). */
    public record SimilarReview(long reviewId, double similarity) {
    }

    private final JdbcTemplate jdbcTemplate;

    public List<SimilarReview> findSimilar(List<Double> queryVector, int limit) {
        String sql = """
            SELECT review_id, 1 - (embedding <=> (?::vector)) AS similarity
            FROM review_embeddings
            ORDER BY embedding <=> (?::vector)
            LIMIT ?
            """;
        String vector = queryVector.toString();
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SimilarReview(rs.getLong("review_id"), rs.getDouble("similarity")),
                vector, vector, limit);
    }

    public void upsertEmbedding(Long reviewId, String sourceText, List<Double> vector) {
        jdbcTemplate.update("""
            INSERT INTO review_embeddings (review_id, source_text, embedding, updated_at)
            VALUES (?, ?, ?::vector, CURRENT_TIMESTAMP)
            ON CONFLICT (review_id)
            DO UPDATE SET embedding = EXCLUDED.embedding, source_text = EXCLUDED.source_text, updated_at = CURRENT_TIMESTAMP
            """, reviewId, sourceText, vector.toString());
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review_embeddings", Long.class);
        return count == null ? 0 : count;
    }
}
