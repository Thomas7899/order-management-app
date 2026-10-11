package com.thomas.order_management.service.reviews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.model.ReviewAspect;
import com.thomas.order_management.model.ReviewSentiment;
import com.thomas.order_management.model.ReviewTheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Misst die regelbasierte Einordnung an allen Sätzen des Textbaukastens, deren Thema und Tonalität bekannt sind.
 */
@DisplayName("Regelbasierte Einordnung von Bewertungen")
class RuleBasedReviewClassifierTest {

    private final RuleBasedReviewClassifier classifier = new RuleBasedReviewClassifier();

    record Labeled(String text, Set<ReviewTheme> themes, ReviewSentiment tone) {
    }

    @Test
    @DisplayName("Erkennt Thema und Tonalität der Sätze aus dem Textbaukasten zuverlässig")
    void shouldClassifyCorpusSentences() throws Exception {
        List<Labeled> sentences = labeledCorpus();
        int themeHits = 0;
        int toneChecks = 0;
        int toneHits = 0;
        List<String> misses = new ArrayList<>();

        for (Labeled s : sentences) {
            // Sterne neutral (3), damit nur der Text über die Tonalität entscheidet
            List<ReviewAspect> aspects = classifier.classify(s.text(), 3).aspects();
            List<ReviewAspect> matching = aspects.stream().filter(a -> s.themes().contains(a.getTheme())).toList();
            if (!matching.isEmpty()) themeHits++;
            else misses.add("THEMA  " + s.themes() + " erwartet: " + s.text() + " -> " + describe(aspects));
            if (s.tone() != ReviewSentiment.NEUTRAL && !matching.isEmpty()) {
                toneChecks++;
                if (matching.stream().anyMatch(a -> a.getSentiment() == s.tone())) toneHits++;
                else misses.add("TON    " + s.tone() + " erwartet: " + s.text() + " -> " + describe(aspects));
            }
        }
        double themeRecall = (double) themeHits / sentences.size();
        double toneAccuracy = (double) toneHits / toneChecks;
        System.out.printf("Sätze: %d, Thema erkannt: %.0f %%, Tonalität richtig: %.0f %%%n",
                sentences.size(), themeRecall * 100, toneAccuracy * 100);
        misses.forEach(System.out::println);

        assertThat(themeRecall).as("Anteil der Sätze mit richtig erkanntem Thema").isGreaterThanOrEqualTo(0.9);
        assertThat(toneAccuracy).as("Anteil richtig erkannter Tonalität").isGreaterThanOrEqualTo(0.9);
    }

    @Test
    @DisplayName("Trennt Lob und Kritik in einem Satz und beachtet Verneinungen")
    void shouldHandleContrastAndNegation() {
        var result = classifier.classify("Das Design ist schick, aber der Akku ist schon nach wenigen Stunden leer.", 3);
        assertThat(result.aspects()).extracting(a -> a.getTheme() + ":" + a.getSentiment())
                .contains("DESIGN:POSITIVE", "FUNCTION:NEGATIVE");

        var negated = classifier.classify("Die Montage ist nicht kompliziert.", 4);
        assertThat(negated.aspects()).extracting(a -> a.getTheme() + ":" + a.getSentiment())
                .containsExactly("USABILITY:POSITIVE");
    }

    @Test
    @DisplayName("Sterne bestimmen die Gesamtstimmung, der Text verschiebt sie")
    void shouldDeriveOverallSentiment() {
        assertThat(classifier.classify("Super Teil, klare Empfehlung!", 5).sentiment()).isEqualTo(ReviewSentiment.POSITIVE);
        assertThat(classifier.classify("Nach drei Wochen defekt, sehr enttäuscht.", 1).sentiment()).isEqualTo(ReviewSentiment.NEGATIVE);
        assertThat(classifier.classify("Für den Preis in Ordnung.", 3).sentiment()).isEqualTo(ReviewSentiment.NEUTRAL);
    }

    private static String describe(List<ReviewAspect> aspects) {
        return aspects.stream().map(a -> a.getTheme() + ":" + a.getSentiment()).toList().toString();
    }

    static List<Labeled> labeledCorpus() throws Exception {
        JsonNode corpus;
        try (InputStream in = new ClassPathResource("demo/review-corpus.json").getInputStream()) {
            corpus = new ObjectMapper().readTree(in);
        }
        List<Labeled> result = new ArrayList<>();
        corpus.path("categories").forEach(category -> category.fields().forEachRemaining(aspect ->
                addAll(result, aspect.getValue(), Set.of(ReviewTheme.valueOf(aspect.getKey())))));
        corpus.path("generic").fields().forEachRemaining(aspect ->
                addAll(result, aspect.getValue(), Set.of(ReviewTheme.valueOf(aspect.getKey()))));
        JsonNode scenarios = corpus.path("scenarios");
        scenarios.path("incident").forEach(t -> result.add(new Labeled(t.asText(),
                Set.of(ReviewTheme.FUNCTION, ReviewTheme.DURABILITY), ReviewSentiment.NEGATIVE)));
        scenarios.path("incidentService").forEach(t -> result.add(new Labeled(t.asText(),
                Set.of(ReviewTheme.SERVICE), ReviewSentiment.NEGATIVE)));
        scenarios.path("decline").forEach(t -> result.add(new Labeled(t.asText(),
                Set.of(ReviewTheme.QUALITY, ReviewTheme.DURABILITY), ReviewSentiment.NEGATIVE)));
        scenarios.path("deliveryIncident").forEach(t -> result.add(new Labeled(t.asText(),
                Set.of(ReviewTheme.DELIVERY), ReviewSentiment.NEGATIVE)));
        return result;
    }

    private static void addAll(List<Labeled> result, JsonNode tones, Set<ReviewTheme> themes) {
        tones.path("POS").forEach(t -> result.add(new Labeled(t.asText(), themes, ReviewSentiment.POSITIVE)));
        tones.path("NEG").forEach(t -> result.add(new Labeled(t.asText(), themes, ReviewSentiment.NEGATIVE)));
        tones.path("NEU").forEach(t -> result.add(new Labeled(t.asText(), themes, ReviewSentiment.NEUTRAL)));
    }
}
