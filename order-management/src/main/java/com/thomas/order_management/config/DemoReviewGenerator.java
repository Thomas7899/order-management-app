package com.thomas.order_management.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.Product;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Erzeugt realistische Demo-Bewertungen aus einem eingecheckten Textbaukasten
 * ({@code demo/review-corpus.json}), ohne Sprachmodell.
 *
 * <p>Die Texte passen zur Warengruppe, Sterne und Text passen zueinander, und es gibt Zeitverläufe
 * mit bekannten Mustern, an denen sich die Auswertung messen lässt:</p>
 * <ul>
 *   <li><b>Qualitätsvorfall:</b> ein Material bekommt in den letzten 10 Tagen viele 1–2-Sterne-Bewertungen
 *       nach einem Firmware-Update (vorher gut bewertet)</li>
 *   <li><b>Verschlechterung:</b> die "absteigenden" Materialien werden über die letzten 90 Tage schlechter</li>
 *   <li><b>Verbesserung:</b> die "aufsteigenden" Materialien werden in den letzten 45 Tagen besser</li>
 *   <li><b>Lieferprobleme:</b> vor 35–56 Tagen häufen sich Beschwerden über die Lieferung, danach nicht mehr</li>
 * </ul>
 */
public class DemoReviewGenerator {

    /** Entwurf einer Bewertung; orderItemId ist bei verifizierten Käufen gesetzt. */
    public record Draft(Product product, Customer customer, Long orderItemId, int rating, String comment,
                        LocalDateTime createdAt) {
    }

    /** Fakturierter Kauf als Grundlage einer verifizierten Bewertung. */
    public record Purchase(Long productId, Long customerId, Long orderItemId, LocalDateTime orderDate) {
    }

    static final int BASE_REVIEWS = 680;
    static final int INCIDENT_REVIEWS = 18;
    static final int INCIDENT_DAYS = 10;
    static final int DECLINE_DAYS = 90;
    static final int RISE_DAYS = 45;
    static final int DELIVERY_PROBLEM_FROM_DAYS_AGO = 56;
    static final int DELIVERY_PROBLEM_TO_DAYS_AGO = 35;
    static final int DELIVERY_COMPLAINTS = 12;

    private static final String[] ASPECTS = {"FUNCTION", "QUALITY", "DURABILITY", "USABILITY", "DESIGN"};

    private final JsonNode corpus;
    private final Random random;
    private final LocalDate today;
    private final Map<Long, Double> baseQuality = new HashMap<>();

    public DemoReviewGenerator(Random random, LocalDate today) {
        this.random = random;
        this.today = today;
        try (InputStream in = new ClassPathResource("demo/review-corpus.json").getInputStream()) {
            this.corpus = JsonMapper.shared().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Textbaukasten für Demo-Bewertungen nicht lesbar", e);
        }
    }

    /** Material mit dem Qualitätsvorfall: das smarte Beleuchtungs-Set, sonst das 11. Material. */
    public static Product incidentProduct(List<Product> products) {
        return products.stream().filter(p -> p.getName().contains("TRÅDFRI")).findFirst()
                .orElse(products.get(Math.min(10, products.size() - 1)));
    }

    public List<Draft> generate(List<Product> products, List<Customer> customers, List<Purchase> purchases,
                                Set<Long> trendingUp, Set<Long> trendingDown) {
        Map<Long, Product> productById = new HashMap<>();
        products.forEach(p -> productById.put(p.getId(), p));
        Map<Long, Customer> customerById = new HashMap<>();
        customers.forEach(c -> customerById.put(c.getId(), c));
        Product incident = incidentProduct(products);
        products.forEach(p -> baseQuality.put(p.getId(), p.getId().equals(incident.getId()) ? 4.4
                : clamp(4.1 + 0.35 * random.nextGaussian(), 3.3, 4.7)));

        List<Draft> drafts = new ArrayList<>();

        // Grundbestand: überwiegend verifizierte Käufe, ergänzt um freie Bewertungen
        for (int i = 0; i < BASE_REVIEWS; i++) {
            Product product;
            Customer customer;
            Long orderItemId = null;
            LocalDateTime date;
            Purchase purchase = !purchases.isEmpty() && random.nextDouble() < 0.8 ? pickPurchase(purchases) : null;
            if (purchase != null && productById.containsKey(purchase.productId()) && customerById.containsKey(purchase.customerId())) {
                product = productById.get(purchase.productId());
                customer = customerById.get(purchase.customerId());
                orderItemId = purchase.orderItemId();
                date = reviewDate(purchase);
            } else {
                product = products.get(random.nextInt(products.size()));
                customer = customers.get(random.nextInt(customers.size()));
                date = randomDate(product);
            }
            int rating = rating(product, date.toLocalDate(), incident, trendingUp, trendingDown);
            drafts.add(withDeliveryProblem(new Draft(product, customer, orderItemId, rating,
                    compose(product, rating, null), date)));
        }

        // Qualitätsvorfall: viele schlechte Bewertungen in den letzten Tagen
        for (int i = 0; i < INCIDENT_REVIEWS; i++) {
            int rating = random.nextDouble() < 0.7 ? 1 : 2;
            StringBuilder text = new StringBuilder();
            if (random.nextDouble() < 0.3) text.append(pick(corpus.path("openers").path("NEG"))).append(' ');
            text.append(pick(corpus.path("scenarios").path("incident")));
            if (random.nextDouble() < 0.3) text.append(' ').append(pick(corpus.path("scenarios").path("incidentService")));
            drafts.add(new Draft(incident, customers.get(random.nextInt(customers.size())), null, rating,
                    text.toString(), daysAgo(random.nextInt(INCIDENT_DAYS))));
        }

        // Verschlechterung: zusätzliche Kritik an der aktuellen Charge
        for (Product product : products) {
            if (!trendingDown.contains(product.getId()) || product.getId().equals(incident.getId())) continue;
            for (int i = 0; i < 4; i++) {
                int rating = random.nextDouble() < 0.6 ? 2 : 3;
                String text = pick(corpus.path("scenarios").path("decline")) + " "
                        + pick(aspects(product).path(random.nextBoolean() ? "QUALITY" : "DURABILITY").path("NEG"));
                drafts.add(new Draft(product, customers.get(random.nextInt(customers.size())), null, rating, text,
                        daysAgo(random.nextInt(60))));
            }
        }

        // Verbesserung: zusätzliche sehr gute Bewertungen in den letzten Wochen
        for (Product product : products) {
            if (!trendingUp.contains(product.getId()) || product.getId().equals(incident.getId())) continue;
            for (int i = 0; i < 3; i++) {
                int rating = random.nextDouble() < 0.75 ? 5 : 4;
                String prefix = random.nextDouble() < 0.4 ? pick(corpus.path("scenarios").path("rising")) + " " : "";
                drafts.add(new Draft(product, customers.get(random.nextInt(customers.size())), null, rating,
                        prefix + compose(product, rating, null), daysAgo(random.nextInt(30))));
            }
        }

        // Lieferprobleme: zusätzliche Beschwerden im betroffenen Zeitraum, über alle Warengruppen verteilt
        for (int i = 0; i < DELIVERY_COMPLAINTS; i++) {
            Product product = products.get(random.nextInt(products.size()));
            if (product.getId().equals(incident.getId())) continue;
            int rating = random.nextDouble() < 0.6 ? 2 : 3;
            int daysAgo = DELIVERY_PROBLEM_TO_DAYS_AGO
                    + random.nextInt(DELIVERY_PROBLEM_FROM_DAYS_AGO - DELIVERY_PROBLEM_TO_DAYS_AGO + 1);
            String text = pick(corpus.path("scenarios").path("deliveryIncident"))
                    + (random.nextBoolean() ? " " + pick(corpus.path("generic").path("SERVICE").path("NEG")) : "");
            drafts.add(new Draft(product, customers.get(random.nextInt(customers.size())), null, rating, text,
                    daysAgo(daysAgo)));
        }

        drafts.sort(Comparator.comparing(Draft::createdAt));
        return drafts;
    }

    // ------------------------------------------------------------------
    // Sterne
    // ------------------------------------------------------------------

    private int rating(Product product, LocalDate date, Product incident, Set<Long> up, Set<Long> down) {
        long daysAgo = ChronoUnit.DAYS.between(date, today);
        double q = baseQuality.getOrDefault(product.getId(), 4.0);
        if (product.getId().equals(incident.getId())) {
            // Vor dem Vorfall gut bewertet; die Vorfall-Bewertungen kommen separat dazu
            q = 4.4;
        } else if (down.contains(product.getId())) {
            // Linear von 4,0 auf 2,4 über die letzten 90 Tage
            double progress = daysAgo >= DECLINE_DAYS ? 0 : 1 - (double) daysAgo / DECLINE_DAYS;
            q = 4.0 - 1.6 * progress;
        } else if (up.contains(product.getId())) {
            double progress = daysAgo >= RISE_DAYS ? 0 : 1 - (double) daysAgo / RISE_DAYS;
            q = 3.6 + 1.0 * progress;
        }
        int rating = (int) Math.round(q + 0.8 * random.nextGaussian());
        return Math.max(1, Math.min(5, rating));
    }

    // ------------------------------------------------------------------
    // Texte
    // ------------------------------------------------------------------

    String compose(Product product, int rating, String forcedSentence) {
        String tone = rating >= 4 ? "POS" : rating == 3 ? "NEU" : "NEG";
        List<String> sentences = new ArrayList<>();
        if (random.nextDouble() < 0.45) sentences.add(pick(corpus.path("openers").path(tone)));

        Set<String> used = new HashSet<>();
        if (rating >= 4) {
            sentences.add(aspectSentence(product, "POS", used));
            if (random.nextDouble() < 0.6) sentences.add(aspectSentence(product, "POS", used));
            if (rating == 4 && random.nextDouble() < 0.35) {
                sentences.add("Einziger Kritikpunkt: " + aspectSentence(product, "NEG", used));
            }
        } else if (rating == 3) {
            double r = random.nextDouble();
            if (r < 0.5) {
                sentences.add(aspectSentence(product, "POS", used));
                sentences.add("Allerdings: " + aspectSentence(product, "NEG", used));
            } else if (r < 0.8) {
                sentences.add(aspectSentence(product, "NEU", used));
                sentences.add(aspectSentence(product, "NEG", used));
            } else {
                sentences.add(aspectSentence(product, "NEU", used));
                sentences.add(aspectSentence(product, "POS", used));
            }
        } else {
            sentences.add(aspectSentence(product, "NEG", used));
            if (random.nextDouble() < 0.55) sentences.add(aspectSentence(product, "NEG", used));
            if (rating == 1 && random.nextDouble() < 0.3 && !used.contains("SERVICE")) {
                sentences.add(pick(corpus.path("generic").path("SERVICE").path("NEG")));
            }
        }
        if (forcedSentence != null) sentences.add(forcedSentence);
        if (random.nextDouble() < 0.35) sentences.add(pick(corpus.path("closers").path(tone)));
        return String.join(" ", sentences);
    }

    /** Satz zu einem noch nicht verwendeten Aspekt; Defekte und Service tauchen eher in Kritik auf. */
    private String aspectSentence(Product product, String tone, Set<String> used) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("FUNCTION", 3.0);
        weights.put("QUALITY", 2.0);
        weights.put("DURABILITY", "NEG".equals(tone) ? 3.0 : 0.8);
        weights.put("USABILITY", 1.5);
        weights.put("DESIGN", 1.0);
        weights.put("PRICE", 1.0);
        weights.put("DELIVERY", 0.8);
        weights.put("SERVICE", "NEG".equals(tone) ? 0.7 : 0.3);
        used.forEach(weights::remove);

        String aspect = weighted(weights);
        used.add(aspect);
        JsonNode node = Arrays.asList(ASPECTS).contains(aspect)
                ? aspects(product).path(aspect).path(tone)
                : corpus.path("generic").path(aspect).path(tone);
        return pick(node);
    }

    private JsonNode aspects(Product product) {
        JsonNode node = corpus.path("categories").path(product.getCategory() == null ? "" : product.getCategory());
        return node.isMissingNode() ? corpus.path("categories").path("Elektronik") : node;
    }

    /** Während der Lieferprobleme bekommen viele Bewertungen eine Beschwerde über die Lieferung und einen Stern weniger. */
    private Draft withDeliveryProblem(Draft draft) {
        long daysAgo = ChronoUnit.DAYS.between(draft.createdAt().toLocalDate(), today);
        if (daysAgo > DELIVERY_PROBLEM_FROM_DAYS_AGO || daysAgo < DELIVERY_PROBLEM_TO_DAYS_AGO || random.nextDouble() >= 0.25) {
            return draft;
        }
        int rating = random.nextBoolean() ? Math.max(1, draft.rating() - 1) : draft.rating();
        String comment = draft.comment() + " " + pick(corpus.path("scenarios").path("deliveryIncident"));
        return new Draft(draft.product(), draft.customer(), draft.orderItemId(), rating, comment, draft.createdAt());
    }

    // ------------------------------------------------------------------
    // Zufall
    // ------------------------------------------------------------------

    /** Kauf, dessen Bewertung (3 bis 27 Tage nach dem Kauf) nicht in der Zukunft läge. */
    private Purchase pickPurchase(List<Purchase> purchases) {
        for (int attempt = 0; attempt < 10; attempt++) {
            Purchase p = purchases.get(random.nextInt(purchases.size()));
            if (!p.orderDate().toLocalDate().plusDays(3).isAfter(today)) return p;
        }
        return null;
    }

    private LocalDateTime reviewDate(Purchase purchase) {
        long maxDelay = Math.min(27, ChronoUnit.DAYS.between(purchase.orderDate().toLocalDate(), today));
        long delay = 3 + random.nextInt((int) Math.max(1, maxDelay - 2));
        return purchase.orderDate().toLocalDate().plusDays(Math.min(delay, maxDelay)).atTime(8 + random.nextInt(13), random.nextInt(60));
    }

    /** Datum im letzten Jahr; Gartenartikel werden im Frühjahr und Sommer häufiger bewertet. */
    private LocalDateTime randomDate(Product product) {
        for (int attempt = 0; attempt < 10; attempt++) {
            LocalDate date = today.minusDays(random.nextInt(365));
            boolean season = date.getMonthValue() >= 4 && date.getMonthValue() <= 9;
            if (!"Garten & Outdoor".equals(product.getCategory()) || season || random.nextDouble() < 0.35) {
                return date.atTime(8 + random.nextInt(13), random.nextInt(60));
            }
        }
        return today.minusDays(random.nextInt(365)).atTime(12, 0);
    }

    private LocalDateTime daysAgo(int days) {
        return today.minusDays(days).atTime(8 + random.nextInt(13), random.nextInt(60));
    }

    private String weighted(Map<String, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double r = random.nextDouble() * total;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            r -= e.getValue();
            if (r <= 0) return e.getKey();
        }
        return weights.keySet().iterator().next();
    }

    private String pick(JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            throw new IllegalStateException("Leerer Abschnitt im Textbaukasten für Demo-Bewertungen");
        }
        return array.get(random.nextInt(array.size())).asString();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
