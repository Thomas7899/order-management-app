package com.thomas.order_management.service.reviews;

import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.model.ReviewAspect;
import com.thomas.order_management.model.ReviewInsight;
import com.thomas.order_management.model.ReviewSentiment;
import com.thomas.order_management.model.ReviewTheme;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewInsightRepository;
import com.thomas.order_management.service.CurrentUserProvider;
import com.thomas.order_management.service.ai.StructuredAi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Antworten des Kundenservice auf Bewertungen: Vorschlag per KI (oder Vorlage ohne KI), gespeichert
 * wird immer der vom Menschen geprüfte Text.
 */
@Service
public class ReviewReplyService {

    public record Suggestion(String text, String source) {
    }

    record Reply(String text) {
    }

    private final ProductReviewRepository reviewRepository;
    private final ReviewInsightRepository insightRepository;
    private final StructuredAi ai;
    private final CurrentUserProvider currentUser;

    public ReviewReplyService(ProductReviewRepository reviewRepository, ReviewInsightRepository insightRepository,
                              StructuredAi ai, CurrentUserProvider currentUser) {
        this.reviewRepository = reviewRepository;
        this.insightRepository = insightRepository;
        this.ai = ai;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public Suggestion suggest(Long reviewId) {
        ProductReview review = load(reviewId);
        List<ReviewAspect> aspects = insightRepository.findWithAspectsByReviewIdIn(List.of(reviewId)).stream()
                .findFirst().map(ReviewInsight::getAspects).map(List::copyOf).orElse(List.of());
        String firstName = review.getCustomer() != null ? review.getCustomer().getFirstName() : null;
        String product = review.getProduct().getName();

        String themes = aspects.stream()
                .map(a -> a.getTheme().getDisplayName() + " (" + a.getSentiment().getDisplayName().toLowerCase() + ")")
                .distinct().reduce((a, b) -> a + ", " + b).orElse("keine erkannt");
        Optional<Reply> reply = ai.call("""
                Schreibe als Kundenservice der OM Handels GmbH eine öffentliche Antwort auf diese Produktbewertung.

                Material: %s
                Sterne: %d von 5
                Kunde (Vorname): %s
                Erkannte Themen: %s
                Bewertung: "%s"

                Regeln:
                - Sie-Form, freundlich und sachlich, 3 bis 5 Sätze, Anrede mit dem Vornamen, wenn vorhanden
                - auf die konkreten Punkte der Bewertung eingehen; bei Lob bedanken, bei Kritik Verständnis zeigen
                - bei Defekten oder Problemen anbieten, sich mit der Bestellnummer beim Kundenservice zu melden,
                  damit Umtausch oder Reparatur geprüft werden kann (keine E-Mail-Adresse oder Telefonnummer nennen)
                - keine Zusagen zu Erstattungen, keine Schuldzuweisungen, keine erfundenen Ursachen oder Fakten
                - Gruß: "Ihr Kundenservice der OM Handels GmbH"
                """.formatted(product, review.getRating(), firstName == null ? "unbekannt" : firstName, themes,
                review.getComment()), Reply.class, false);

        return reply.map(Reply::text).filter(t -> !t.isBlank())
                .map(t -> new Suggestion(t.trim(), ReviewAnalyticsService.SOURCE_AI))
                .orElseGet(() -> new Suggestion(template(review, firstName, aspects), ReviewAnalyticsService.SOURCE_RULES));
    }

    @Transactional
    public ProductReview reply(Long reviewId, String text) {
        ProductReview review = load(reviewId);
        review.setReplyText(text.trim());
        review.setRepliedAt(LocalDateTime.now());
        review.setRepliedBy(currentUser.currentUsername());
        return reviewRepository.save(review);
    }

    private ProductReview load(Long reviewId) {
        return reviewRepository.findAllByIdWithProductAndCustomer(List.of(reviewId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Bewertung " + reviewId + " nicht gefunden"));
    }

    /** Vorlage ohne KI, abhängig von Sternen und dem wichtigsten kritisierten Thema. */
    private static String template(ProductReview review, String firstName, List<ReviewAspect> aspects) {
        String greeting = firstName == null ? "Guten Tag," : "Guten Tag " + firstName + ",";
        String product = review.getProduct().getName();
        String closing = "\n\nIhr Kundenservice der OM Handels GmbH";
        if (review.getRating() >= 4) {
            return greeting + "\n\nvielen Dank für Ihre positive Bewertung zu „" + product + "“. "
                    + "Es freut uns sehr, dass Sie zufrieden sind, und wir wünschen Ihnen weiterhin viel Freude damit." + closing;
        }
        ReviewTheme criticized = aspects.stream().filter(a -> a.getSentiment() == ReviewSentiment.NEGATIVE)
                .map(ReviewAspect::getTheme).findFirst().orElse(null);
        String subject = criticized == null ? "unserem Produkt"
                : switch (criticized) {
                    case DELIVERY -> "der Lieferung";
                    case SERVICE -> "unserem Kundenservice";
                    case PRICE -> "dem Preis-Leistungs-Verhältnis";
                    case USABILITY -> "Aufbau bzw. Bedienung";
                    default -> "dem Produkt";
                };
        return greeting + "\n\nvielen Dank für Ihre offene Rückmeldung zu „" + product + "“. Es tut uns leid, dass Sie mit "
                + subject + " nicht zufrieden waren. Bitte melden Sie sich mit Ihrer Bestellnummer bei unserem "
                + "Kundenservice, damit wir Ihr Anliegen prüfen und eine Lösung finden können." + closing;
    }
}
