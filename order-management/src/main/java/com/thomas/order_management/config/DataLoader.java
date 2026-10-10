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
    private final UserRepository userRepository;
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
            UserRepository userRepository,
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
        this.userRepository = userRepository;
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
                "DELETE FROM users",
                "DELETE FROM number_ranges")) {
            jdbcTemplate.update(sql);
        }
    }

    private void loadSampleData() {
        long started = System.currentTimeMillis();
        List<Customer> customers = seedCustomers();
        seedUsers();
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
                c.setCreditLimit(BigDecimal.valueOf((10 + random.nextInt(41)) * 1000L));
            } else if (random.nextDouble() < 0.6) {
                // Einige Privatkunden mit knappem Limit erzeugen gelegentlich Kreditsperren
                c.setCreditLimit(BigDecimal.valueOf(random.nextDouble() < 0.15 ? 1500 : (3 + random.nextInt(8)) * 1000L));
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

    private List<User> seedUsers() {
        List<User> list = new ArrayList<>();

        list.add(new User("Admin", "admin@example.com", "Admin"));
        list.add(new User("Marie Reuter", "marie@example.com", "User"));
        list.add(new User("Jonas Meier", "jonas@example.com", "User"));
        list.add(new User("Paul Richter", "paul@example.com", "User"));

        return userRepository.saveAll(list);
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
        List<ProductReview> reviews = new ArrayList<>();
        Map<Long, Product> productById = new HashMap<>();
        products.forEach(p -> productById.put(p.getId(), p));
        Map<Long, Customer> customerById = new HashMap<>();
        customers.forEach(c -> customerById.put(c.getId(), c));
        List<Object[]> purchases = orderItemRepository.findBilledPurchases();

        // 600 Reviews: überwiegend verifizierte Käufe, ergänzt um freie Bewertungen
        for (int i = 0; i < 600; i++) {
            if (!purchases.isEmpty() && random.nextDouble() < 0.8) {
                Object[] purchase = purchases.get(random.nextInt(purchases.size()));
                Product product = productById.get((Long) purchase[0]);
                Customer customer = customerById.get((Long) purchase[1]);
                if (product != null && customer != null) {
                    int rating = generateTrendAwareRating(product);
                    ProductReview review = new ProductReview();
                    review.setProduct(product);
                    review.setCustomer(customer);
                    review.setOrderItem(orderItemRepository.getReferenceById((Long) purchase[2]));
                    review.setRating(rating);
                    review.setComment(generateEmotionalComment(product, rating));
                    LocalDateTime reviewDate = ((LocalDateTime) purchase[3]).plusDays(3 + random.nextInt(25));
                    review.setCreatedAt(reviewDate.isAfter(LocalDateTime.now()) ? LocalDateTime.now().minusHours(random.nextInt(48)) : reviewDate);
                    reviews.add(review);
                    continue;
                }
            }
            Product product = products.get(random.nextInt(products.size()));
            Customer customer = customers.get(random.nextInt(customers.size()));

            // Rating basierend auf Produkt-Trend
            int rating = generateTrendAwareRating(product);
            
            // Detaillierter, emotionaler Kommentar
            String comment = generateEmotionalComment(product, rating);

            ProductReview review = new ProductReview();
            review.setProduct(product);
            review.setCustomer(customer);
            review.setRating(rating);
            review.setComment(comment);
            
            // Reviews über 365 Tage verteilen (statt nur 180)
            LocalDateTime reviewDate = generateReviewDate(product);
            review.setCreatedAt(reviewDate);
            reviews.add(review);
        }

        // Anomalie-Produkt mit plötzlichem Qualitätsproblem
        if (products.size() > 10 && !customers.isEmpty()) {
            Product anomalyProduct = products.get(10); 
            
            // 20 schlechte Reviews in den letzten 7 Tagen (Qualitätsproblem)
            for (int i = 0; i < 20; i++) {
                Customer c = customers.get(random.nextInt(customers.size()));
                ProductReview anomalyReview = new ProductReview();
                anomalyReview.setProduct(anomalyProduct);
                anomalyReview.setCustomer(c);
                anomalyReview.setRating(1);
                
                String[] criticalComments = {
                    "Direkt nach dem Auspacken defekt! Inakzeptabel!",
                    "Das Produkt ist nach 2 Tagen kaputtgegangen. Sehr enttäuscht!",
                    "Qualität ist katastrophal. Geld verschwendet!",
                    "Finger weg! Hatte hohe Erwartungen, wurde bitter enttäuscht.",
                    "Schlechteste Kaufentscheidung überhaupt. Geht gar nicht!",
                    "Nach einer Woche schon Probleme. Support reagiert nicht!",
                    "Totaler Schrott, verarbeitung unterirdisch.",
                    "Produkt riecht komisch und funktioniert nicht richtig.",
                    "Wurde beschädigt geliefert. Rücksendung war auch ein Albtraum!",
                    "Nie wieder! Das Produkt hat meine Erwartungen null erfüllt."
                };
                anomalyReview.setComment(criticalComments[random.nextInt(criticalComments.length)]);
                anomalyReview.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(7)));
                reviews.add(anomalyReview);
            }
        }
        
        // Trend-Up Produkt: Viele positive Reviews in letzter Zeit
        for (Long productId : trendingUpProducts) {
            Product trendProduct = products.stream()
                    .filter(p -> p.getId().equals(productId))
                    .findFirst()
                    .orElse(null);
                    
            if (trendProduct != null) {
                for (int i = 0; i < 8; i++) {
                    Customer c = customers.get(random.nextInt(customers.size()));
                    ProductReview trendReview = new ProductReview();
                    trendReview.setProduct(trendProduct);
                    trendReview.setCustomer(c);
                    trendReview.setRating(random.nextDouble() < 0.8 ? 5 : 4);
                    trendReview.setComment(generatePositiveTrendComment(trendProduct));
                    trendReview.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(30)));
                    reviews.add(trendReview);
                }
            }
        }
        
        // Trend-Down Produkt: Verschlechternde Reviews über Zeit
        for (Long productId : trendingDownProducts) {
            Product decliningProduct = products.stream()
                    .filter(p -> p.getId().equals(productId))
                    .findFirst()
                    .orElse(null);
                    
            if (decliningProduct != null) {
                for (int i = 0; i < 6; i++) {
                    Customer c = customers.get(random.nextInt(customers.size()));
                    ProductReview declineReview = new ProductReview();
                    declineReview.setProduct(decliningProduct);
                    declineReview.setCustomer(c);
                    declineReview.setRating(random.nextDouble() < 0.6 ? 2 : 3);
                    declineReview.setComment(generateNegativeTrendComment(decliningProduct));
                    declineReview.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(60)));
                    reviews.add(declineReview);
                }
            }
        }

        List<ProductReview> savedReviews = reviewRepository.saveAll(reviews);
        log.info("Seeded {} reviews (including anomalies and trends)", savedReviews.size());

        if (generateEmbeddings) {
            if (openAiApiKey == null || openAiApiKey.isBlank() || openAiApiKey.contains("${")) {
                log.warn("Embeddings werden übersprungen: kein gültiger OpenAI API Key konfiguriert.");
                return;
            }

            int embeddedCount = 0;
            for (ProductReview r1 : savedReviews) {
                try {
                    embeddingService.createEmbedding(r1);
                    embeddedCount++;
                } catch (RuntimeException ex) {
                    log.warn("Embedding für Review {} fehlgeschlagen – übersprungen: {}", r1.getId(), ex.getMessage());
                }
            }
            log.info("Created embeddings for {} reviews", embeddedCount);
        }
    }
    
    /**
     * Generiert Rating basierend auf Produkt-Trend
     */
    private int generateTrendAwareRating(Product product) {
        if (trendingUpProducts.contains(product.getId())) {
            // Trend-Up: mehr 4er und 5er
            double rand = random.nextDouble();
            if (rand < 0.5) return 5;
            if (rand < 0.8) return 4;
            if (rand < 0.95) return 3;
            return 2;
        }
        
        if (trendingDownProducts.contains(product.getId())) {
            // Trend-Down: mehr 2er und 3er
            double rand = random.nextDouble();
            if (rand < 0.3) return 2;
            if (rand < 0.6) return 3;
            if (rand < 0.85) return 4;
            return random.nextBoolean() ? 5 : 1;
        }
        
        // Normal: leicht positiv verzerrt
        return random.nextInt(1, 6);
    }
    
    /**
     * Generiert Review-Datum mit Berücksichtigung von Trends
     */
    private LocalDateTime generateReviewDate(Product product) {
        if (trendingUpProducts.contains(product.getId())) {
            // Mehr neuere Reviews für Trend-Produkte
            return LocalDateTime.now().minusDays(random.nextInt(90));
        }
        if (trendingDownProducts.contains(product.getId())) {
            // Verteilt über längeren Zeitraum
            return LocalDateTime.now().minusDays(30 + random.nextInt(335));
        }
        // Normal: über 365 Tage verteilt
        return LocalDateTime.now().minusDays(random.nextInt(365));
    }
    
    /**
     * Generiert positive Kommentare für Trend-Up Produkte
     */
    private String generatePositiveTrendComment(Product product) {
        String[] comments = {
            "Wow, ein echter Geheimtipp! Bin total begeistert! 🔥",
            "Hab es auf Social Media gesehen und musste es haben. Absolut lohnenswert!",
            "Beste Investition seit langem! Kann es nur empfehlen.",
            "Übertrifft alle Erwartungen. Preis-Leistung ist unschlagbar!",
            "Endlich ein Produkt, das hält was es verspricht! Super zufrieden.",
            "Mein absolutes Highlight dieses Jahr. Danke für dieses tolle Produkt!",
            "Freunde haben es mir empfohlen - sie hatten recht! Fantastisch!",
            "Design und Qualität sind erstklassig. Würde es sofort wieder kaufen.",
            "Das " + product.getCategory() + "-Produkt meiner Träume! ⭐⭐⭐⭐⭐",
            "Nach langer Recherche endlich das Richtige gefunden. Perfekt!"
        };
        return comments[random.nextInt(comments.length)];
    }
    
    /**
     * Generiert negative Kommentare für Trend-Down Produkte
     */
    private String generateNegativeTrendComment(Product product) {
        String[] comments = {
            "Früher war das Produkt besser. Qualität hat nachgelassen.",
            "Leider nicht mehr wie erwartet. Alternativen wären besser.",
            "Enttäuscht. Die Konkurrenz bietet mittlerweile mehr fürs Geld.",
            "Nach 3 Monaten schon Probleme. Nicht mehr zu empfehlen.",
            "Service lässt zu wünschen übrig. Werde wechseln.",
            "Preis stimmt nicht mehr mit der Qualität überein.",
            "Meine zweite Bestellung - deutlich schlechter als die erste.",
            "Updates haben das Produkt eher verschlechtert als verbessert.",
            "War mal ein Fan, aber die Qualitätskontrolle scheint nachzulassen.",
            "Schade, hatte gute Erinnerungen an die Marke. Diesmal enttäuscht."
        };
        return comments[random.nextInt(comments.length)];
    }

    private String generateRealisticComment(Product product, int rating) {
        String category = product.getCategory();

        String[] generic_5 = {
                "Einfach perfekt! Übertrifft alle Erwartungen.",
                "Tolles Produkt, kann ich nur wärmstens empfehlen!",
                "Super schnelle Lieferung! Das Produkt ist einwandfrei."
        };
        String[] generic_4 = {
                "Sehr gutes Produkt, fast perfekt.",
                "Preis-Leistung ist unschlagbar.",
                "Bin sehr zufrieden, gute Qualität."
        };
        String[] generic_3 = {
                "Ganz okay, aber nichts Besonderes.",
                "Erfüllt seinen Zweck, mehr aber auch nicht.",
                "Mittelmäßig. Weder gut noch schlecht."
        };
        String[] generic_2 = {
                "Leider nicht wie erwartet, die Farbe war anders.",
                "Die Verpackung war beschädigt, das Produkt zum Glück nicht.",
                "Enttäuschend. Die Qualität lässt zu wünschen übrig."
        };
        String[] generic_1 = {
                "Nach zwei Wochen schon kaputt. Nicht zu empfehlen.",
                "Absoluter Schrott, Finger weg!",
                "Ging direkt zurück. Das Produkt war unbrauchbar."
        };

        Map<String, List<String>> cat_5 = Map.of(
                "Elektronik", List.of("Der Akku hält ewig!", "Super Display, tolle Farben.", "Die Verbindung war sofort da."),
                "Möbel", List.of("Sehr stabil und einfach aufzubauen.", "Sieht noch besser aus als auf den Bildern.", "Das Material fühlt sich hochwertig an."),
                "Beleuchtung", List.of("Macht ein wunderbar warmes Licht.", "Die Helligkeit ist perfekt.", "Installation war kinderleicht."),
                "Bürobedarf", List.of("Endlich Ordnung auf dem Schreibtisch!", "Schreibt perfekt, ohne zu schmieren.", "Sehr ergonomisch, super für lange Arbeitstage."),
                "Sport & Fitness", List.of("Training macht jetzt richtig Spaß!", "Hochwertige Verarbeitung, hält einiges aus.", "Genau das was ich gesucht habe für mein Home-Gym."),
                "Haushalt & Küche", List.of("Kochen ist jetzt so viel einfacher!", "Reinigung ist super simpel.", "Revolutioniert meine Küche!"),
                "Garten & Outdoor", List.of("Mein Garten sieht fantastisch aus!", "Wetterfest wie versprochen.", "Robuste Qualität für draußen.")
        );
        Map<String, List<String>> cat_1 = Map.of(
                "Elektronik", List.of("Der Akku war nach 2 Tagen kaputt.", "Lässt sich nicht verbinden.", "Schon beim Auspacken ein Defekt."),
                "Möbel", List.of("Teile haben gefehlt!", "Der Lack ist zerkratzt.", "Die Anleitung war unbrauchbar."),
                "Beleuchtung", List.of("Viel zu dunkel.", "Hat nach 2 Stunden geflackert.", "Wackelkontakt."),
                "Bürobedarf", List.of("Die Tinte ist ausgelaufen.", "Ist direkt zerbrochen.", "Papierstau nach 5 Seiten."),
                "Sport & Fitness", List.of("Material reißt sofort.", "Falsche Größenangaben.", "Nach erstem Training schon defekt."),
                "Haushalt & Küche", List.of("Funktioniert nicht wie beschrieben.", "Plastik riecht chemisch.", "Motor ist sofort überhitzt."),
                "Garten & Outdoor", List.of("Beim ersten Regen kaputt.", "Farbe verblasst nach einer Woche.", "Instabil, kippt ständig um.")
        );

        if (random.nextBoolean()) {
            if (rating == 5 && cat_5.containsKey(category)) {
                List<String> comments = cat_5.get(category);
                return comments.get(random.nextInt(comments.size()));
            }
            if (rating == 1 && cat_1.containsKey(category)) {
                List<String> comments = cat_1.get(category);
                return comments.get(random.nextInt(comments.size()));
            }
        }

        switch (rating) {
            case 1: return generic_1[random.nextInt(generic_1.length)];
            case 2: return generic_2[random.nextInt(generic_2.length)];
            case 3: return generic_3[random.nextInt(generic_3.length)];
            case 4: return generic_4[random.nextInt(generic_4.length)];
            case 5: return generic_5[random.nextInt(generic_5.length)];
            default: return "Ganz okay.";
        }
    }
    
    /**
     * Generiert emotionale Kommentare für die Sentiment-Analyse
     */
    private String generateEmotionalComment(Product product, int rating) {
        // 40% Chance für emotionalen Kommentar, sonst Standard
        if (random.nextDouble() > 0.4) {
            return generateRealisticComment(product, rating);
        }
        
        String category = product.getCategory();
        
        // Emotionale Kommentare nach Rating
        String[][] emotional_5 = {
            // Joy / Freude
            {"Ich bin SO GLÜCKLICH mit diesem Kauf! 😍", "Das beste Produkt ever! Bin überglücklich!", 
             "Wow wow wow! Übertrifft alle meine Erwartungen!"},
            // Satisfaction / Zufriedenheit
            {"Endlich ein Produkt das hält was es verspricht. Sehr zufrieden.",
             "Genau das was ich gesucht habe. Rundum zufrieden!",
             "Läuft einwandfrei seit Wochen. Absolute Kaufempfehlung."},
            // Surprise / Überraschung  
            {"Hätte nicht erwartet, dass es SO gut ist! Angenehm überrascht.",
             "Die Qualität hat mich wirklich überrascht! Viel besser als gedacht.",
             "Unerwartet schnelle Lieferung und top Produkt!"}
        };
        
        String[][] emotional_1 = {
            // Frustration
            {"Ich bin so FRUSTRIERT! Nichts funktioniert wie es soll!", 
             "Stundenlang versucht es zum Laufen zu bekommen. Totale Zeitverschwendung!",
             "Support ist ein Witz. Keinerlei Hilfe bekommen. Frustrierend!"},
            // Disappointment / Enttäuschung
            {"Sehr enttäuscht. Hatte mir so viel mehr erhofft...",
             "Die Bewertungen waren so gut, das Produkt leider nicht. Enttäuschend.",
             "Schade, der Hersteller war mal besser. Bin sehr enttäuscht."},
            // Anger / Ärger
            {"Das ist UNVERSCHÄMT! Für den Preis bekommt man sowas?!",
             "Bin stinksauer! Das geht gar nicht! Geld zurück!",
             "Ärgerlich! Die Produktbeschreibung ist komplett irreführend!"}
        };
        
        String[][] emotional_3 = {
            // Neutral/Mixed
            {"Naja... es ist okay, denke ich. Nicht begeistert, nicht enttäuscht.",
             "Schwer zu sagen ob ich es empfehlen würde. Hat Vor- und Nachteile.",
             "Für den Preis ist es in Ordnung, aber mehr auch nicht."}
        };
        
        if (rating >= 4) {
            String[] comments = emotional_5[random.nextInt(emotional_5.length)];
            return comments[random.nextInt(comments.length)];
        } else if (rating <= 2) {
            String[] comments = emotional_1[random.nextInt(emotional_1.length)];
            return comments[random.nextInt(comments.length)];
        } else {
            String[] comments = emotional_3[0];
            return comments[random.nextInt(comments.length)];
        }
    }
}
