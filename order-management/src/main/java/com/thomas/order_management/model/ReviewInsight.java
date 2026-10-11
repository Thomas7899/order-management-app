package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Einmal berechnete Auswertung einer Bewertung: Stimmung, Emotion und Themen.
 * Auswertungen über Zeiträume lesen nur diese Daten und rufen kein Sprachmodell auf.
 */
@Entity
@Table(name = "review_insights")
@Getter
@Setter
@NoArgsConstructor
public class ReviewInsight {

    /** Woher die Einordnung stammt. */
    public enum Source {
        LLM, RULES
    }

    @Id
    @Column(name = "review_id")
    private Long reviewId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "review_id")
    private ProductReview review;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewSentiment sentiment;

    /** Stimmungswert von -1 (sehr negativ) bis +1 (sehr positiv). */
    @Column(nullable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewEmotion emotion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @ElementCollection
    @CollectionTable(name = "review_insight_aspects", joinColumns = @JoinColumn(name = "review_id"))
    private List<ReviewAspect> aspects = new ArrayList<>();
}
