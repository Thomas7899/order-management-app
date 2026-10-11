package com.thomas.order_management.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ein in der Bewertung angesprochenes Thema mit seiner Tonalität ("Design schön, Akku schwach" = zwei Aspekte). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAspect {

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", length = 20, nullable = false)
    private ReviewTheme theme;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment", length = 20, nullable = false)
    private ReviewSentiment sentiment;
}
