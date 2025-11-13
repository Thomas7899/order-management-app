// order-management/src/main/java/com/thomas/order_management/repository/ReviewEmbeddingRepository.java
package com.thomas.order_management.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveEmbedding(Long reviewId, List<Double> vector) {
        String vectorStr = vector.toString(); 
        String sql = "INSERT INTO review_embeddings (review_id, embedding) VALUES (?, ?::vector)";
        jdbcTemplate.update(sql, reviewId, vectorStr);
    }

    public List<Long> findSimilarReviewIds(List<Double> queryVector) {
        String vectorStr = queryVector.toString(); 
        String sql = """
            SELECT review_id 
            FROM review_embeddings 
            ORDER BY embedding <-> (?::vector) 
            LIMIT 5
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("review_id"), vectorStr);
    }
}
