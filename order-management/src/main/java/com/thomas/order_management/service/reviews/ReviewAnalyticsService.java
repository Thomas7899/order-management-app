package com.thomas.order_management.service.reviews;

import tools.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.reviews.ReviewInsightDtos.*;
import com.thomas.order_management.dto.sales.CodeText;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewInsightRepository;
import com.thomas.order_management.service.ai.StructuredAi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Auswertung der Kundenbewertungen auf Basis der gespeicherten Einordnungen.
 *
 * <p>Erkennung und Kennzahlen sind berechnet und nachvollziehbar: Vergleich mit dem gleich langen
 * Vorzeitraum, Mindestanzahlen, Vergleichsbasis der letzten 180 Tage. Das Sprachmodell formuliert
 * nur Zusammenfassung, Einschätzung und Empfehlungen aus diesen Fakten und Zitaten.</p>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ReviewAnalyticsService {

    static final int MIN_THEME_MENTIONS = 3;
    static final double THEME_SHIFT = 0.15;
    static final int MIN_PRODUCT_REVIEWS = 3;
    static final double PRODUCT_SHIFT = 0.8;
    static final int ANOMALY_MIN_REVIEWS = 4;
    static final int BASELINE_DAYS = 180;
    static final int THEME_MIN_NEGATIVE = 8;
    static final double THEME_RATE_FACTOR = 3.0;

    public static final String SOURCE_AI = "KI";
    public static final String SOURCE_RULES = "REGELN";

    /** Bewertung mit Einordnung, wie sie in die Berechnungen eingeht. */
    record Fact(Long reviewId, Long productId, String productName, String category, int rating, LocalDateTime createdAt,
                String comment, ReviewSentiment sentiment, double score, ReviewEmotion emotion,
                List<ReviewAspect> aspects) {

        LocalDate date() {
            return createdAt.toLocalDate();
        }

        boolean negative() {
            return sentiment == ReviewSentiment.NEGATIVE;
        }
    }

    record Narrative(String summary, List<String> recommendations) {
    }

    record Assessment(String key, String assessment, String recommendedAction) {
    }

    record Assessments(List<Assessment> items) {
    }

    private final ProductReviewRepository reviewRepository;
    private final ReviewInsightRepository insightRepository;
    private final ProductRepository productRepository;
    private final RuleBasedReviewClassifier rules;
    private final ReviewInsightService insightService;
    private final StructuredAi ai;
    private final ObjectMapper objectMapper;

    public ReviewAnalyticsService(ProductReviewRepository reviewRepository, ReviewInsightRepository insightRepository,
                                  ProductRepository productRepository, RuleBasedReviewClassifier rules,
                                  ReviewInsightService insightService, StructuredAi ai,
                                  ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.insightRepository = insightRepository;
        this.productRepository = productRepository;
        this.rules = rules;
        this.insightService = insightService;
        this.ai = ai;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Trend: Zeitraum gegen gleich langen Vorzeitraum
    // ------------------------------------------------------------------

    public TrendReport trend(LocalDate start, LocalDate end, Long productId, String category) {
        Period p = Period.of(start, end);
        Predicate<Fact> scope = scope(productId, category);
        List<Fact> facts = load(p.start.minusDays(BASELINE_DAYS), end).stream().filter(scope).toList();
        List<Fact> current = p.current(facts);
        List<Fact> previous = p.previous(facts);
        List<Fact> history = facts.stream().filter(f -> f.date().isBefore(p.start)).toList();

        List<ThemeTrend> themes = themeTrends(current, previous);
        List<ProductMovement> movements = productId == null ? productMovements(current, previous, history) : List.of();
        List<ProductMovement> declining = movements.stream()
                .filter(m -> m.delta() != null && m.delta() <= -PRODUCT_SHIFT)
                .sorted(Comparator.comparing(ProductMovement::delta)).limit(5).toList();
        List<ProductMovement> improving = movements.stream()
                .filter(m -> m.delta() != null && m.delta() >= PRODUCT_SHIFT)
                .sorted(Comparator.comparing(ProductMovement::delta).reversed()).limit(5).toList();

        PeriodStats cur = stats(current);
        PeriodStats prev = stats(previous);
        List<String> negativeTrends = negativeTrends(themes, declining);
        List<String> positiveTrends = positiveTrends(themes, improving);
        List<String> observations = observations(cur, prev, themes);
        List<ReviewQuote> evidence = evidence(current, themes, declining, improving);

        String title = productId != null
                ? productRepository.findById(productId).map(Product::getName).orElse("Material " + productId)
                : category != null && !category.isBlank() ? "Warengruppe " + category : "Alle Bewertungen";
        String scopeCode = productId != null ? "PRODUCT" : category != null && !category.isBlank() ? "CATEGORY" : "GLOBAL";

        Map<String, Object> narrativeFacts = new LinkedHashMap<>();
        narrativeFacts.put("auswertung", title);
        narrativeFacts.put("zeitraum", start + " bis " + end);
        narrativeFacts.put("vorzeitraum", p.previousStart + " bis " + p.previousEnd);
        narrativeFacts.put("aktuell", cur);
        narrativeFacts.put("vorperiode", prev);
        narrativeFacts.put("themen", themes.stream().map(t -> Map.of("thema", t.theme().text(), "erwähnungen", t.mentions(),
                "anteilNegativ", percent(t.negativeShare()), "anteilNegativVorperiode", percent(t.previousNegativeShare()),
                "entwicklung", t.direction())).toList());
        narrativeFacts.put("verschlechterteMaterialien", declining);
        narrativeFacts.put("verbesserteMaterialien", improving);
        narrativeFacts.put("negativeEntwicklungen", negativeTrends);
        narrativeFacts.put("positiveEntwicklungen", positiveTrends);

        Optional<Narrative> narrative = cur.reviews() == 0 ? Optional.empty() : ai.call("""
                Erstelle für das Management eine kurze Einordnung der Kundenbewertungen.
                Nutze ausschließlich die berechneten Fakten und Zitate unten. Nenne konkrete Zahlen und betroffene
                Materialien, vergleiche mit der Vorperiode und vermeide allgemeine Floskeln.

                Fakten:
                %s

                Zitate aus Bewertungen des Zeitraums:
                %s

                Ausgabe:
                - summary: 3 bis 4 Sätze
                - recommendations: 2 bis 3 konkrete Maßnahmen (je ein Satz), nur wenn die Fakten sie begründen
                """.formatted(json(narrativeFacts), json(quotesForPrompt(evidence))), Narrative.class);

        String summary = narrative.map(Narrative::summary).filter(s -> !s.isBlank())
                .orElseGet(() -> ruleSummary(cur, prev, themes, declining, improving));
        List<String> recommendations = narrative.map(Narrative::recommendations).filter(r -> r != null && !r.isEmpty())
                .orElseGet(() -> ruleRecommendations(themes, declining));

        return new TrendReport(scopeCode, title, start, end, p.previousStart, p.previousEnd, cur, prev, themes,
                declining, improving, positiveTrends, negativeTrends, observations, summary, recommendations,
                narrative.isPresent() ? SOURCE_AI : SOURCE_RULES, evidence);
    }

    // ------------------------------------------------------------------
    // Auffälligkeiten: Zeitraum gegen die 180 Tage davor
    // ------------------------------------------------------------------

    /** Auffällige Materialien ohne KI-Einschätzung (z. B. für die Bestandsanalyse). */
    public List<ProductAnomaly> productAnomalies(LocalDate start, LocalDate end) {
        return anomalies(start, end, false).products();
    }

    public AnomalyReport anomalies(LocalDate start, LocalDate end) {
        return anomalies(start, end, true);
    }

    private AnomalyReport anomalies(LocalDate start, LocalDate end, boolean withNarrative) {
        LocalDate baselineStart = start.minusDays(BASELINE_DAYS);
        List<Fact> facts = load(baselineStart, end);
        List<Fact> current = facts.stream().filter(f -> !f.date().isBefore(start)).toList();
        List<Fact> baseline = facts.stream().filter(f -> f.date().isBefore(start)).toList();
        double baselineAvg = baseline.stream().mapToInt(Fact::rating).average().orElse(4.0);

        Map<Long, List<Fact>> currentByProduct = current.stream().collect(Collectors.groupingBy(Fact::productId));
        Map<Long, List<Fact>> baselineByProduct = baseline.stream().collect(Collectors.groupingBy(Fact::productId));

        record Candidate(ProductAnomaly anomaly, double severityScore) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<Long, List<Fact>> entry : currentByProduct.entrySet()) {
            List<Fact> cur = entry.getValue();
            if (cur.size() < ANOMALY_MIN_REVIEWS) continue;
            List<Fact> base = baselineByProduct.getOrDefault(entry.getKey(), List.of());
            double curAvg = avgRating(cur);
            Double baseAvg = base.size() >= MIN_PRODUCT_REVIEWS ? avgRating(base) : null;
            double reference = baseAvg != null ? baseAvg : baselineAvg;
            double drop = reference - curAvg;
            double negativeShare = share(cur, Fact::negative);
            boolean flagged = (curAvg <= 2.5 && drop >= 1.0) || (negativeShare >= 0.6 && cur.size() >= 5);
            if (!flagged) continue;

            double severityScore = Math.max(drop, 0.5) * Math.sqrt(cur.size());
            String severity = severityScore >= 5 ? "HIGH" : severityScore >= 2.5 ? "MEDIUM" : "LOW";
            Fact any = cur.get(0);
            List<ReviewQuote> evidence = cur.stream().filter(f -> f.rating() <= 2)
                    .sorted(Comparator.comparing(Fact::createdAt).reversed()).limit(3).map(this::quote).toList();
            candidates.add(new Candidate(new ProductAnomaly(any.productId(), any.productName(), any.category(), severity,
                    cur.size(), round1(curAvg), base.size(), baseAvg == null ? null : round1(baseAvg),
                    round2(negativeShare), topProblems(cur), null, null, evidence), severityScore));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::severityScore).reversed());
        List<ProductAnomaly> products = candidates.stream().map(Candidate::anomaly).toList();

        // Themen-Häufungen über alle Materialien; auffällige Materialien herausrechnen, damit sie nicht doppelt zählen
        Set<Long> flaggedProducts = products.stream().map(ProductAnomaly::productId).collect(Collectors.toSet());
        double weeks = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1) / 7.0;
        double baselineWeeks = BASELINE_DAYS / 7.0;
        List<ThemeAnomaly> themes = new ArrayList<>();
        for (ReviewTheme theme : ReviewTheme.values()) {
            long curMentions = mentions(current, theme, flaggedProducts, null);
            long curNegative = mentions(current, theme, flaggedProducts, ReviewSentiment.NEGATIVE);
            long baseMentions = mentions(baseline, theme, flaggedProducts, null);
            long baseNegative = mentions(baseline, theme, flaggedProducts, ReviewSentiment.NEGATIVE);
            double rate = curNegative / weeks;
            double baseRate = baseNegative / baselineWeeks;
            double negShare = curMentions == 0 ? 0 : (double) curNegative / curMentions;
            double baseShare = baseMentions == 0 ? 0 : (double) baseNegative / baseMentions;
            if (curNegative >= THEME_MIN_NEGATIVE && rate >= THEME_RATE_FACTOR * Math.max(baseRate, 0.5)
                    && negShare - baseShare >= THEME_SHIFT) {
                themes.add(new ThemeAnomaly(themeCode(theme), curNegative, round1(rate), round1(baseRate),
                        round2(negShare), round2(baseShare), null));
            }
        }
        // Stärkste Häufungen zuerst (Verhältnis zur üblichen Rate)
        themes.sort(Comparator.comparingDouble((ThemeAnomaly t) ->
                t.negativePerWeek() / Math.max(t.baselineNegativePerWeek(), 0.5)).reversed());
        if (themes.size() > 4) themes = new ArrayList<>(themes.subList(0, 4));

        // Einschätzung und Maßnahme: KI aus Kennzahlen und Zitaten, sonst Regeltexte
        Optional<Assessments> assessments = !withNarrative || (products.isEmpty() && themes.isEmpty()) ? Optional.empty()
                : ai.call(assessmentPrompt(products, themes, current, flaggedProducts), Assessments.class);
        List<String> keys = new ArrayList<>();
        products.forEach(p -> keys.add("P" + p.productId()));
        themes.forEach(t -> keys.add("T" + t.theme().code()));
        Map<String, Assessment> byKey = matchAssessments(keys,
                assessments.map(a -> a.items() == null ? List.<Assessment>of() : a.items()).orElse(List.of()));

        List<ProductAnomaly> assessedProducts = products.stream().map(a -> {
            Assessment x = byKey.get("P" + a.productId());
            String assessment = x != null && notBlank(x.assessment()) ? x.assessment() : ruleAssessment(a);
            String action = x != null && notBlank(x.recommendedAction()) ? x.recommendedAction() : ruleAction(a.topProblems());
            return new ProductAnomaly(a.productId(), a.productName(), a.category(), a.severity(), a.reviews(),
                    a.avgRating(), a.baselineReviews(), a.baselineAvgRating(), a.negativeShare(), a.topProblems(),
                    assessment, action, a.evidence());
        }).toList();
        List<ThemeAnomaly> assessedThemes = themes.stream().map(t -> {
            Assessment x = byKey.get("T" + t.theme().code());
            String assessment = x != null && notBlank(x.assessment()) ? x.assessment()
                    : "%d negative Erwähnungen (%s pro Woche statt sonst %s), Anteil negativ %d %% statt %d %%."
                    .formatted(t.negativeMentions(), fmt(t.negativePerWeek()), fmt(t.baselineNegativePerWeek()),
                            percent(t.negativeShare()), percent(t.baselineNegativeShare()));
            return new ThemeAnomaly(t.theme(), t.negativeMentions(), t.negativePerWeek(), t.baselineNegativePerWeek(),
                    t.negativeShare(), t.baselineNegativeShare(), assessment);
        }).toList();

        return new AnomalyReport(start, end, baselineStart, assessedProducts, assessedThemes,
                !byKey.isEmpty() ? SOURCE_AI : SOURCE_RULES);
    }

    // ------------------------------------------------------------------
    // Themen und Stimmung: reine Auswertung der gespeicherten Einordnungen, ohne KI-Aufruf
    // ------------------------------------------------------------------

    public SentimentReport sentiment(LocalDate start, LocalDate end, Long productId, String category) {
        Period p = Period.of(start, end);
        List<Fact> facts = load(p.previousStart, end).stream().filter(scope(productId, category)).toList();
        List<Fact> current = p.current(facts);
        List<Fact> previous = p.previous(facts);

        List<Count> sentiments = Arrays.stream(ReviewSentiment.values())
                .map(s -> new Count(s.name(), s.getDisplayName(), current.stream().filter(f -> f.sentiment() == s).count()))
                .toList();
        List<Count> emotions = Arrays.stream(ReviewEmotion.values())
                .map(e -> new Count(e.name(), e.getDisplayName(), current.stream().filter(f -> f.emotion() == e).count()))
                .filter(c -> c.count() > 0)
                .sorted(Comparator.comparingLong(Count::count).reversed())
                .toList();

        boolean weekly = ChronoUnit.DAYS.between(start, end) > 21;
        Map<LocalDate, List<Fact>> buckets = current.stream().collect(Collectors.groupingBy(f -> weekly
                ? f.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : f.date()));
        List<SentimentPoint> timeline = new ArrayList<>();
        LocalDate cursor = weekly ? start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : start;
        while (!cursor.isAfter(end)) {
            List<Fact> bucket = buckets.getOrDefault(cursor, List.of());
            timeline.add(new SentimentPoint(cursor, bucket.size(), round2(bucket.stream().mapToDouble(Fact::score).average().orElse(0)),
                    bucket.stream().filter(f -> f.sentiment() == ReviewSentiment.POSITIVE).count(),
                    bucket.stream().filter(f -> f.sentiment() == ReviewSentiment.NEUTRAL).count(),
                    bucket.stream().filter(Fact::negative).count()));
            cursor = weekly ? cursor.plusWeeks(1) : cursor.plusDays(1);
        }

        List<ReviewQuote> critical = current.stream().filter(f -> f.negative() || f.rating() <= 2)
                .sorted(Comparator.comparing(Fact::createdAt).reversed()).limit(10).map(this::quote).toList();

        return new SentimentReport(start, end, p.previousStart, p.previousEnd, stats(current), stats(previous),
                sentiments, emotions, themeTrends(current, previous), timeline, weekly ? "WEEK" : "DAY", critical,
                classification());
    }

    public Classification classification() {
        long byAi = insightRepository.countBySource(ReviewInsight.Source.LLM);
        long byRules = insightRepository.countBySource(ReviewInsight.Source.RULES);
        return new Classification(reviewRepository.count(), byAi, byRules, insightService.isAiAvailable(),
                insightService.jobStatus());
    }

    // ------------------------------------------------------------------
    // Laden
    // ------------------------------------------------------------------

    List<Fact> load(LocalDate from, LocalDate toInclusive) {
        List<ProductReview> reviews = reviewRepository.findWithProductByCreatedAtBetween(
                from.atStartOfDay(), toInclusive.plusDays(1).atStartOfDay());
        Map<Long, ReviewInsight> insights = new HashMap<>();
        List<Long> ids = reviews.stream().map(ProductReview::getId).toList();
        for (int i = 0; i < ids.size(); i += 1000) {
            insightRepository.findWithAspectsByReviewIdIn(ids.subList(i, Math.min(ids.size(), i + 1000)))
                    .forEach(insight -> insights.put(insight.getReviewId(), insight));
        }
        List<Fact> facts = new ArrayList<>(reviews.size());
        for (ProductReview r : reviews) {
            ReviewInsight insight = insights.get(r.getId());
            Product product = r.getProduct();
            if (insight != null) {
                facts.add(new Fact(r.getId(), product.getId(), product.getName(), product.getCategory(), r.getRating(),
                        r.getCreatedAt(), r.getComment(), insight.getSentiment(), insight.getScore(), insight.getEmotion(),
                        List.copyOf(insight.getAspects())));
            } else {
                // Noch nicht eingeordnet (z. B. direkt nach dem Import): regelbasiert, ohne zu speichern
                ReviewClassification c = rules.classify(r.getComment(), r.getRating());
                facts.add(new Fact(r.getId(), product.getId(), product.getName(), product.getCategory(), r.getRating(),
                        r.getCreatedAt(), r.getComment(), c.sentiment(), c.score(), c.emotion(), c.aspects()));
            }
        }
        return facts;
    }

    private static Predicate<Fact> scope(Long productId, String category) {
        if (productId != null) return f -> productId.equals(f.productId());
        if (category != null && !category.isBlank()) return f -> category.equals(f.category());
        return f -> true;
    }

    /** Auswertungszeitraum und der gleich lange Zeitraum direkt davor. */
    private record Period(LocalDate start, LocalDate end, LocalDate previousStart, LocalDate previousEnd) {
        static Period of(LocalDate start, LocalDate end) {
            if (end.isBefore(start)) throw new IllegalArgumentException("Ende liegt vor dem Beginn");
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            LocalDate previousEnd = start.minusDays(1);
            return new Period(start, end, previousEnd.minusDays(days - 1), previousEnd);
        }

        List<Fact> current(List<Fact> facts) {
            return facts.stream().filter(f -> !f.date().isBefore(start) && !f.date().isAfter(end)).toList();
        }

        List<Fact> previous(List<Fact> facts) {
            return facts.stream().filter(f -> !f.date().isBefore(previousStart) && !f.date().isAfter(previousEnd)).toList();
        }
    }

    // ------------------------------------------------------------------
    // Kennzahlen
    // ------------------------------------------------------------------

    static PeriodStats stats(List<Fact> facts) {
        if (facts.isEmpty()) return new PeriodStats(0, 0, 0, 0, 0);
        return new PeriodStats(facts.size(), round2(avgRating(facts)),
                round2(facts.stream().mapToDouble(Fact::score).average().orElse(0)),
                round2(share(facts, f -> f.sentiment() == ReviewSentiment.POSITIVE)),
                round2(share(facts, Fact::negative)));
    }

    static List<ThemeTrend> themeTrends(List<Fact> current, List<Fact> previous) {
        List<ThemeTrend> result = new ArrayList<>();
        for (ReviewTheme theme : ReviewTheme.values()) {
            long cur = mentions(current, theme, Set.of(), null);
            long prev = mentions(previous, theme, Set.of(), null);
            if (cur == 0 && prev == 0) continue;
            double negShare = cur == 0 ? 0 : (double) mentions(current, theme, Set.of(), ReviewSentiment.NEGATIVE) / cur;
            double posShare = cur == 0 ? 0 : (double) mentions(current, theme, Set.of(), ReviewSentiment.POSITIVE) / cur;
            double prevNegShare = prev == 0 ? 0 : (double) mentions(previous, theme, Set.of(), ReviewSentiment.NEGATIVE) / prev;
            String direction;
            if (cur >= MIN_THEME_MENTIONS && prev >= MIN_THEME_MENTIONS) {
                double delta = negShare - prevNegShare;
                direction = delta >= THEME_SHIFT ? "DECLINING" : delta <= -THEME_SHIFT ? "IMPROVING" : "STABLE";
            } else {
                direction = cur >= MIN_THEME_MENTIONS ? "NEW" : "STABLE";
            }
            result.add(new ThemeTrend(themeCode(theme), cur, prev, round2(posShare), round2(negShare), round2(prevNegShare), direction));
        }
        result.sort(Comparator.comparingLong(ThemeTrend::mentions).reversed());
        return result;
    }

    /** Ø-Bewertung je Material im Zeitraum gegen die Vorperiode (bei zu wenigen Bewertungen: die 180 Tage davor). */
    static List<ProductMovement> productMovements(List<Fact> current, List<Fact> previous, List<Fact> history) {
        Map<Long, List<Fact>> cur = current.stream().collect(Collectors.groupingBy(Fact::productId));
        Map<Long, List<Fact>> prev = previous.stream().collect(Collectors.groupingBy(Fact::productId));
        Map<Long, List<Fact>> hist = history.stream().collect(Collectors.groupingBy(Fact::productId));
        List<ProductMovement> result = new ArrayList<>();
        for (Map.Entry<Long, List<Fact>> e : cur.entrySet()) {
            if (e.getValue().size() < MIN_PRODUCT_REVIEWS) continue;
            List<Fact> reference = prev.getOrDefault(e.getKey(), List.of());
            if (reference.size() < MIN_PRODUCT_REVIEWS) reference = hist.getOrDefault(e.getKey(), List.of());
            Fact any = e.getValue().get(0);
            double avg = avgRating(e.getValue());
            Double refAvg = reference.size() >= MIN_PRODUCT_REVIEWS ? avgRating(reference) : null;
            result.add(new ProductMovement(any.productId(), any.productName(), any.category(), e.getValue().size(),
                    round1(avg), reference.size(), refAvg == null ? null : round1(refAvg),
                    refAvg == null ? null : round1(avg - refAvg)));
        }
        return result;
    }

    private static long mentions(List<Fact> facts, ReviewTheme theme, Set<Long> excludedProducts, ReviewSentiment sentiment) {
        return facts.stream()
                .filter(f -> !excludedProducts.contains(f.productId()))
                .flatMap(f -> f.aspects().stream())
                .filter(a -> a.getTheme() == theme && (sentiment == null || a.getSentiment() == sentiment))
                .count();
    }

    private static List<String> topProblems(List<Fact> facts) {
        return facts.stream().flatMap(f -> f.aspects().stream())
                .filter(a -> a.getSentiment() == ReviewSentiment.NEGATIVE)
                .collect(Collectors.groupingBy(ReviewAspect::getTheme, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<ReviewTheme, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey().getDisplayName() + " (" + e.getValue() + "×)")
                .toList();
    }

    // ------------------------------------------------------------------
    // Texte aus Kennzahlen (auch Rückfall ohne KI)
    // ------------------------------------------------------------------

    private static List<String> negativeTrends(List<ThemeTrend> themes, List<ProductMovement> declining) {
        List<String> result = new ArrayList<>();
        themes.stream().filter(t -> "DECLINING".equals(t.direction())).forEach(t -> result.add(
                "%s: %d %% negative Erwähnungen (Vorperiode %d %%)".formatted(t.theme().text(),
                        percent(t.negativeShare()), percent(t.previousNegativeShare()))));
        declining.forEach(m -> result.add("%s: Ø %s Sterne statt %s (%d Bewertungen)".formatted(
                m.productName(), fmt(m.avgRating()), fmt(m.previousAvgRating()), m.reviews())));
        return result;
    }

    private static List<String> positiveTrends(List<ThemeTrend> themes, List<ProductMovement> improving) {
        List<String> result = new ArrayList<>();
        themes.stream().filter(t -> "IMPROVING".equals(t.direction())).forEach(t -> result.add(
                "%s: nur noch %d %% negative Erwähnungen (Vorperiode %d %%)".formatted(t.theme().text(),
                        percent(t.negativeShare()), percent(t.previousNegativeShare()))));
        improving.forEach(m -> result.add("%s: Ø %s Sterne statt %s (%d Bewertungen)".formatted(
                m.productName(), fmt(m.avgRating()), fmt(m.previousAvgRating()), m.reviews())));
        themes.stream().filter(t -> t.mentions() >= 5 && t.positiveShare() >= 0.7).limit(2).forEach(t -> result.add(
                "%s wird überwiegend gelobt (%d %% positiv)".formatted(t.theme().text(), percent(t.positiveShare()))));
        return result;
    }

    private static List<String> observations(PeriodStats cur, PeriodStats prev, List<ThemeTrend> themes) {
        List<String> result = new ArrayList<>();
        result.add(prev.reviews() > 0
                ? "%d Bewertungen (%+d %% ggü. Vorperiode mit %d)".formatted(cur.reviews(),
                Math.round((cur.reviews() - prev.reviews()) * 100.0 / prev.reviews()), prev.reviews())
                : "%d Bewertungen, keine Vergleichswerte aus der Vorperiode".formatted(cur.reviews()));
        if (cur.reviews() > 0 && prev.reviews() > 0) {
            result.add("Ø %s Sterne (Vorperiode %s)".formatted(fmt(cur.avgRating()), fmt(prev.avgRating())));
        }
        themes.stream().filter(t -> "STABLE".equals(t.direction()) && t.mentions() >= MIN_THEME_MENTIONS).limit(2)
                .forEach(t -> result.add("%s unverändert (%d Erwähnungen, %d %% negativ)".formatted(
                        t.theme().text(), t.mentions(), percent(t.negativeShare()))));
        return result;
    }

    private static String ruleSummary(PeriodStats cur, PeriodStats prev, List<ThemeTrend> themes,
                                      List<ProductMovement> declining, List<ProductMovement> improving) {
        if (cur.reviews() == 0) return "Im Zeitraum gibt es keine Bewertungen.";
        StringBuilder sb = new StringBuilder();
        sb.append("%d Bewertungen mit Ø %s Sterne".formatted(cur.reviews(), fmt(cur.avgRating())));
        if (prev.reviews() > 0) {
            sb.append(" (Vorperiode: %d Bewertungen, Ø %s)".formatted(prev.reviews(), fmt(prev.avgRating())));
        }
        sb.append(". Anteil negativer Bewertungen: %d %%".formatted(percent(cur.negativeShare())));
        if (prev.reviews() > 0) sb.append(" (Vorperiode %d %%)".formatted(percent(prev.negativeShare())));
        sb.append('.');
        themes.stream().filter(t -> "DECLINING".equals(t.direction())).findFirst().ifPresent(t -> sb.append(
                " Verschlechtert hat sich vor allem %s mit %d %% negativen Erwähnungen statt %d %%.".formatted(
                        t.theme().text(), percent(t.negativeShare()), percent(t.previousNegativeShare()))));
        if (!declining.isEmpty()) {
            ProductMovement m = declining.get(0);
            sb.append(" Am deutlichsten verschlechtert: %s (Ø %s statt %s).".formatted(m.productName(),
                    fmt(m.avgRating()), fmt(m.previousAvgRating())));
        }
        if (!improving.isEmpty()) {
            ProductMovement m = improving.get(0);
            sb.append(" Am deutlichsten verbessert: %s (Ø %s statt %s).".formatted(m.productName(),
                    fmt(m.avgRating()), fmt(m.previousAvgRating())));
        }
        return sb.toString();
    }

    private static List<String> ruleRecommendations(List<ThemeTrend> themes, List<ProductMovement> declining) {
        List<String> result = new ArrayList<>();
        declining.stream().limit(2).forEach(m -> result.add(
                "%s: Beschwerden der letzten Wochen sichten und Qualitätsmeldung an den Lieferanten prüfen.".formatted(m.productName())));
        themes.stream().filter(t -> "DECLINING".equals(t.direction())).limit(2)
                .forEach(t -> result.add(themeAction(t.theme().code())));
        return result.stream().distinct().limit(3).toList();
    }

    private static String ruleAssessment(ProductAnomaly a) {
        String reference = a.baselineAvgRating() != null
                ? "sonst Ø %s aus %d Bewertungen".formatted(fmt(a.baselineAvgRating()), a.baselineReviews())
                : "deutlich unter dem Durchschnitt aller Materialien";
        return "Ø %s Sterne aus %d Bewertungen im Zeitraum (%s), %d %% negativ.%s".formatted(
                fmt(a.avgRating()), a.reviews(), reference, percent(a.negativeShare()),
                a.topProblems().isEmpty() ? "" : " Häufigste Kritik: " + String.join(", ", a.topProblems()) + ".");
    }

    private static String ruleAction(List<String> topProblems) {
        if (topProblems.isEmpty()) return "Bewertungen sichten und Ursache klären.";
        String top = topProblems.get(0);
        return Arrays.stream(ReviewTheme.values()).filter(t -> top.startsWith(t.getDisplayName()))
                .findFirst().map(t -> themeAction(t.name())).orElse("Bewertungen sichten und Ursache klären.");
    }

    private static String themeAction(String theme) {
        return switch (theme) {
            case "FUNCTION", "DURABILITY", "QUALITY" ->
                    "Qualitätsmeldung anlegen, Restbestand prüfen und den Lieferanten mit den Reklamationen konfrontieren.";
            case "DELIVERY" -> "Verpackung und Versanddienstleister prüfen; Transportschäden mit dem Dienstleister klären.";
            case "SERVICE" -> "Bearbeitungszeiten im Kundenservice prüfen und offene Reklamationen priorisieren.";
            case "USABILITY" -> "Montage- bzw. Bedienungsanleitung überarbeiten und eine Kurzanleitung beilegen.";
            case "PRICE" -> "Preispositionierung gegenüber dem Wettbewerb prüfen.";
            case "DESIGN" -> "Produktbilder und Beschreibung mit der gelieferten Ware abgleichen.";
            default -> "Bewertungen sichten und Ursache klären.";
        };
    }

    // ------------------------------------------------------------------
    // Belege und Prompts
    // ------------------------------------------------------------------

    private List<ReviewQuote> evidence(List<Fact> current, List<ThemeTrend> themes, List<ProductMovement> declining,
                                       List<ProductMovement> improving) {
        Set<ReviewTheme> decliningThemes = themes.stream().filter(t -> "DECLINING".equals(t.direction()))
                .map(t -> ReviewTheme.valueOf(t.theme().code())).collect(Collectors.toSet());
        Set<Long> decliningProducts = declining.stream().map(ProductMovement::productId).collect(Collectors.toSet());
        Set<Long> improvingProducts = improving.stream().map(ProductMovement::productId).collect(Collectors.toSet());
        Comparator<Fact> newestFirst = Comparator.comparing(Fact::createdAt).reversed();

        LinkedHashMap<Long, Fact> selected = new LinkedHashMap<>();
        current.stream().filter(f -> f.rating() <= 2 && (decliningProducts.contains(f.productId())
                        || f.aspects().stream().anyMatch(a -> decliningThemes.contains(a.getTheme()) && a.getSentiment() == ReviewSentiment.NEGATIVE)))
                .sorted(newestFirst).limit(4).forEach(f -> selected.put(f.reviewId(), f));
        if (selected.isEmpty()) {
            current.stream().filter(f -> f.rating() <= 2).sorted(newestFirst).limit(2).forEach(f -> selected.put(f.reviewId(), f));
        }
        List<Fact> positives = current.stream().filter(f -> f.rating() >= 4 && improvingProducts.contains(f.productId()))
                .sorted(newestFirst).limit(2).toList();
        if (positives.isEmpty()) {
            positives = current.stream().filter(f -> f.rating() == 5 && !f.aspects().isEmpty()).sorted(newestFirst).limit(2).toList();
        }
        positives.forEach(f -> selected.put(f.reviewId(), f));
        return selected.values().stream().map(this::quote).toList();
    }

    private ReviewQuote quote(Fact f) {
        return new ReviewQuote(f.reviewId(), f.productId(), f.productName(), f.rating(), f.createdAt(), f.comment(),
                f.sentiment().name(), f.aspects().stream().map(a -> a.getTheme().getDisplayName()).distinct().toList());
    }

    private static List<Map<String, Object>> quotesForPrompt(List<ReviewQuote> quotes) {
        return quotes.stream().map(q -> Map.<String, Object>of("material", q.productName(), "sterne", q.rating(),
                "datum", q.createdAt().toLocalDate().toString(), "text", q.comment())).toList();
    }

    private String assessmentPrompt(List<ProductAnomaly> products, List<ThemeAnomaly> themes, List<Fact> current,
                                    Set<Long> flaggedProducts) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProductAnomaly a : products) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", "P" + a.productId());
            m.put("material", a.productName());
            m.put("bewertungenImZeitraum", a.reviews());
            m.put("durchschnittImZeitraum", a.avgRating());
            m.put("durchschnittVorher", a.baselineAvgRating());
            m.put("anteilNegativProzent", percent(a.negativeShare()));
            m.put("häufigsteKritik", a.topProblems());
            m.put("zitate", a.evidence().stream().map(ReviewQuote::comment).toList());
            items.add(m);
        }
        for (ThemeAnomaly t : themes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", "T" + t.theme().code());
            m.put("thema", t.theme().text());
            m.put("negativeErwähnungen", t.negativeMentions());
            m.put("negativProWoche", t.negativePerWeek());
            m.put("negativProWocheVorher", t.baselineNegativePerWeek());
            m.put("zitate", current.stream()
                    .filter(f -> !flaggedProducts.contains(f.productId()) && f.aspects().stream().anyMatch(a ->
                            a.getTheme().name().equals(t.theme().code()) && a.getSentiment() == ReviewSentiment.NEGATIVE))
                    .sorted(Comparator.comparing(Fact::createdAt).reversed()).limit(4).map(Fact::comment).toList());
            items.add(m);
        }
        return """
                Folgende %d Auffälligkeiten in Kundenbewertungen wurden statistisch erkannt (Zeitraum gegen die 180 Tage davor).
                Gib in items für JEDEN Eintrag genau ein Element zurück, also %d Elemente, mit:
                - key: unverändert aus dem Eintrag übernehmen
                - assessment: 1 bis 2 Sätze Einschätzung mit den wichtigsten Zahlen und der wahrscheinlichsten Ursache,
                  so wie sie aus den Zitaten hervorgeht (keine Ursache erfinden)
                - recommendedAction: eine konkrete nächste Maßnahme für Qualitätsmanagement, Einkauf oder Kundenservice

                Auffälligkeiten:
                %s
                """.formatted(items.size(), items.size(), json(items));
    }

    /** Ordnet die KI-Einschätzungen über den Schlüssel zu, ersatzweise über die Reihenfolge der Eingabe. */
    private static Map<String, Assessment> matchAssessments(List<String> keys, List<Assessment> items) {
        Map<String, Assessment> result = new HashMap<>();
        for (Assessment a : items) {
            if (a != null && a.key() != null && keys.contains(a.key().trim())) result.putIfAbsent(a.key().trim(), a);
        }
        if (result.isEmpty() && items.size() == keys.size()) {
            for (int i = 0; i < keys.size(); i++) {
                if (items.get(i) != null) result.put(keys.get(i), items.get(i));
            }
        }
        if (result.isEmpty() && !keys.isEmpty()) {
            log.warn("KI-Einschätzungen ließen sich nicht zuordnen; erwartet {}, geliefert {}", keys,
                    items.stream().map(a -> a == null ? null : a.key()).toList());
        }
        return result;
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    private static CodeText themeCode(ReviewTheme theme) {
        return CodeText.of(theme.name(), theme.getDisplayName());
    }

    private static double avgRating(List<Fact> facts) {
        return facts.stream().mapToInt(Fact::rating).average().orElse(0);
    }

    private static double share(List<Fact> facts, Predicate<Fact> predicate) {
        return facts.isEmpty() ? 0 : (double) facts.stream().filter(predicate).count() / facts.size();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static int percent(double share) {
        return (int) Math.round(share * 100);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String fmt(Double v) {
        return v == null ? "–" : String.format(Locale.GERMANY, "%.1f", v);
    }
}
