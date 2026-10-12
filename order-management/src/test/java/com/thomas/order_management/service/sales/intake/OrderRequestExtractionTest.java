package com.thomas.order_management.service.sales.intake;

import com.thomas.order_management.dto.sales.OrderRequestDtos.*;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Auslesen ohne KI und Zuordnung zu Stammdaten, ohne Datenbank.
 */
@DisplayName("Auftragseingang: Auslesen und Zuordnen")
class OrderRequestExtractionTest {

    /** Sonntag, damit relative Angaben eindeutig sind */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    private static final List<Product> CATALOG = List.of(
            product(1L, "200011", "IKEA TRÅDFRI Smarte Beleuchtung Set"),
            product(2L, "200012", "Miele Triflex HX2 Akkusauger"),
            product(3L, "200013", "iRobot Roomba j7+ Saugroboter"),
            product(4L, "200014", "Weber Genesis II E-310 Gasgrill"),
            product(5L, "200015", "Solar-Gartenleuchten (8er-Set)"),
            product(6L, "200016", "Philips Hue Beleuchtung Starter-Set"));

    private static final List<Customer> CUSTOMERS = List.of(
            customer(1L, "100001", "Greta", "Gärtner", "greta.gaertner@beispiel.de", "Augsburg"),
            customer(2L, "100002", "Jonas", "Gärtner", "jonas.gaertner@beispiel.de", "Kiel"),
            customer(3L, "100003", "Mia", "Schulz", "mia.schulz@firma.de", "Bremen"));

    @Test
    @DisplayName("Strukturierte E-Mail: Nummern, Menge, Termin und Bestellnummer")
    void shouldReadStructuredEmail() {
        Extraction e = RuleBasedOrderExtractor.extract("""
                Sehr geehrte Damen und Herren,

                hiermit bestelle ich zur Lieferung bis spätestens 23.10.2026:

                200012  Miele Triflex HX2 Akkusauger  – 2 Stück
                200014  Weber Genesis II E-310 Gasgrill  – 1 Stück

                Kundennummer: 100001
                Bestellnummer: B-2026-384

                Mit freundlichen Grüßen
                Greta Gärtner
                86150 Augsburg
                """, "Greta Gärtner <greta.gaertner@beispiel.de>", TODAY);

        assertThat(e.customer().customerNumber()).isEqualTo("100001");
        assertThat(e.customer().email()).isEqualTo("greta.gaertner@beispiel.de");
        assertThat(e.customer().name()).isEqualTo("Greta Gärtner");
        assertThat(e.customer().city()).isEqualTo("Augsburg");
        assertThat(e.customerReference()).isEqualTo("B-2026-384");
        assertThat(e.requestedDeliveryDate()).isEqualTo("2026-10-23");
        assertThat(e.items()).extracting(Line::articleNumber, Line::quantity).containsExactly(
                tuple("200012", 2), tuple("200014", 1));

        CustomerProposal customer = OrderRequestMatcher.matchCustomer(e.customer(), CUSTOMERS);
        assertThat(customer.match().customerId()).isEqualTo(1L);
        assertThat(customer.match().confidence()).isEqualTo(Confidence.HIGH);
        List<ItemProposal> items = OrderRequestMatcher.matchItems(e.items(), CATALOG);
        assertThat(items).allSatisfy(i -> assertThat(i.match().confidence()).isEqualTo(Confidence.HIGH));
        assertThat(items.get(0).match().productId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Formlose E-Mail: Mengen in verschiedenen Schreibweisen, Wochentag, Hinweis und unbekannter Artikel")
    void shouldReadInformalEmail() {
        Extraction e = RuleBasedOrderExtractor.extract("""
                Hallo zusammen,

                wir bräuchten bis nächsten Freitag:

                - 4x IKEA Beleuchtung
                - Miele Akkusauger, 2 Stück
                - 3 Kartons Druckerpapier A4

                Unsere Bestellnummer ist GAERTNER/284. Bitte vor der Anlieferung kurz anrufen.

                Danke und viele Grüße
                Greta Gärtner
                """, "Greta Gärtner <greta.gaertner@web.de>", TODAY);

        assertThat(e.requestedDeliveryDate()).isEqualTo("2026-10-16");
        assertThat(e.customerReference()).isEqualTo("GAERTNER/284");
        assertThat(e.notes()).contains("anrufen");
        assertThat(e.items()).extracting(Line::description, Line::quantity, Line::unit).containsExactly(
                tuple("IKEA Beleuchtung", 4, "Stück"),
                tuple("Miele Akkusauger", 2, "Stück"),
                tuple("Druckerpapier A4", 3, "Karton"));

        // E-Mail-Domain unbekannt, aber Name und lokaler Teil passen; der Namensvetter ist nur Alternative
        CustomerProposal customer = OrderRequestMatcher.matchCustomer(e.customer(), CUSTOMERS);
        assertThat(customer.match().customerId()).isEqualTo(1L);
        assertThat(customer.alternatives()).extracting(CustomerCandidate::customerId).contains(2L);

        List<ItemProposal> items = OrderRequestMatcher.matchItems(e.items(), CATALOG);
        assertThat(items.get(0).match().productId()).isEqualTo(1L);
        assertThat(items.get(0).match().confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(items.get(1).match().productId()).isEqualTo(2L);
        assertThat(items.get(2).match()).isNull();
    }

    @Test
    @DisplayName("Tabellenzeilen aus einem PDF und Liefertermin als Kalenderwoche")
    void shouldReadTableRowsAndCalendarWeek() {
        Extraction e = RuleBasedOrderExtractor.extract("""
                Schulz & Partner GmbH
                Hafenstraße 4 · 28195 Bremen
                Einkauf: Mia Schulz · mia.schulz@firma.de
                Bestellung Nr. PO-45284
                Gewünschter Liefertermin: KW 44/2026
                Pos Art.-Nr. Bezeichnung Menge Einheit
                10 200013 iRobot Roomba j7+ Saugroboter 5 ST
                20 200015 Solar-Gartenleuchten (8er-Set) 12 ST
                """, null, TODAY);

        assertThat(e.customer().company()).isEqualTo("Schulz & Partner GmbH");
        assertThat(e.customer().email()).isEqualTo("mia.schulz@firma.de");
        assertThat(e.customerReference()).isEqualTo("PO-45284");
        assertThat(e.requestedDeliveryDate()).isEqualTo("2026-10-26");
        assertThat(e.items()).extracting(Line::articleNumber, Line::quantity).containsExactly(
                tuple("200013", 5), tuple("200015", 12));
        assertThat(OrderRequestMatcher.matchCustomer(e.customer(), CUSTOMERS).match().customerId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Vorschlag der KI aus dem Katalog: bestätigt durch die Bezeichnung eindeutig, sonst zu prüfen")
    void shouldWeighAiCatalogSuggestion() {
        List<ItemProposal> items = OrderRequestMatcher.matchItems(List.of(
                new Line(null, "Saugroboter von iRobot", 1, "Stück", "200013"),
                new Line(null, "Kaffeemaschine", 1, "Stück", "200014")), CATALOG);

        assertThat(items.get(0).match().productId()).isEqualTo(3L);
        assertThat(items.get(0).match().confidence()).isEqualTo(Confidence.HIGH);
        assertThat(items.get(1).match().productId()).isEqualTo(4L);
        assertThat(items.get(1).match().confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(items.get(1).match().reason()).contains("weicht ab");
    }

    @Test
    @DisplayName("Angaben der KI werden gegen den Originaltext geprüft")
    void shouldVerifyAiOutputAgainstText() {
        String text = "Hallo, bitte 4x IKEA Beleuchtung an die hinterlegte Adresse. Bestellnummer B-77. Gruß Greta";
        Extraction e = OrderRequestExtractor.verified(new Extraction(
                new Party("Greta", "", null, "null", null), "B-77", "2026-10-16", "an die hinterlegte Adresse", "null",
                List.of(new Line("200011", "IKEA Beleuchtung", 4, "Stück", null),
                        new Line(null, null, 1, null, null))), text);

        assertThat(e.customer().company()).isNull();
        assertThat(e.customer().customerNumber()).isNull();
        assertThat(e.deliveryAddress()).isNull();
        assertThat(e.notes()).isNull();
        assertThat(e.customerReference()).isEqualTo("B-77");
        // Die Nummer steht nicht im Text: kein Beleg aus dem Dokument, aber als Katalogvorschlag weiter nutzbar
        assertThat(e.items()).singleElement().satisfies(l -> {
            assertThat(l.articleNumber()).isNull();
            assertThat(l.catalogMaterialNumber()).isEqualTo("200011");
        });
    }

    @Test
    @DisplayName("Ähnliche Bezeichnungen gelten als mehrdeutig")
    void shouldFlagAmbiguousMatches() {
        ItemProposal item = OrderRequestMatcher.matchItems(List.of(new Line(null, "Beleuchtung Set", 1, null, null)), CATALOG).get(0);

        assertThat(item.match().confidence()).isNotEqualTo(Confidence.HIGH);
        assertThat(item.alternatives()).isNotEmpty();
    }

    @Test
    @DisplayName("Unscharfer Vergleich toleriert Umlaute, Tippfehler und Wortteile")
    void shouldCompareFuzzy() {
        assertThat(TextSimilarity.score("Gaertner Grill", "Gärtner Gasgrill")).isGreaterThan(0.8);
        assertThat(TextSimilarity.score("Akkusaugr Miele", "Miele Triflex HX2 Akkusauger")).isGreaterThan(0.75);
        assertThat(TextSimilarity.score("Druckerpapier", "Miele Triflex HX2 Akkusauger")).isZero();
        assertThat(OrderRequestSamples.paraphrase("IKEA TRÅDFRI Smarte Beleuchtung Set")).isEqualTo("IKEA Beleuchtung");
        assertThat(OrderRequestSamples.paraphrase("Weber Genesis II E-310 Gasgrill")).isEqualTo("Weber Gasgrill");
    }

    @Test
    @DisplayName("E-Mail-Dateien in quoted-printable werden dekodiert")
    void shouldDecodeQuotedPrintable() {
        String eml = "Content-Transfer-Encoding: quoted-printable\n\nGr=C3=BC=C3=9Fe und sch=\nönen Tag";
        assertThat(OrderRequestService.decodeQuotedPrintable(eml)).contains("Grüße und schönen Tag");
    }

    // ------------------------------------------------------------------

    private static Product product(Long id, String number, String name) {
        Product p = new Product(name, null, new BigDecimal("100.00"), 0);
        p.setId(id);
        p.setMaterialNumber(number);
        return p;
    }

    private static Customer customer(Long id, String number, String first, String last, String email, String city) {
        Customer c = new Customer(first, last, email);
        c.setId(id);
        c.setCustomerNumber(number);
        c.setCity(city);
        return c;
    }
}
