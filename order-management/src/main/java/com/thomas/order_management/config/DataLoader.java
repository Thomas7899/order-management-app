// order-management/src/main/java/com/thomas/order_management/config/DataLoader.java
package com.thomas.order_management.config;

import com.github.javafaker.Faker;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import com.thomas.order_management.service.NumberRangeService;
import com.thomas.order_management.service.ReviewEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
@ConditionalOnProperty(name = "app.demo.seed-data", havingValue = "true", matchIfMissing = true)
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    private static final long SEED = 42L;

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewEmbeddingService embeddingService;
    private final NumberRangeService numberRangeService;
    private final DemoBusinessSimulator simulator;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.dev.generate-embeddings:true}")
    private boolean generateEmbeddings;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${app.dev.reset-on-start:true}")
    private boolean resetOnStart;

    private final Faker faker = new Faker(Locale.GERMANY, new Random(SEED));
    private final Random random = new Random(SEED);

    // Trend-Produkte IDs (werden nach dem Seeding gesetzt)
    private final Set<Long> trendingUpProducts = new HashSet<>();
    private final Set<Long> trendingDownProducts = new HashSet<>();

    public DataLoader(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductReviewRepository reviewRepository,
            ReviewEmbeddingService embeddingService,
            NumberRangeService numberRangeService,
            DemoBusinessSimulator simulator,
            JdbcTemplate jdbcTemplate
    ) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.reviewRepository = reviewRepository;
        this.embeddingService = embeddingService;
        this.numberRangeService = numberRangeService;
        this.simulator = simulator;
        this.jdbcTemplate = jdbcTemplate;
    }


    @Override
    public void run(String... args) {

        if (resetOnStart) {
            clearAll();
            loadSampleData();
            return;
        }

        boolean hasAnyData =
                customerRepository.count() > 0 ||
                productRepository.count() > 0 ||
                orderRepository.count() > 0 ||
                reviewRepository.count() > 0;

        if (!hasAnyData) loadSampleData();
    }

    /** Löscht alle Bewegungsdaten und Stammdaten in einer Reihenfolge, die alle Fremdschlüssel respektiert. */
    private void clearAll() {
        for (String sql : List.of(
                "DELETE FROM change_documents",
                "DELETE FROM review_embeddings",
                "DELETE FROM review_insight_aspects",
                "DELETE FROM review_insights",
                "DELETE FROM product_reviews",
                "DELETE FROM invoice_items",
                "UPDATE invoices SET reference_invoice_id = NULL",
                "DELETE FROM invoices",
                "DELETE FROM delivery_items",
                "DELETE FROM deliveries",
                "UPDATE stock_movements SET reversed_movement_id = NULL",
                "DELETE FROM stock_movements",
                "DELETE FROM warehouse_stocks",
                "DELETE FROM warehouses",
                "DELETE FROM order_item_conditions",
                "DELETE FROM order_items",
                "DELETE FROM orders",
                "DELETE FROM products",
                "DELETE FROM customers",
                "DELETE FROM number_ranges")) {
            jdbcTemplate.update(sql);
        }
    }

    private void loadSampleData() {
        long started = System.currentTimeMillis();
        List<Customer> customers = seedCustomers();
        List<Product> products = seedProducts();
        simulator.simulate(customers, products, trendingUpProducts, trendingDownProducts, random);
        seedReviews(products, customers);
        log.info("Demo data loaded in {} s", (System.currentTimeMillis() - started) / 1000);
    }

    private String normalizeEmailPart(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        
        normalized = normalized
                .toLowerCase(Locale.GERMANY)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    private List<Customer> seedCustomers() {
        List<Customer> customers = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 200; i++) {
            String first = faker.name().firstName();
            String last = faker.name().lastName();
            String email = normalizeEmailPart(first) + "." +
                           normalizeEmailPart(last) + "@" +
                           faker.internet().domainName();

            Customer c = new Customer(first, last, email);
            c.setCustomerNumber(numberRangeService.next(NumberRangeObject.CUSTOMER));
            c.setCity(faker.address().city());
            c.setCountry(random.nextDouble() < 0.85 ? "Deutschland" : random.nextBoolean() ? "Österreich" : "Schweiz");
            c.setZipCode(faker.address().zipCode());
            c.setAddress(faker.address().streetAddress());
            c.setPhone(faker.phoneNumber().cellPhone());

            // Geschäftskunden (ca. 20 %) erhalten Kundenrabatt, Skontokonditionen und höhere Kreditlimits
            boolean business = random.nextDouble() < 0.2;
            double terms = random.nextDouble();
            c.setPaymentTerms(business
                    ? (terms < 0.7 ? PaymentTerms.ZB01 : PaymentTerms.ZB30)
                    : (terms < 0.25 ? PaymentTerms.ZB01 : terms < 0.75 ? PaymentTerms.ZB30 : terms < 0.95 ? PaymentTerms.ZB14 : PaymentTerms.ZB00));
            c.setDiscountPercent(business ? BigDecimal.valueOf(2 + random.nextInt(9)) : BigDecimal.ZERO);
            if (business) {
                c.setCreditLimit(BigDecimal.valueOf((20 + random.nextInt(61)) * 1000L));
            } else if (random.nextDouble() < 0.7) {
                // Wenige Privatkunden mit knappem Limit erzeugen gelegentlich Kreditsperren
                c.setCreditLimit(BigDecimal.valueOf(random.nextDouble() < 0.07 ? 1000 : (5 + random.nextInt(11)) * 1000L));
            }
            c.setActive(true);

            // Die meisten Kunden existieren schon vor dem simulierten Jahr, einige kommen im Laufe des Jahres hinzu
            LocalDateTime createdAt = random.nextDouble() < 0.85
                    ? now.minusDays(370 + random.nextInt(700))
                    : now.minusDays(random.nextInt(360));
            c.setCreatedAt(createdAt);
            c.setUpdatedAt(createdAt);

            customers.add(c);
        }
        return customerRepository.saveAll(customers);
    }

    // *** KOMPLETT NEUE LOGIK FÜR PRODUKTE ***
    private List<Product> seedProducts() {
        List<Product> list = new ArrayList<>();

        // Kategorie: Elektronik (erweitert)
        String catElektronik = "Elektronik";
        List<String> elektronikNamen = Arrays.asList(
                "Logitech MX Master 3S - Kabellose Maus",
                "Dell XPS 15 Laptop (15 Zoll, 16GB RAM, 1TB SSD)",
                "Sony WH-1000XM5 Noise-Cancelling-Kopfhörer",
                "Apple MacBook Pro 14 Zoll (M3 Chip)",
                "Samsung QLED 4K TV (55 Zoll)",
                "Razer BlackWidow V4 Pro - Mechanische Tastatur",
                "Bose SoundLink Revolve+ II Bluetooth-Lautsprecher",
                "Anker 737 Power Bank (PowerCore 24K)",
                "TP-Link Archer AX73 WLAN 6 Router",
                "GoPro HERO12 Black - Action-Kamera",
                "Apple iPad Pro 12.9 Zoll (M2 Chip, 256GB)",
                "Nintendo Switch OLED Modell",
                "Samsung Galaxy Watch 6 Classic",
                "DJI Mini 3 Pro Drohne",
                "Sonos Era 300 Premium Lautsprecher"
        );
        for (String name : elektronikNamen) {
            String desc = generateAmazonLikeDescription(catElektronik, name);
            Product p = createProduct(name, desc, catElektronik, faker.number().randomDouble(2, 100, 3000));
            list.add(p);
        }

        // Kategorie: Möbel (erweitert)
        String catMoebel = "Möbel";
        List<String> moebelNamen = Arrays.asList(
                "Ergonomischer Bürostuhl 'ErgoPro'",
                "Minimalistischer Schreibtisch (Weiß, 120x60cm)",
                "Skandinavischer Esszimmerstuhl (Eiche)",
                "Modernes Ledersofa (3-Sitzer, Grau)",
                "Massivholz-Doppelbett 'Natura' (180x200cm)",
                "Schwebendes TV-Lowboard (Hochglanz)",
                "Industrie-Design Bücherregal (Stahl & Holz)",
                "Ausziehbarer Esstisch (Platz für 8 Personen)",
                "Bequemer 'Lounge' Sessel mit Hocker",
                "Kompakter Garderobenschrank 'Entry'",
                "Höhenverstellbarer Stehschreibtisch 'FlexDesk'",
                "Modulares Regalsystem 'Cubo'",
                "Samt-Polsterstuhl 'Velvet' (2er-Set)",
                "Massivholz-Sideboard (Wildeiche)",
                "Gaming-Schreibtisch mit RGB-Beleuchtung"
        );
        for (String name : moebelNamen) {
            String desc = generateAmazonLikeDescription(catMoebel, name);
            Product p = createProduct(name, desc, catMoebel, faker.number().randomDouble(2, 80, 2000));
            list.add(p);
        }
        
        // Kategorie: Beleuchtung (erweitert)
        String catLicht = "Beleuchtung";
        List<String> lichtNamen = Arrays.asList(
                "Philips Hue White & Color Ambiance (E27)",
                "Moderne Bogen-Stehlampe (Stoffschirm)",
                "Dimmbare LED-Deckenleuchte (Rund)",
                "Vintage Edison Glühbirne (4er-Pack)",
                "LED Schreibtischlampe mit USB-Ladeanschluss",
                "Solar-Lichterkette für den Garten (10m)",
                "Paulmann 'URail' Schienensystem (Starter-Set)",
                "indirekte LED-Wandleuchte (Warmweiß)",
                "Smarte WiFi-Lichtschalter (3er-Pack)",
                "Kinderzimmer-Deckenlampe 'Sternenhimmel'",
                "Nanoleaf Shapes Hexagon Starter Kit",
                "IKEA TRÅDFRI Smarte Beleuchtung Set",
                "Außen-Wandleuchte mit Bewegungsmelder",
                "Designer-Pendelleuchte 'Sphere' (Kupfer)",
                "LED-Strip RGB+W (5 Meter, App-Steuerung)"
        );
        for (String name : lichtNamen) {
            String desc = generateAmazonLikeDescription(catLicht, name);
            Product p = createProduct(name, desc, catLicht, faker.number().randomDouble(2, 20, 450));
            list.add(p);
        }

        // Kategorie: Bürobedarf (erweitert)
        String catBuero = "Bürobedarf";
        List<String> bueroNamen = Arrays.asList(
                "Moleskine Notizbuch A5 (Liniert, Schwarz)",
                "Faber-Castell Stifte-Set 'Grip' (24er-Etui)",
                "Leitz Aktenordner (DIN A4, 10er-Pack, Wolkenmarmor)",
                "HP OfficeJet Pro 9010e Multifunktionsdrucker",
                "Fellowes 'Powershred' Aktenvernichter",
                "Tesa Klebeband-Abroller (Inkl. 4 Rollen)",
                "Durable Schreibtisch-Organizer (Metall)",
                "Laminiergerät A3/A4 (Starter-Set)",
                "Magnetisches Whiteboard (90x60cm)",
                "Post-it Haftnotizen (Cube, Neonfarben)",
                "Ergonomische Dokumentenhalter",
                "Brother P-touch Beschriftungsgerät",
                "Locher und Heftgerät Kombi-Set",
                "Flipchart-Ständer mit Papierrolle",
                "USB-C Docking Station 12-in-1"
        );
        for (String name : bueroNamen) {
            String desc = generateAmazonLikeDescription(catBuero, name);
            Product p = createProduct(name, desc, catBuero, faker.number().randomDouble(2, 10, 300));
            list.add(p);
        }
        
        // NEU: Kategorie Sport & Fitness
        String catSport = "Sport & Fitness";
        List<String> sportNamen = Arrays.asList(
                "Fitbit Charge 6 Fitness-Tracker",
                "Yoga-Matte Premium (6mm, Rutschfest)",
                "Kurzhantel-Set 2-20kg (Gusseisen)",
                "Laufband 'ProRunner' (Klappbar)",
                "Resistance Bands Set (5 Stärken)",
                "Fahrradcomputer GPS 'CycloNav'",
                "Boxsack-Set mit Handschuhen",
                "Schwimmbrille Anti-Fog (3er-Pack)",
                "Gymnastikball 65cm mit Pumpe",
                "Kettlebell Set (4kg, 8kg, 12kg)",
                "Springseil Speed Rope (Profi)",
                "Foam Roller Massagerolle (45cm)",
                "Trinkflasche Edelstahl 1L (Isoliert)",
                "Sport-Kopfhörer wasserdicht IP68",
                "Heimtrainer 'SpinCycle Pro'"
        );
        for (String name : sportNamen) {
            String desc = generateAmazonLikeDescription(catSport, name);
            Product p = createProduct(name, desc, catSport, faker.number().randomDouble(2, 15, 800));
            list.add(p);
        }
        
        // NEU: Kategorie Haushalt & Küche
        String catHaushalt = "Haushalt & Küche";
        List<String> haushaltNamen = Arrays.asList(
                "KitchenAid Artisan Küchenmaschine 4.8L",
                "Dyson V15 Detect Akkusauger",
                "Nespresso Vertuo Next Kaffeemaschine",
                "Philips Airfryer XXL Premium",
                "Thermomix TM6 Küchenmaschine",
                "iRobot Roomba j7+ Saugroboter",
                "WMF Topfset 'Function 4' (6-teilig)",
                "Ninja Foodi Multikocher 11-in-1",
                "Sodastream Crystal 3.0 Wassersprudler",
                "Zwilling Messerblock 'Pro' (7-teilig)",
                "Siemens iQ700 Einbau-Backofen",
                "Miele Triflex HX2 Akkusauger",
                "Smeg Retro-Toaster (Pastellgrün)",
                "Le Creuset Bräter (26cm, Kirschrot)",
                "Sage Barista Express Espressomaschine"
        );
        for (String name : haushaltNamen) {
            String desc = generateAmazonLikeDescription(catHaushalt, name);
            Product p = createProduct(name, desc, catHaushalt, faker.number().randomDouble(2, 30, 1500));
            list.add(p);
        }
        
        // NEU: Kategorie Garten & Outdoor
        String catGarten = "Garten & Outdoor";
        List<String> gartenNamen = Arrays.asList(
                "Bosch Rasenmäher 'Rotak 43'",
                "Weber Genesis II E-310 Gasgrill",
                "Gardena Mähroboter 'Sileno City'",
                "Kärcher Hochdruckreiniger K5",
                "Solar-Gartenleuchten (8er-Set)",
                "Hängemattengestell mit Hängematte",
                "Outdoor-Loungemöbel Set (5-teilig)",
                "Gewächshaus 'GrowPro' (3x2m)",
                "Makita Akku-Heckenschere",
                "Regentonne 300L mit Ablaufhahn",
                "Feuerschale Edelstahl (80cm)",
                "Sonnenschirm 'Ampelschirm' (3m)",
                "Vertikaler Kräutergarten (Wandmontage)",
                "Pool Intex Frame 'Ultra XTR'",
                "Komposter Schnellkomposter 800L"
        );
        for (String name : gartenNamen) {
            String desc = generateAmazonLikeDescription(catGarten, name);
            Product p = createProduct(name, desc, catGarten, faker.number().randomDouble(2, 25, 1200));
            list.add(p);
        }

        Collections.shuffle(list, random);
        for (Product p : list) {
            p.setMaterialNumber(numberRangeService.next(NumberRangeObject.MATERIAL));
        }
        List<Product> savedProducts = productRepository.saveAll(list);
        
        // Trend-Produkte markieren (für saisonale Analyse)
        markTrendProducts(savedProducts);
        
        log.info("Seeded {} products across {} categories", savedProducts.size(), 7);
        return savedProducts;
    }
    
    /**
     * Markiert einige Produkte als aufstrebend oder absteigend für Trendanalysen
     */
    private void markTrendProducts(List<Product> products) {
        // 15% der Produkte sind Trend-Produkte (steigend)
        // 10% der Produkte sind absteigende Produkte
        int trendUpCount = (int) (products.size() * 0.15);
        int trendDownCount = (int) (products.size() * 0.10);
        
        List<Product> shuffled = new ArrayList<>(products);
        Collections.shuffle(shuffled, random);
        
        for (int i = 0; i < trendUpCount && i < shuffled.size(); i++) {
            trendingUpProducts.add(shuffled.get(i).getId());
        }
        
        for (int i = trendUpCount; i < trendUpCount + trendDownCount && i < shuffled.size(); i++) {
            trendingDownProducts.add(shuffled.get(i).getId());
        }
        
        log.info("Marked {} trending up products and {} trending down products", 
                trendingUpProducts.size(), trendingDownProducts.size());
    }

    /**
     * NEUE HILFSMETHODE
     * Erstellt eine "Amazon-ähnliche" Beschreibung.
     */
    private String generateAmazonLikeDescription(String category, String productName) {
        String intro = "Entdecken Sie " + productName + ". ";
        String body = "";
        String outro = "Perfekt für den täglichen Gebrauch. Jetzt bestellen und von schneller Lieferung profitieren!";
        
        switch (category) {
            case "Elektronik":
                body = "Dieses High-End-Gerät kombiniert Leistung mit elegantem Design. Mit " + 
                       faker.number().numberBetween(2, 6) + " Kernen und " + 
                       faker.number().numberBetween(8, 32) + "GB RAM. ";
                outro = "Ideal für Home-Office, Gaming und kreative Profis. Inklusive " + 
                        faker.number().numberBetween(2, 3) + " Jahren Herstellergarantie.";
                break;
            case "Möbel":
                body = "Ein echter Hingucker für Ihr Zuhause. Gefertigt aus hochwertigem " + 
                       faker.commerce().material() + ". Bietet optimalen Komfort und Langlebigkeit. ";
                outro = "Einfache Montage. Verleihen Sie Ihrem Wohnraum einen neuen Look.";
                break;
            case "Beleuchtung":
                body = "Sorgt für die perfekte Atmosphäre in jedem Raum. Energieeffizienzklasse A++. " +
                       "Mit " + faker.number().numberBetween(800, 3000) + " Lumen. ";
                outro = "Lange Lebensdauer von über " + faker.number().numberBetween(15, 50) + ".000 Stunden.";
                break;
            case "Bürobedarf":
                body = "Unverzichtbar für ein organisiertes Büro. Dieses Produkt " + 
                       "hilft Ihnen, produktiv zu bleiben. Hochwertige Verarbeitung. ";
                outro = "Ideal für Schule, Universität oder das Büro. Multipack verfügbar.";
                break;
            case "Sport & Fitness":
                body = "Erreichen Sie Ihre Fitnessziele mit diesem professionellen Equipment. " +
                       "Ergonomisch gestaltet für maximale Effizienz. " +
                       "Von Athleten empfohlen. ";
                outro = "Perfekt für Anfänger und Profis. Inklusive Trainingsleitfaden.";
                break;
            case "Haushalt & Küche":
                body = "Revolutionieren Sie Ihre Küche mit diesem innovativen Gerät. " +
                       faker.number().numberBetween(5, 15) + " Funktionen in einem. " +
                       "Einfache Reinigung, langlebige Materialien. ";
                outro = "Der perfekte Küchenhelfer für Hobbyköche und Profis. 2 Jahre Garantie.";
                break;
            case "Garten & Outdoor":
                body = "Verwandeln Sie Ihren Garten in eine Oase. Wetterfest und robust. " +
                       "Einfache Installation in nur " + faker.number().numberBetween(10, 30) + " Minuten. ";
                outro = "Genießen Sie die Natur mit Premium-Qualität. Ideal für jede Jahreszeit.";
                break;
            default:
                body = faker.lorem().sentence(10) + ". ";
        }
        
        return intro + body + outro;
    }

    /**
     * ANGEPASSTE HILFSMETHODE
     * Nimmt jetzt eine Beschreibung entgegen und verwendet nicht mehr faker.lorem.
     */
    private Product createProduct(String name, String description, String category, double price) {
        BigDecimal bigPrice = BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP);
        // Bestand entsteht ausschließlich über Materialbelege (Anfangsbestand 561 im Simulator)
        Product p = new Product(name, description, bigPrice, 0);
        p.setCategory(category);
        p.setUnitOfMeasure("ST");
        p.setTaxCode(TaxCode.FULL);
        // Einstandspreis: 55–75 % des Verkaufspreises
        double costShare = 0.55 + random.nextDouble() * 0.20;
        p.setCostPrice(bigPrice.multiply(BigDecimal.valueOf(costShare)).setScale(2, java.math.RoundingMode.HALF_UP));
        p.setImageUrl("https://picsum.photos/seed/" + name.replaceAll("[^A-Za-z0-9]", "") + "/400/300");
        return p;
    }

    private void seedReviews(List<Product> products, List<Customer> customers) {
        List<DemoReviewGenerator.Purchase> purchases = orderItemRepository.findBilledPurchases().stream()
                .map(row -> new DemoReviewGenerator.Purchase((Long) row[0], (Long) row[1], (Long) row[2], (LocalDateTime) row[3]))
                .toList();
        List<DemoReviewGenerator.Draft> drafts = new DemoReviewGenerator(random, LocalDate.now())
                .generate(products, customers, purchases, trendingUpProducts, trendingDownProducts);

        List<ProductReview> reviews = drafts.stream().map(draft -> {
            ProductReview review = new ProductReview();
            review.setProduct(draft.product());
            review.setCustomer(draft.customer());
            if (draft.orderItemId() != null) {
                review.setOrderItem(orderItemRepository.getReferenceById(draft.orderItemId()));
            }
            review.setRating(draft.rating());
            review.setComment(draft.comment());
            review.setCreatedAt(draft.createdAt());
            return review;
        }).toList();
        List<ProductReview> saved = reviewRepository.saveAll(reviews);
        log.info("Seeded {} reviews (Qualitätsvorfall bei '{}')", saved.size(),
                DemoReviewGenerator.incidentProduct(products).getName());

        // Die Einordnung (Stimmung, Themen) übernimmt ReviewInsightBootstrap nach dem Start
        if (generateEmbeddings) {
            if (openAiApiKey == null || openAiApiKey.isBlank() || openAiApiKey.contains("${")
                    || "not-configured".equals(openAiApiKey)) {
                log.warn("Embeddings werden übersprungen: kein gültiger OpenAI API Key konfiguriert.");
                return;
            }
            log.info("Created embeddings for {} reviews", embeddingService.embed(saved));
        }
    }
}
