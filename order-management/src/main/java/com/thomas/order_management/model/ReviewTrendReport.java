package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "review_trend_reports")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewTrendReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Zeitfenster der Auswertung (optional filterbar) */
    private LocalDate windowStart;
    private LocalDate windowEnd;

    /** Wann der Report erstellt wurde */
    private Instant generatedAt;

    /** Kurz-Zusammenfassung */
    @Column(length = 2000)
    private String summary;

    /** Trends als JSON (Postgres jsonb) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> positiveTrends;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> negativeTrends;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> neutralObservations;
}
