package com.thomas.order_management.controller;

import com.thomas.order_management.dto.reviews.ReviewInsightDtos.*;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.service.reviews.ReviewAnalyticsService;
import com.thomas.order_management.service.reviews.ReviewInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/reviews/insights")
@RequiredArgsConstructor
@Tag(name = "Bewertungsanalyse", description = "Trends, Auffälligkeiten und Themen der Kundenbewertungen")
public class ReviewInsightController {

    private static final int MAX_DAYS = 366;

    private final ReviewAnalyticsService analytics;
    private final ReviewInsightService insights;

    @Operation(summary = "Trend im Zeitraum gegen den gleich langen Vorzeitraum (gesamt, je Material oder Warengruppe)")
    @GetMapping("/trend")
    public TrendReport trend(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                             @RequestParam(required = false) Long productId,
                             @RequestParam(required = false) String category) {
        validate(start, end);
        return analytics.trend(start, end, productId, category);
    }

    @Operation(summary = "Statistisch auffällige Materialien und Themen im Zeitraum")
    @GetMapping("/anomalies")
    public AnomalyReport anomalies(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        validate(start, end);
        return analytics.anomalies(start, end);
    }

    @Operation(summary = "Stimmung, Emotionen und Themen im Zeitraum")
    @GetMapping("/sentiment")
    public SentimentReport sentiment(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                     @RequestParam(required = false) Long productId,
                                     @RequestParam(required = false) String category) {
        validate(start, end);
        return analytics.sentiment(start, end, productId, category);
    }

    @Operation(summary = "Stand der Einordnung (wie viele Bewertungen per KI bzw. regelbasiert eingeordnet sind)")
    @GetMapping("/classification")
    public Classification classification() {
        return analytics.classification();
    }

    @Operation(summary = "Einordnung per KI im Hintergrund starten (für alle noch regelbasiert eingeordneten Bewertungen)")
    @PostMapping("/classification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Classification startClassification() {
        if (!insights.isAiAvailable()) {
            throw new BusinessException("ZR", "010", "Keine KI verfügbar: OPENAI_API_KEY ist nicht gesetzt",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!insights.startLlmClassification()) {
            throw new BusinessException("ZR", "011", "Die Einordnung per KI läuft bereits", HttpStatus.CONFLICT);
        }
        return analytics.classification();
    }

    private static void validate(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BusinessException("ZR", "001", "Das Ende des Zeitraums liegt vor dem Beginn");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw new BusinessException("ZR", "002", "Der Zeitraum darf höchstens ein Jahr umfassen");
        }
    }
}
