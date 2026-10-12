package com.thomas.order_management.service.sales.intake;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.thomas.order_management.config.DemoReviewGenerator;
import com.thomas.order_management.dto.sales.OrderRequestDtos.Sample;
import com.thomas.order_management.dto.sales.OrderRequestDtos.SampleText;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.ProductRepository;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Beispielbestellungen für die Demo, gebaut aus den aktuellen Stammdaten: eine strukturierte E-Mail mit
 * Nummern, eine formlose E-Mail mit umschriebenen Bezeichnungen und eine Bestellung als PDF.
 */
@Component
public class OrderRequestSamples {

    public static final String STRUCTURED = "email-strukturiert";
    public static final String INFORMAL = "email-formlos";
    public static final String PDF = "bestellung-pdf";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Color LINE = new Color(0xD9, 0xD9, 0xD9);

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public OrderRequestSamples(CustomerRepository customerRepository, ProductRepository productRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    public List<Sample> list() {
        return List.of(
                new Sample(STRUCTURED, "E-Mail mit Kunden- und Artikelnummern",
                        "Bestellung eines Stammkunden mit Nummern und festem Liefertermin", "TEXT"),
                new Sample(INFORMAL, "Formlose E-Mail",
                        "Ohne Nummern, umschriebene Bezeichnungen, relativer Termin und ein Artikel, den es nicht gibt", "TEXT"),
                new Sample(PDF, "Bestellung als PDF",
                        "Bestellformular eines Geschäftskunden mit Kalenderwoche und abweichender Lieferanschrift", "PDF"));
    }

    public SampleText text(String key, LocalDate today) {
        Data d = data();
        return switch (key) {
            case STRUCTURED -> structured(d, today);
            case INFORMAL -> informal(d, today);
            default -> throw new ResourceNotFoundException("Beispiel " + key + " nicht gefunden");
        };
    }

    // ------------------------------------------------------------------

    private record Data(List<Customer> customers, List<Product> products, Product incident) {
        Customer customer(int index) {
            return customers.get(index % customers.size());
        }

        Product product(int index) {
            return products.get(index % products.size());
        }
    }

    private Data data() {
        List<Customer> customers = customerRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()) && c.getCustomerNumber() != null)
                .sorted(Comparator.comparing(Customer::getCustomerNumber)).toList();
        List<Product> products = productRepository.findByActiveTrue().stream()
                .filter(p -> p.getMaterialNumber() != null && p.getMaterialNumber().matches("\\d{6}"))
                .sorted(Comparator.comparing(Product::getMaterialNumber)).toList();
        if (customers.isEmpty() || products.isEmpty()) {
            throw new ResourceNotFoundException("Für Beispiele werden Debitoren und Materialien benötigt");
        }
        return new Data(customers, products, DemoReviewGenerator.incidentProduct(products));
    }

    private static SampleText structured(Data d, LocalDate today) {
        Customer c = d.customer(17);
        Product a = d.product(3);
        Product b = d.product(28);
        LocalDate date = workday(today.plusDays(10));
        String text = """
                Sehr geehrte Damen und Herren,

                hiermit bestelle ich zur Lieferung bis spätestens %s:

                %s  %s  – 2 Stück
                %s  %s  – 1 Stück

                Kundennummer: %s
                Bestellnummer: B-%d-%03d

                Bitte liefern Sie an die hinterlegte Adresse.

                Mit freundlichen Grüßen
                %s %s
                %s
                """.formatted(DATE.format(date), a.getMaterialNumber(), a.getName(), b.getMaterialNumber(), b.getName(),
                c.getCustomerNumber(), today.getYear(), 100 + today.getDayOfYear(), c.getFirstName(), c.getLastName(), cityLine(c));
        return new SampleText(c.getFirstName() + " " + c.getLastName() + " <" + c.getEmail() + ">",
                "Bestellung " + c.getCustomerNumber(), text);
    }

    private static SampleText informal(Data d, LocalDate today) {
        Customer c = d.customer(58);
        Product a = d.incident;
        Product b = d.products.stream().filter(p -> !p.equals(a)).skip(41).findFirst().orElse(d.product(41));
        String privateMail = c.getEmail().substring(0, c.getEmail().indexOf('@')) + "@web.de";
        String text = """
                Hallo zusammen,

                wir richten gerade unser neues Büro ein und bräuchten bis nächsten Freitag:

                - 4x %s
                - %s, 2 Stück
                - 3 Kartons Druckerpapier A4

                Unsere Bestellnummer ist %s/%d. Bitte vor der Anlieferung kurz anrufen, wir sind im Hinterhof.

                Danke und viele Grüße
                %s %s
                %s
                """.formatted(paraphrase(a.getName()), paraphrase(b.getName()), c.getLastName().toUpperCase(Locale.GERMANY),
                today.getDayOfYear(), c.getFirstName(), c.getLastName(), c.getCity() == null ? "" : c.getCity());
        return new SampleText(c.getFirstName() + " " + c.getLastName() + " <" + privateMail + ">",
                "Bestellung fürs neue Büro", text);
    }

    /** PDF-Bestellformular eines Geschäftskunden. */
    public byte[] pdf(LocalDate today) {
        Data d = data();
        Customer c = d.customer(121);
        String company = c.getLastName() + " & Partner GmbH";
        List<Product> items = List.of(d.product(9), d.product(47), d.product(76));
        int[] quantities = {5, 12, 2};
        LocalDate week = today.plusWeeks(3);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 56, 56, 56, 56);
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph(company, font(14, Font.BOLD)));
        document.add(new Paragraph(Objects.toString(c.getAddress(), "") + " · " + Objects.toString(c.getZipCode(), "") + " "
                + Objects.toString(c.getCity(), ""), font(9, Font.NORMAL)));
        document.add(new Paragraph("Einkauf: " + c.getFirstName() + " " + c.getLastName() + " · " + c.getEmail(), font(9, Font.NORMAL)));
        document.add(new Paragraph(" "));
        document.add(new Paragraph("An: OM Handels GmbH, Vertrieb", font(9, Font.NORMAL)));
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Bestellung Nr. PO-" + (45000 + today.getDayOfYear()), font(16, Font.BOLD)));
        document.add(new Paragraph("Bestelldatum: " + DATE.format(today), font(10, Font.NORMAL)));
        document.add(new Paragraph("Gewünschter Liefertermin: KW " + week.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + "/"
                + week.get(IsoFields.WEEK_BASED_YEAR), font(10, Font.NORMAL)));
        document.add(new Paragraph("Lieferanschrift: " + company + ", Lager Tor 3, Gewerbering 12, "
                + Objects.toString(c.getZipCode(), "") + " " + Objects.toString(c.getCity(), ""), font(10, Font.NORMAL)));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(new float[]{0.6f, 1.4f, 5f, 1f, 1f});
        table.setWidthPercentage(100);
        for (String h : List.of("Pos", "Art.-Nr.", "Bezeichnung", "Menge", "Einheit")) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font(9, Font.BOLD)));
            cell.setBorderColor(LINE);
            cell.setPadding(4);
            table.addCell(cell);
        }
        for (int i = 0; i < items.size(); i++) {
            for (String v : List.of(String.valueOf((i + 1) * 10), items.get(i).getMaterialNumber(), items.get(i).getName(),
                    String.valueOf(quantities[i]), "ST")) {
                PdfPCell cell = new PdfPCell(new Phrase(v, font(9, Font.NORMAL)));
                cell.setBorderColor(LINE);
                cell.setPadding(4);
                table.addCell(cell);
            }
        }
        document.add(table);
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Anlieferung bitte nur Mo–Do zwischen 7 und 14 Uhr an Tor 3.", font(10, Font.NORMAL)));
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Mit freundlichen Grüßen", font(10, Font.NORMAL)));
        document.add(new Paragraph(c.getFirstName() + " " + c.getLastName(), font(10, Font.NORMAL)));
        document.close();
        return out.toByteArray();
    }

    // ------------------------------------------------------------------

    /** Umschreibt eine Bezeichnung so, wie Kunden sie formlos schreiben: Marke und Produktart, ohne Zusätze. */
    static String paraphrase(String name) {
        String plain = name.replaceAll("\\(.*?\\)", " ").replaceAll("['\"„“‚‘]", " ").replaceAll("\\s{2,}", " ").strip();
        List<String> words = new ArrayList<>(List.of(plain.split(" ")));
        // Zusätze wie "Set", "Pro" oder Modellnummern lassen Kunden meist weg
        while (words.size() > 2 && (words.getLast().length() <= 3 || words.getLast().matches(".*\\d.*")
                || words.getLast().matches("(?i)set|plus|mini|max"))) {
            words.removeLast();
        }
        return words.size() <= 2 ? String.join(" ", words) : words.getFirst() + " " + words.getLast();
    }

    private static LocalDate workday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY
                ? date.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) : date;
    }

    private static String cityLine(Customer c) {
        return (Objects.toString(c.getZipCode(), "") + " " + Objects.toString(c.getCity(), "")).strip();
    }

    private static Font font(float size, int style) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, Color.BLACK);
    }
}
