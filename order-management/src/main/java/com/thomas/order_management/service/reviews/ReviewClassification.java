package com.thomas.order_management.service.reviews;

import com.thomas.order_management.model.ReviewAspect;
import com.thomas.order_management.model.ReviewEmotion;
import com.thomas.order_management.model.ReviewInsight;
import com.thomas.order_management.model.ReviewSentiment;

import java.util.List;

/** Einordnung einer Bewertung, bevor sie als {@link ReviewInsight} gespeichert wird. */
public record ReviewClassification(
        ReviewSentiment sentiment,
        double score,
        ReviewEmotion emotion,
        List<ReviewAspect> aspects,
        ReviewInsight.Source source
) {
}
