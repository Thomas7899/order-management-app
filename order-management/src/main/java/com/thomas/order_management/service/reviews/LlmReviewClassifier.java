package com.thomas.order_management.service.reviews;

import tools.jackson.databind.ObjectMapper;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.model.ReviewAspect;
import com.thomas.order_management.model.ReviewEmotion;
import com.thomas.order_management.model.ReviewInsight;
import com.thomas.order_management.model.ReviewSentiment;
import com.thomas.order_management.model.ReviewTheme;
import com.thomas.order_management.service.ai.StructuredAi;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ordnet mehrere Bewertungen mit einem Aufruf des Sprachmodells ein. Themen kommen aus einer festen
 * Liste, damit Auswertungen über Zeiträume vergleichbar bleiben.
 */
@Component
public class LlmReviewClassifier {

    record AspectItem(String theme, String sentiment) {
    }

    record Item(Long reviewId, String sentiment, Double score, String emotion, List<AspectItem> aspects) {
    }

    record BatchResult(List<Item> items) {
    }

    /** Platz für rund 20 Einordnungen mit mehreren Themen. */
    private static final int MAX_TOKENS = 4000;

    private final StructuredAi ai;
    private final ObjectMapper objectMapper;

    public LlmReviewClassifier(StructuredAi ai, ObjectMapper objectMapper) {
        this.ai = ai;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return ai.isAvailable();
    }

    /** Einordnung je Bewertungs-ID; fehlt eine Bewertung im Ergebnis, ist sie nicht enthalten. */
    public Map<Long, ReviewClassification> classify(List<ProductReview> reviews) {
        if (reviews.isEmpty()) return Map.of();
        Optional<BatchResult> result = ai.call(prompt(reviews), BatchResult.class, false, MAX_TOKENS);
        if (result.isEmpty() || result.get().items() == null) return Map.of();

        Set<Long> requested = reviews.stream().map(ProductReview::getId).collect(Collectors.toSet());
        Map<Long, ReviewClassification> classifications = new HashMap<>();
        for (Item item : result.get().items()) {
            if (item == null || item.reviewId() == null || !requested.contains(item.reviewId())) continue;
            toClassification(item).ifPresent(c -> classifications.put(item.reviewId(), c));
        }
        return classifications;
    }

    private Optional<ReviewClassification> toClassification(Item item) {
        try {
            ReviewSentiment sentiment = ReviewSentiment.valueOf(item.sentiment());
            ReviewEmotion emotion = ReviewEmotion.valueOf(item.emotion());
            double score = item.score() == null ? 0 : Math.max(-1, Math.min(1, item.score()));
            Set<String> seen = new LinkedHashSet<>();
            List<ReviewAspect> aspects = new ArrayList<>();
            for (AspectItem aspect : item.aspects() == null ? List.<AspectItem>of() : item.aspects()) {
                ReviewTheme theme = ReviewTheme.valueOf(aspect.theme());
                ReviewSentiment aspectSentiment = ReviewSentiment.valueOf(aspect.sentiment());
                if (seen.add(theme + ":" + aspectSentiment)) {
                    aspects.add(new ReviewAspect(theme, aspectSentiment));
                }
            }
            return Optional.of(new ReviewClassification(sentiment, Math.round(score * 100) / 100.0, emotion, aspects,
                    ReviewInsight.Source.LLM));
        } catch (IllegalArgumentException | NullPointerException ex) {
            // Unbekannter Wert vom Modell: diese Bewertung regelbasiert einordnen
            return Optional.empty();
        }
    }

    private String prompt(List<ProductReview> reviews) {
        List<Map<String, Object>> input = reviews.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reviewId", r.getId());
            m.put("produkt", r.getProduct().getName());
            m.put("warengruppe", r.getProduct().getCategory());
            m.put("sterne", r.getRating());
            m.put("text", r.getComment());
            return m;
        }).toList();

        String themes = Arrays.stream(ReviewTheme.values())
                .map(t -> "  " + t.name() + " = " + t.getDisplayName())
                .collect(Collectors.joining("\n"));
        return """
            Ordne jede der folgenden Produktbewertungen ein.

            Je Bewertung:
            - reviewId: unverändert übernehmen
            - sentiment: POSITIVE, NEUTRAL oder NEGATIVE (Gesamteindruck aus Text und Sternen)
            - score: Zahl von -1 (sehr negativ) bis 1 (sehr positiv)
            - emotion: JOY, SATISFACTION, SURPRISE, NEUTRAL, DISAPPOINTMENT, FRUSTRATION oder ANGER
            - aspects: nur die Themen, die im Text tatsächlich angesprochen werden, jeweils mit eigener
              Tonalität (POSITIVE, NEUTRAL, NEGATIVE). Ein Text kann ein Thema loben und ein anderes kritisieren.
              Erlaubte Themen:
            %s

            Gib das JSON kompakt ohne Einrückungen und Zeilenumbrüche aus.

            Bewertungen:
            %s
            """.formatted(themes, objectMapper.writeValueAsString(input));
    }
}
