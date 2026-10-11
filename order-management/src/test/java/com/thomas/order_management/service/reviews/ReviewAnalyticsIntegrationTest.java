package com.thomas.order_management.service.reviews;

import com.thomas.order_management.config.DemoReviewGenerator;
import com.thomas.order_management.dto.reviews.ReviewInsightDtos.*;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.ProductReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prüft die Auswertung gegen die bekannten Muster der Demo-Bewertungen (ohne KI, regelbasiert eingeordnet).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Auswertung der Bewertungen")
class ReviewAnalyticsIntegrationTest {

    private static final List<String> CATEGORIES = List.of("Elektronik", "Möbel", "Beleuchtung", "Bürobedarf",
            "Sport & Fitness", "Haushalt & Küche", "Garten & Outdoor");

    private static boolean seeded;
    private static Product incident;
    private static final Set<Long> rising = new HashSet<>();
    private static final Set<Long> declining = new HashSet<>();

    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductReviewRepository reviewRepository;
    @Autowired private ReviewInsightService insightService;
    @Autowired private ReviewAnalyticsService analytics;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void seed() {
        if (seeded) return;
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        List<Product> products = new ArrayList<>();
        for (String category : CATEGORIES) {
            for (int i = 0; i < 3; i++) {
                String name = category.equals("Beleuchtung") && i == 0 ? "IKEA TRÅDFRI Testset " + suffix
                        : category + " Testmaterial " + i + " " + suffix;
                Product p = new Product(name, null, new BigDecimal("100.00"), 0);
                p.setMaterialNumber("A" + suffix + products.size());
                p.setCategory(category);
                products.add(productRepository.save(p));
            }
        }
        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Customer c = new Customer("Kunde" + i, "Test", "kunde" + i + "." + suffix + "@example.com");
            c.setCustomerNumber("K" + suffix + i);
            customers.add(customerRepository.save(c));
        }
        incident = DemoReviewGenerator.incidentProduct(products);
        products.stream().filter(p -> !p.equals(incident)).limit(3).forEach(p -> rising.add(p.getId()));
        products.stream().filter(p -> !p.equals(incident)).skip(10).limit(3).forEach(p -> declining.add(p.getId()));

        List<DemoReviewGenerator.Draft> drafts = new DemoReviewGenerator(new Random(7), today)
                .generate(products, customers, List.of(), rising, declining);
        reviewRepository.saveAll(drafts.stream().map(d -> {
            ProductReview r = new ProductReview();
            r.setProduct(d.product());
            r.setCustomer(d.customer());
            r.setRating(d.rating());
            r.setComment(d.comment());
            r.setCreatedAt(d.createdAt());
            return r;
        }).toList());
        insightService.classifyMissingWithRules();
        seeded = true;
    }

    @Test
    @DisplayName("Der Qualitätsvorfall der letzten Tage ist die wichtigste Auffälligkeit")
    void shouldDetectQualityIncident() {
        AnomalyReport report = analytics.anomalies(today.minusDays(13), today);

        assertThat(report.products()).isNotEmpty();
        ProductAnomaly top = report.products().get(0);
        assertThat(top.productId()).isEqualTo(incident.getId());
        assertThat(top.severity()).isEqualTo("HIGH");
        assertThat(top.avgRating()).isLessThanOrEqualTo(2.0);
        assertThat(top.baselineAvgRating()).isGreaterThanOrEqualTo(3.5);
        assertThat(top.topProblems()).anyMatch(p -> p.startsWith("Funktion") || p.startsWith("Haltbarkeit"));
        assertThat(top.evidence()).isNotEmpty();
        assertThat(report.narrativeSource()).isEqualTo(ReviewAnalyticsService.SOURCE_RULES);
        // Materialien mit positivem Trend dürfen nicht als auffällig gelten
        assertThat(report.products()).noneMatch(a -> rising.contains(a.productId()));
    }

    @Test
    @DisplayName("Die Häufung von Lieferproblemen wird im betroffenen Zeitraum als Themen-Auffälligkeit erkannt")
    void shouldDetectDeliveryProblemsInTheirWindow() {
        AnomalyReport report = analytics.anomalies(today.minusDays(56), today.minusDays(35));

        assertThat(report.themes()).extracting(t -> t.theme().code()).contains("DELIVERY");
    }

    @Test
    @DisplayName("Der Trend vergleicht mit der Vorperiode und findet Verbesserungen und Verschlechterungen")
    void shouldCompareWithPreviousPeriod() {
        TrendReport report = analytics.trend(today.minusDays(29), today, null, null);

        assertThat(report.previousEnd()).isEqualTo(today.minusDays(30));
        assertThat(report.current().reviews()).isPositive();
        assertThat(report.previous().reviews()).isPositive();
        // Die Lieferprobleme lagen in der Vorperiode und sind abgeklungen
        assertThat(report.themes()).anyMatch(t -> t.theme().code().equals("DELIVERY") && t.direction().equals("IMPROVING"));
        assertThat(report.declining()).anyMatch(m -> declining.contains(m.productId()) || m.productId().equals(incident.getId()));
        assertThat(report.improving()).anyMatch(m -> rising.contains(m.productId()));
        assertThat(report.summary()).isNotBlank();
        assertThat(report.recommendations()).isNotEmpty();
        assertThat(report.evidence()).isNotEmpty();
    }

    @Test
    @DisplayName("Trend je Material zeigt den Einbruch beim Qualitätsvorfall")
    void shouldShowIncidentForSingleProduct() {
        TrendReport report = analytics.trend(today.minusDays(13), today, incident.getId(), null);

        assertThat(report.scope()).isEqualTo("PRODUCT");
        assertThat(report.current().avgRating()).isLessThan(report.previous().reviews() > 0 ? report.previous().avgRating() : 5.0);
        assertThat(report.current().negativeShare()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    @DisplayName("Stimmungsbericht ist konsistent und braucht kein Sprachmodell")
    void shouldBuildConsistentSentimentReport() {
        SentimentReport report = analytics.sentiment(today.minusDays(89), today, null, null);

        long total = report.sentiments().stream().mapToLong(Count::count).sum();
        assertThat(total).isEqualTo(report.current().reviews());
        assertThat(report.timelineGranularity()).isEqualTo("WEEK");
        assertThat(report.timeline().stream().mapToLong(SentimentPoint::reviews).sum()).isEqualTo(report.current().reviews());
        assertThat(report.criticalReviews()).isNotEmpty();
        assertThat(report.themes()).isNotEmpty();
        assertThat(report.classification().aiAvailable()).isFalse();
    }
}
