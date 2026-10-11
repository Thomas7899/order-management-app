package com.thomas.order_management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.inventory.InventoryAnalysisDto.*;
import com.thomas.order_management.dto.reviews.ReviewInsightDtos.ProductAnomaly;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.StockMovement;
import com.thomas.order_management.model.WarehouseStock;
import com.thomas.order_management.repository.OrderItemRepository;
import com.thomas.order_management.repository.StockMovementRepository;
import com.thomas.order_management.repository.WarehouseStockRepository;
import com.thomas.order_management.service.ai.StructuredAi;
import com.thomas.order_management.service.reviews.ReviewAnalyticsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bestandsanalyse: Bedarfsprognose, Nachschubvorschläge, Auffälligkeiten und Kennzahlen.
 *
 * <p>Alles wird aus den Daten berechnet: Warenausgänge (601/602) der letzten 12 Wochen mit
 * exponentieller Glättung, Melde- und Höchstbestand je Lagerort, Rückstand offener Aufträge und
 * Auffälligkeiten aus den Kundenbewertungen. Das Sprachmodell fasst die Ergebnisse nur zusammen.</p>
 */
@Service
@Transactional(readOnly = true)
public class InventoryAnalysisService {

    static final int WEEKS = 12;
    static final double SMOOTHING = 0.4;

    private record Narrative(String summary, List<String> actions) {
    }

    /** Verbrauch je Kalenderwoche, älteste Woche zuerst. */
    record WeeklyDemand(double[] weeks) {
        double smoothedDaily() {
            double level = weeks[0];
            for (int i = 1; i < weeks.length; i++) level = SMOOTHING * weeks[i] + (1 - SMOOTHING) * level;
            return level / 7.0;
        }

        double total() {
            return Arrays.stream(weeks).sum();
        }

        String trend() {
            double recent = Arrays.stream(weeks, weeks.length - 4, weeks.length).average().orElse(0);
            double earlier = Arrays.stream(weeks, 0, weeks.length - 4).average().orElse(0);
            if (earlier == 0) return recent > 0 ? "RISING" : "STABLE";
            double ratio = recent / earlier;
            return ratio >= 1.25 ? "RISING" : ratio <= 0.75 ? "FALLING" : "STABLE";
        }

        /** 1 bei gleichmäßigem Verbrauch, gegen 0 bei stark schwankendem. */
        double confidence() {
            double mean = Arrays.stream(weeks).average().orElse(0);
            if (mean == 0) return 0;
            double variance = Arrays.stream(weeks).map(w -> (w - mean) * (w - mean)).average().orElse(0);
            return Math.round(100.0 / (1 + Math.sqrt(variance) / mean)) / 100.0;
        }
    }

    private final StockMovementRepository movementRepository;
    private final WarehouseStockRepository stockRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewAnalyticsService reviewAnalytics;
    private final StructuredAi ai;
    private final ObjectMapper objectMapper;
    private final int leadTimeDays;

    public InventoryAnalysisService(StockMovementRepository movementRepository, WarehouseStockRepository stockRepository,
                                    OrderItemRepository orderItemRepository, ReviewAnalyticsService reviewAnalytics,
                                    StructuredAi ai, @Qualifier("aiObjectMapper") ObjectMapper objectMapper,
                                    @Value("${app.inventory.lead-time-days:3}") int leadTimeDays) {
        this.movementRepository = movementRepository;
        this.stockRepository = stockRepository;
        this.orderItemRepository = orderItemRepository;
        this.reviewAnalytics = reviewAnalytics;
        this.ai = ai;
        this.objectMapper = objectMapper;
        this.leadTimeDays = leadTimeDays;
    }

    public Report analyze() {
        LocalDate today = LocalDate.now();
        LocalDate firstWeekStart = today.minusDays(WEEKS * 7L - 1);

        // Verbrauch je Material und je Material/Lagerort
        List<StockMovement> issues = movementRepository.findByPostingDateGreaterThanEqualAndMovementCodeIn(
                firstWeekStart.atStartOfDay(), List.of("601", "602"));
        Map<Long, double[]> byProduct = new HashMap<>();
        Map<String, double[]> byStock = new HashMap<>();
        Map<Long, Product> products = new HashMap<>();
        for (StockMovement m : issues) {
            int week = (int) (ChronoUnit.DAYS.between(firstWeekStart, m.getPostingDate().toLocalDate()) / 7);
            if (week < 0 || week >= WEEKS) continue;
            double qty = "601".equals(m.getMovementCode()) ? m.getQuantity() : -m.getQuantity();
            byProduct.computeIfAbsent(m.getProduct().getId(), k -> new double[WEEKS])[week] += qty;
            var warehouse = m.getSourceWarehouse() != null ? m.getSourceWarehouse() : m.getTargetWarehouse();
            if (warehouse != null) {
                byStock.computeIfAbsent(m.getProduct().getId() + ":" + warehouse.getId(), k -> new double[WEEKS])[week] += qty;
            }
            products.put(m.getProduct().getId(), m.getProduct());
        }

        List<WarehouseStock> stocks = stockRepository.findAll();
        Map<Long, Integer> stockByProduct = new HashMap<>();
        for (WarehouseStock s : stocks) {
            stockByProduct.merge(s.getProduct().getId(), s.getQuantity(), Integer::sum);
            products.putIfAbsent(s.getProduct().getId(), s.getProduct());
        }
        Map<Long, Integer> backlog = orderItemRepository.sumBacklogQuantityByProduct().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue(), Integer::sum));

        List<DemandForecast> forecasts = forecasts(products, byProduct, stockByProduct, backlog, today);
        List<ReorderRecommendation> reorders = reorders(stocks, byStock, backlog, today);
        List<ProductAnomaly> qualityIssues = reviewAnalytics.productAnomalies(today.minusDays(29), today);
        List<InventoryAnomaly> anomalies = anomalies(products, byProduct, stocks, stockByProduct, backlog, qualityIssues);
        HealthScore health = health(stocks, byStock, products, today);

        Optional<Narrative> narrative = ai.call(narrativePrompt(reorders, anomalies, health, forecasts), Narrative.class);
        String summary = narrative.map(Narrative::summary).filter(s -> !s.isBlank())
                .orElseGet(() -> ruleSummary(reorders, anomalies, health));
        List<String> actions = narrative.map(Narrative::actions).filter(a -> a != null && !a.isEmpty())
                .orElseGet(() -> ruleActions(reorders, anomalies));

        return new Report(LocalDateTime.now(), summary, actions,
                narrative.isPresent() ? ReviewAnalyticsService.SOURCE_AI : ReviewAnalyticsService.SOURCE_RULES,
                "Verbrauch aus Warenausgängen (601 abzüglich Storno 602) der letzten %d Wochen, exponentiell geglättet; "
                        .formatted(WEEKS) + "Nachschub über Melde-/Höchstbestand je Lagerort, Wiederbeschaffungszeit %d Tage."
                        .formatted(leadTimeDays),
                forecasts, reorders, anomalies, health);
    }

    // ------------------------------------------------------------------
    // Bedarfsprognose
    // ------------------------------------------------------------------

    private List<DemandForecast> forecasts(Map<Long, Product> products, Map<Long, double[]> byProduct,
                                           Map<Long, Integer> stockByProduct, Map<Long, Integer> backlog, LocalDate today) {
        List<DemandForecast> result = new ArrayList<>();
        for (Product p : products.values()) {
            WeeklyDemand demand = new WeeklyDemand(byProduct.getOrDefault(p.getId(), new double[WEEKS]));
            int open = backlog.getOrDefault(p.getId(), 0);
            if (demand.total() <= 0 && open == 0) continue;
            double daily = demand.smoothedDaily();
            int stock = stockByProduct.getOrDefault(p.getId(), 0);
            Integer reach = daily > 0 ? (int) Math.floor(Math.max(0, stock - open) / daily) : null;
            result.add(new DemandForecast(p.getId(), p.getName(), p.getCategory(), stock, round2(daily),
                    (int) Math.round(daily * 7), (int) Math.round(daily * 14), (int) Math.round(daily * 30),
                    demand.trend(), demand.confidence(), reach,
                    reach != null && reach <= 60 ? today.plusDays(reach) : null, open));
        }
        result.sort(Comparator.comparing(DemandForecast::reachDays, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DemandForecast::productName));
        return result;
    }

    // ------------------------------------------------------------------
    // Nachschub
    // ------------------------------------------------------------------

    private List<ReorderRecommendation> reorders(List<WarehouseStock> stocks, Map<String, double[]> byStock,
                                                 Map<Long, Integer> backlog, LocalDate today) {
        // Rückstand dem Lagerort mit dem höchsten Verbrauch des Materials zuordnen
        Map<Long, WarehouseStock> backlogStock = new HashMap<>();
        for (WarehouseStock s : stocks) {
            WarehouseStock current = backlogStock.get(s.getProduct().getId());
            if (current == null || dailyDemand(byStock, s) > dailyDemand(byStock, current)) {
                backlogStock.put(s.getProduct().getId(), s);
            }
        }

        List<ReorderRecommendation> result = new ArrayList<>();
        for (WarehouseStock s : stocks) {
            double daily = dailyDemand(byStock, s);
            int open = backlogStock.get(s.getProduct().getId()) == s ? backlog.getOrDefault(s.getProduct().getId(), 0) : 0;
            int qty = s.getQuantity();
            boolean planned = s.getMaxStock() > 0;
            Integer reach = daily > 0 ? (int) Math.floor(Math.max(0, qty - open) / daily) : null;

            boolean belowReorderPoint = planned && qty <= s.getMinStock();
            boolean reachTooShort = planned && reach != null && reach <= leadTimeDays + 2;
            boolean stockoutWithBacklog = qty <= 0 && open > 0;
            if (!belowReorderPoint && !reachTooShort && !stockoutWithBacklog) continue;

            int coverage = (int) Math.ceil(daily * 14);
            int orderQty = planned ? Math.max(Math.max(s.getMaxStock() - qty, coverage - qty) + open, 1)
                    : Math.max(open + coverage - qty, 1);
            String urgency = qty <= 0 && (daily > 0 || open > 0) ? "CRITICAL"
                    : reach != null && reach <= leadTimeDays ? "HIGH"
                    : belowReorderPoint ? "MEDIUM" : "LOW";

            List<String> reasons = new ArrayList<>();
            reasons.add(planned ? "Bestand %d bei Meldebestand %d".formatted(qty, s.getMinStock())
                    : "Bestand %d, Lagerort nicht disponiert".formatted(qty));
            if (daily > 0) reasons.add("Verbrauch %s ST/Tag".formatted(fmt(daily)));
            if (reach != null) reasons.add("Reichweite %d Tage".formatted(reach));
            if (open > 0) reasons.add("Rückstand %d ST".formatted(open));

            BigDecimal price = s.getProduct().getValuationPrice() == null ? BigDecimal.ZERO : s.getProduct().getValuationPrice();
            LocalDate orderDate = "CRITICAL".equals(urgency) || "HIGH".equals(urgency) || reach == null
                    ? today : today.plusDays(Math.max(0, reach - leadTimeDays - 2));
            result.add(new ReorderRecommendation(s.getProduct().getId(), s.getProduct().getName(),
                    s.getWarehouse().getCode() + " " + s.getWarehouse().getName(), qty, s.getMinStock(), s.getMaxStock(),
                    round2(daily), open, orderQty, urgency, String.join(", ", reasons),
                    price.multiply(BigDecimal.valueOf(orderQty)).setScale(2, RoundingMode.HALF_UP), orderDate, reach));
        }
        List<String> order = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        result.sort(Comparator.comparing((ReorderRecommendation r) -> order.indexOf(r.urgency()))
                .thenComparing(ReorderRecommendation::daysUntilStockout, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private static double dailyDemand(Map<String, double[]> byStock, WarehouseStock s) {
        double[] weeks = byStock.get(s.getProduct().getId() + ":" + s.getWarehouse().getId());
        return weeks == null ? 0 : Math.max(0, new WeeklyDemand(weeks).smoothedDaily());
    }

    // ------------------------------------------------------------------
    // Auffälligkeiten
    // ------------------------------------------------------------------

    private List<InventoryAnomaly> anomalies(Map<Long, Product> products, Map<Long, double[]> byProduct,
                                             List<WarehouseStock> stocks, Map<Long, Integer> stockByProduct,
                                             Map<Long, Integer> backlog, List<ProductAnomaly> qualityIssues) {
        List<InventoryAnomaly> result = new ArrayList<>();

        // Qualitätsauffälligkeit aus den Bewertungen bei vorhandenem Bestand
        for (ProductAnomaly q : qualityIssues) {
            int stock = stockByProduct.getOrDefault(q.productId(), 0);
            if (stock <= 0) continue;
            result.add(new InventoryAnomaly(q.productId(), q.productName(), "alle Lagerorte", "QUALITY_RISK",
                    "HIGH".equals(q.severity()) ? "HIGH" : "MEDIUM",
                    "Kundenbewertungen auffällig (Ø %s Sterne aus %d Bewertungen) bei %d ST Bestand.".formatted(
                            fmt(q.avgRating()), q.reviews(), stock),
                    "Restbestand prüfen und bis zur Klärung ggf. sperren; Lieferanten informieren."));
        }

        // Ausverkauft trotz offener Aufträge
        backlog.forEach((productId, open) -> {
            if (open > 0 && stockByProduct.getOrDefault(productId, 0) <= 0 && products.containsKey(productId)) {
                result.add(new InventoryAnomaly(productId, products.get(productId).getName(), "alle Lagerorte",
                        "STOCKOUT_WITH_BACKLOG", "HIGH",
                        "Kein Bestand, aber %d ST in offenen Aufträgen nicht bestätigt.".formatted(open),
                        "Eilbestellung auslösen und Kunden über den Liefertermin informieren."));
            }
        });

        // Nachfragespitze: letzte Woche mindestens dreimal so hoch wie der Durchschnitt davor
        byProduct.forEach((productId, weeks) -> {
            double last = weeks[WEEKS - 1];
            double before = Arrays.stream(weeks, 0, WEEKS - 1).average().orElse(0);
            if (last >= 5 && before > 0 && last >= 3 * before && products.containsKey(productId)) {
                result.add(new InventoryAnomaly(productId, products.get(productId).getName(), "alle Lagerorte",
                        "DEMAND_SPIKE", last >= 5 * before ? "HIGH" : "MEDIUM",
                        "%s ST in den letzten 7 Tagen statt Ø %s ST pro Woche.".formatted(fmt(last), fmt(before)),
                        "Ursache klären (Aktion, Großkunde?) und Melde-/Höchstbestand anpassen."));
            }
        });

        for (WarehouseStock s : stocks) {
            Product p = s.getProduct();
            String warehouse = s.getWarehouse().getCode() + " " + s.getWarehouse().getName();
            // Überbestand gegenüber dem Höchstbestand
            if (s.getMaxStock() > 0 && s.getQuantity() > Math.max(s.getMaxStock() * 1.5, s.getMaxStock() + 2)) {
                BigDecimal value = p.getValuationPrice().multiply(BigDecimal.valueOf(s.getQuantity() - s.getMaxStock()));
                if (value.compareTo(BigDecimal.valueOf(300)) < 0) continue;
                result.add(new InventoryAnomaly(p.getId(), p.getName(), warehouse, "OVERSTOCK", "MEDIUM",
                        "Bestand %d bei Höchstbestand %d; gebundenes Kapital über Höchstbestand %s €.".formatted(
                                s.getQuantity(), s.getMaxStock(), value.setScale(0, RoundingMode.HALF_UP)),
                        "Umlagerung an einen Lagerort mit Bedarf prüfen oder Nachschub aussetzen."));
            }
        }

        // Ladenhüter: Bestand ohne Warenausgang in 12 Wochen; nur die fünf größten gebundenen Werte
        record SlowMover(Product product, int stock, BigDecimal value) {
        }
        List<SlowMover> slowMovers = new ArrayList<>();
        stockByProduct.forEach((productId, stock) -> {
            double[] weeks = byProduct.get(productId);
            Product p = products.get(productId);
            if (p == null || stock <= 0 || (weeks != null && Arrays.stream(weeks).sum() > 0)) return;
            BigDecimal value = p.getValuationPrice().multiply(BigDecimal.valueOf(stock));
            if (value.compareTo(BigDecimal.valueOf(1000)) >= 0) slowMovers.add(new SlowMover(p, stock, value));
        });
        slowMovers.stream().sorted(Comparator.comparing(SlowMover::value).reversed()).limit(5).forEach(m ->
                result.add(new InventoryAnomaly(m.product().getId(), m.product().getName(), "alle Lagerorte", "SLOW_MOVER",
                        m.value().compareTo(BigDecimal.valueOf(5000)) >= 0 ? "MEDIUM" : "LOW",
                        "%d ST (%s €) ohne Warenausgang in %d Wochen.".formatted(m.stock(),
                                m.value().setScale(0, RoundingMode.HALF_UP), WEEKS),
                        "Abverkauf fördern oder Sortiment prüfen.")));

        List<String> order = List.of("HIGH", "MEDIUM", "LOW");
        result.sort(Comparator.comparing(a -> order.indexOf(a.severity())));
        return result;
    }

    // ------------------------------------------------------------------
    // Kennzahlen
    // ------------------------------------------------------------------

    private HealthScore health(List<WarehouseStock> stocks, Map<String, double[]> byStock, Map<Long, Product> products,
                               LocalDate today) {
        List<WarehouseStock> planned = stocks.stream().filter(s -> s.getMaxStock() > 0).toList();
        long available = planned.stream().filter(s -> s.getQuantity() > s.getMinStock()).count();
        int availability = planned.isEmpty() ? 100 : (int) Math.round(100.0 * available / planned.size());

        // Umschlag: Verbrauchswert der letzten 12 Wochen hochgerechnet aufs Jahr / aktueller Bestandswert
        BigDecimal stockValue = stocks.stream().map(s -> s.getProduct().getValuationPrice().multiply(BigDecimal.valueOf(s.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double issuedValue = 0;
        for (WarehouseStock s : stocks) {
            double[] weeks = byStock.get(s.getProduct().getId() + ":" + s.getWarehouse().getId());
            if (weeks != null) issuedValue += Arrays.stream(weeks).sum() * s.getProduct().getValuationPrice().doubleValue();
        }
        double turnover = stockValue.signum() > 0 ? issuedValue * (52.0 / WEEKS) / stockValue.doubleValue() : 0;
        int turnoverScore = (int) Math.min(100, Math.round(turnover / 6.0 * 100));

        // Inventurgenauigkeit: Inventurdifferenzen (701/702) im Verhältnis zur bewegten Menge des letzten Jahres
        List<StockMovement> lastYear = movementRepository.findByPostingDateGreaterThanEqualAndMovementCodeIn(
                today.minusDays(365).atStartOfDay(), List.of("601", "701", "702"));
        double moved = lastYear.stream().filter(m -> "601".equals(m.getMovementCode())).mapToInt(StockMovement::getQuantity).sum();
        double differences = lastYear.stream().filter(m -> !"601".equals(m.getMovementCode())).mapToInt(StockMovement::getQuantity).sum();
        int accuracy = moved <= 0 ? 100 : (int) Math.max(0, Math.round(100 - differences / moved * 1000));

        int overall = (int) Math.round(0.4 * availability + 0.3 * turnoverScore + 0.3 * accuracy);
        String grade = overall >= 90 ? "A" : overall >= 80 ? "B" : overall >= 70 ? "C" : overall >= 60 ? "D" : "F";

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        (availability >= 90 ? strengths : weaknesses).add("Verfügbarkeit: %d %% der disponierten Lagerorte über Meldebestand"
                .formatted(availability));
        (turnoverScore >= 70 ? strengths : weaknesses).add("Lagerumschlag %s pro Jahr (Ziel 6)".formatted(fmt(turnover)));
        (accuracy >= 90 ? strengths : weaknesses).add("Inventurgenauigkeit %d %%".formatted(accuracy));
        return new HealthScore(overall, grade, availability, turnoverScore, accuracy, round2(turnover), strengths, weaknesses);
    }

    // ------------------------------------------------------------------
    // Texte
    // ------------------------------------------------------------------

    private String narrativePrompt(List<ReorderRecommendation> reorders, List<InventoryAnomaly> anomalies,
                                   HealthScore health, List<DemandForecast> forecasts) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("kennzahlen", health);
        facts.put("nachschubvorschläge", reorders.stream().limit(8).map(r -> Map.of("material", r.productName(),
                "lagerort", r.warehouseName(), "dringlichkeit", r.urgency(), "menge", r.recommendedOrderQuantity(),
                "begründung", r.reason())).toList());
        facts.put("anzahlNachschubvorschläge", reorders.size());
        facts.put("auffälligkeiten", anomalies.stream().limit(8).map(a -> Map.of("material", a.productName(),
                "art", a.anomalyType(), "schwere", a.severity(), "beschreibung", a.description())).toList());
        facts.put("steigendeNachfrage", forecasts.stream().filter(f -> "RISING".equals(f.trendDirection()))
                .limit(5).map(DemandForecast::productName).toList());
        try {
            return """
                Fasse die berechnete Bestandsanalyse für die Disposition zusammen.
                Nutze ausschließlich diese Fakten, nenne Materialien und Zahlen.

                Fakten:
                %s

                Ausgabe:
                - summary: 2 bis 3 Sätze zur Lage
                - actions: 3 bis 5 konkrete nächste Schritte, wichtigste zuerst
                """.formatted(objectMapper.writeValueAsString(facts));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String ruleSummary(List<ReorderRecommendation> reorders, List<InventoryAnomaly> anomalies,
                                      HealthScore health) {
        long critical = reorders.stream().filter(r -> "CRITICAL".equals(r.urgency()) || "HIGH".equals(r.urgency())).count();
        long quality = anomalies.stream().filter(a -> "QUALITY_RISK".equals(a.anomalyType())).count();
        return "Gesamtbewertung %s (%d/100). %d Nachschubvorschläge, davon %d dringend. %d Auffälligkeiten%s."
                .formatted(health.grade(), health.overallScore(), reorders.size(), critical, anomalies.size(),
                        quality > 0 ? ", darunter %d Material(ien) mit Qualitätsproblemen laut Kundenbewertungen".formatted(quality) : "");
    }

    private static List<String> ruleActions(List<ReorderRecommendation> reorders, List<InventoryAnomaly> anomalies) {
        List<String> actions = new ArrayList<>();
        anomalies.stream().filter(a -> "HIGH".equals(a.severity())).limit(2)
                .forEach(a -> actions.add(a.productName() + ": " + a.suggestedAction()));
        reorders.stream().filter(r -> "CRITICAL".equals(r.urgency()) || "HIGH".equals(r.urgency())).limit(3)
                .forEach(r -> actions.add("%s bestellen: %d ST für %s (%s)".formatted(r.productName(),
                        r.recommendedOrderQuantity(), r.warehouseName(), r.reason())));
        if (actions.isEmpty() && !reorders.isEmpty()) {
            ReorderRecommendation r = reorders.get(0);
            actions.add("%s für %s nachbestellen (%d ST)".formatted(r.productName(), r.warehouseName(), r.recommendedOrderQuantity()));
        }
        return actions;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String fmt(double v) {
        return String.format(Locale.GERMANY, "%.1f", v);
    }
}
