package com.thomas.order_management.repository;

import com.thomas.order_management.model.ReviewInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewInsightRepository extends JpaRepository<ReviewInsight, Long> {

    @Query("SELECT DISTINCT i FROM ReviewInsight i LEFT JOIN FETCH i.aspects WHERE i.reviewId IN :ids")
    List<ReviewInsight> findWithAspectsByReviewIdIn(@Param("ids") Collection<Long> ids);

    long countBySource(ReviewInsight.Source source);

    /** Bewertungen ohne Einordnung oder mit der angegebenen Herkunft (z. B. regelbasiert, für die KI-Nachklassifizierung). */
    @Query("""
        SELECT r.id FROM ProductReview r
        WHERE NOT EXISTS (SELECT 1 FROM ReviewInsight i WHERE i.reviewId = r.id)
           OR EXISTS (SELECT 1 FROM ReviewInsight i WHERE i.reviewId = r.id AND i.source = :source)
        ORDER BY r.id
        """)
    List<Long> findReviewIdsWithoutInsightOrSource(@Param("source") ReviewInsight.Source source);

    @Query("SELECT r.id FROM ProductReview r WHERE NOT EXISTS (SELECT 1 FROM ReviewInsight i WHERE i.reviewId = r.id) ORDER BY r.id")
    List<Long> findReviewIdsWithoutInsight();
}
