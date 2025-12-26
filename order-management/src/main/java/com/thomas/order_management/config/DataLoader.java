// order-management/src/main/java/com/thomas/order_management/config/DataLoader.java
package com.thomas.order_management.config;

import com.github.javafaker.Faker;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import com.thomas.order_management.service.ReviewEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewEmbeddingService embeddingService;
    private final ReviewEmbeddingRepository embeddingRepository;
    
    // NEU: Inventory Repositories
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final StockMovementRepository stockMovementRepository;

    @Value("${app.dev.generate-embeddings:true}")
    private boolean generateEmbeddings;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${app.dev.reset-on-start:true}")
    private boolean resetOnStart;

    private final Faker faker = new Faker(Locale.GERMANY);
    private final Random random = new Random();
    
    // Trend-Produkte IDs (werden nach dem Seeding gesetzt)
    private final Set<Long> trendingUpProducts = new HashSet<>();
    private final Set<Long> trendingDownProducts = new HashSet<>();

    public DataLoader(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            ProductReviewRepository reviewRepository,
            ReviewEmbeddingService embeddingService,
            ReviewEmbeddingRepository embeddingRepository,
            WarehouseRepository warehouseRepository,
            WarehouseStockRepository warehouseStockRepository,
            StockMovementRepository stockMovementRepository
    ) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.warehouseRepository = warehouseRepository;
        this.warehouseStockRepository = warehouseStockRepository;
        this.stockMovementRepository = stockMovementRepository;
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
                userRepository.count() > 0 ||
                orderRepository.count() > 0 ||
                reviewRepository.count() > 0;

        if (!hasAnyData) loadSampleData();
    }

    private void clearAll() {
        stockMovementRepository.deleteAll();
        warehouseStockRepository.deleteAll();
        warehouseRepository.deleteAll();
        embeddingRepository.deleteAll();
        reviewRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void loadSampleData() {
        List<Customer> customers = seedCustomers();
        seedUsers();
        List<Product> products = seedProducts();
        seedOrders(customers, products);
        seedReviews(products, customers);
        seedWarehouses(products);  // NEU: Lager und Bestände
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
        Random r = new Random(); // Sicherstellen, dass 'r' verfügbar ist, oder 'faker' verwenden

        for (int i = 0; i < 200; i++) {
            String first = faker.name().firstName();
            String last = faker.name().lastName();
            String email = normalizeEmailPart(first) + "." + 
                           normalizeEmailPart(last) + "@" + 
                           faker.internet().domainName();

            Customer c = new Customer(first, last, email);
            c.setCity(faker.address().city());
            c.setCountry(faker.address().country());
            c.setZipCode(faker.address().zipCode());
            c.setAddress(faker.address().streetAddress());
            c.setPhone(faker.phoneNumber().cellPhone());
            // Setzt ein zufälliges Erstellungsdatum innerhalb der letzten 365 Tage
            LocalDateTime createdAt = LocalDateTime.now().minusDays(r.nextInt(365));
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

        Collections.shuffle(list);
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
        Collections.shuffle(shuffled);
        
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
        BigDecimal bigPrice = BigDecimal.valueOf(price);
        Product p = new Product(
                name,
                description, // Verwendet die neue Beschreibung
                bigPrice,
                faker.number().numberBetween(5, 150)
        );
        p.setCategory(category);
        
        // Behält picsum.photos bei, wie gewünscht
        p.setImageUrl("https://picsum.photos/seed/" + name + "/400/300");
        
        return p;
    }

    // *** AB HIER ALLES UNVERÄNDERT ***

    private void seedOrders(List<Customer> customers, List<Product> products) {
        List<Order> orders = new ArrayList<>();
        Set<String> uniqueOrderNumbers = new HashSet<>();

        // Mehr Bestellungen (500 statt 300) über 365 Tage verteilt
        for (int i = 0; i < 500; i++) {
            Customer c = customers.get(random.nextInt(customers.size()));
            
            String orderNumber;
            do {
                orderNumber = "ORD-" + faker.number().numberBetween(10000, 99999);
            } while (!uniqueOrderNumbers.add(orderNumber)); 

            Order o = new Order(c, orderNumber);
            OrderStatus status = OrderStatus.values()[random.nextInt(OrderStatus.values().length)];
            o.setStatus(status);
            
            // Saisonale Verteilung: Mehr Bestellungen im Nov/Dez (Weihnachtsgeschäft)
            LocalDateTime orderDate = generateSeasonalOrderDate();
            o.setOrderDate(orderDate);

            // Mehr Items pro Bestellung für Trend-Produkte
            int itemCount = 1 + random.nextInt(4);
            for (int j = 0; j < itemCount; j++) {
                Product p = selectProductWithTrend(products, orderDate);
                int qty = 1 + random.nextInt(3);
                o.addItem(p, qty);
            }

            o.setTotalAmount(o.calculateTotalAmount());
            orders.add(o);
        }
        orderRepository.saveAll(orders);
        log.info("Seeded {} orders with seasonal patterns", orders.size());
    }
    
    /**
     * Generiert ein Bestelldatum mit saisonalen Mustern
     * - Mehr Bestellungen im November/Dezember (Weihnachten)
     * - Weniger im Januar/Februar
     * - Peaks um Black Friday und Ostern
     */
    private LocalDateTime generateSeasonalOrderDate() {
        LocalDateTime now = LocalDateTime.now();
        int daysBack = random.nextInt(365);
        LocalDateTime date = now.minusDays(daysBack);
        
        Month month = date.getMonth();
        
        // Saisonale Gewichtung - höhere Chance für bestimmte Monate
        double keepProbability = switch (month) {
            case NOVEMBER, DECEMBER -> 0.95; // Weihnachtsgeschäft
            case JANUARY, FEBRUARY -> 0.6;   // Nach-Weihnachts-Flaute
            case MARCH, APRIL -> 0.8;        // Frühjahr/Ostern
            case JUNE, JULY, AUGUST -> 0.7;  // Sommerferien
            default -> 0.75;
        };
        
        // Wenn nicht behalten, neues Datum generieren (rekursiv begrenzt)
        if (random.nextDouble() > keepProbability) {
            return now.minusDays(random.nextInt(365));
        }
        
        return date;
    }
    
    /**
     * Wählt ein Produkt aus, mit Berücksichtigung von Trends basierend auf dem Datum
     */
    private Product selectProductWithTrend(List<Product> products, LocalDateTime orderDate) {
        // Für neuere Bestellungen: höhere Wahrscheinlichkeit für Trend-Up Produkte
        int daysAgo = (int) java.time.Duration.between(orderDate, LocalDateTime.now()).toDays();
        
        if (daysAgo < 90 && random.nextDouble() < 0.3) {
            // Letzte 90 Tage: 30% Chance auf Trend-Up Produkt
            for (Product p : products) {
                if (trendingUpProducts.contains(p.getId())) {
                    return p;
                }
            }
        }
        
        if (daysAgo > 180 && random.nextDouble() < 0.25) {
            // Ältere Bestellungen: 25% Chance auf Trend-Down Produkt
            for (Product p : products) {
                if (trendingDownProducts.contains(p.getId())) {
                    return p;
                }
            }
        }
        
        return products.get(random.nextInt(products.size()));
    }

    private void seedReviews(List<Product> products, List<Customer> customers) {
        List<ProductReview> reviews = new ArrayList<>();

        // 600 Reviews über 365 Tage (mehr als vorher)
        for (int i = 0; i < 600; i++) {
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

    // ===== NEU: Warehouse & Stock Seeding =====
    
    private void seedWarehouses(List<Product> products) {
        log.info("Seeding warehouses and stock...");

        // Lagerorte anlegen
        Warehouse wh1 = new Warehouse();
        wh1.setCode("WH-MAIN");
        wh1.setName("Hauptlager München");
        wh1.setDescription("Zentrales Hauptlager für alle Produktkategorien");
        wh1.setAddress("Industriestraße 15");
        wh1.setCity("München");
        wh1.setZipCode("80339");
        wh1.setCountry("Deutschland");
        wh1.setActive(true);
        wh1.setIsDefault(true);
        wh1 = warehouseRepository.save(wh1);

        Warehouse wh2 = new Warehouse();
        wh2.setCode("WH-NORD");
        wh2.setName("Außenlager Hamburg");
        wh2.setDescription("Regionales Distributionszentrum Nord");
        wh2.setAddress("Hafenweg 42");
        wh2.setCity("Hamburg");
        wh2.setZipCode("20457");
        wh2.setCountry("Deutschland");
        wh2.setActive(true);
        wh2.setIsDefault(false);
        wh2 = warehouseRepository.save(wh2);

        Warehouse wh3 = new Warehouse();
        wh3.setCode("WH-OST");
        wh3.setName("Lager Berlin");
        wh3.setDescription("Ostdeutsches Distributionszentrum");
        wh3.setAddress("Spreestraße 88");
        wh3.setCity("Berlin");
        wh3.setZipCode("10179");
        wh3.setCountry("Deutschland");
        wh3.setActive(true);
        wh3.setIsDefault(false);
        wh3 = warehouseRepository.save(wh3);
        
        // NEU: Viertes Lager für mehr Vielfalt
        Warehouse wh4 = new Warehouse();
        wh4.setCode("WH-SUED");
        wh4.setName("Lager Stuttgart");
        wh4.setDescription("Süddeutsches Distributionszentrum");
        wh4.setAddress("Automobilstraße 12");
        wh4.setCity("Stuttgart");
        wh4.setZipCode("70173");
        wh4.setCountry("Deutschland");
        wh4.setActive(true);
        wh4.setIsDefault(false);
        wh4 = warehouseRepository.save(wh4);

        List<Warehouse> warehouses = List.of(wh1, wh2, wh3, wh4);
        List<WarehouseStock> allStock = new ArrayList<>();
        List<StockMovement> movements = new ArrayList<>();
        
        // Produkte für spezielle Bestandssituationen markieren
        Set<Long> lowStockProducts = new HashSet<>();
        Set<Long> outOfStockProducts = new HashSet<>();
        Set<Long> overstockProducts = new HashSet<>();
        
        // 10% Low-Stock, 5% Out-of-Stock, 8% Überbestand
        int lowStockCount = (int) (products.size() * 0.10);
        int outOfStockCount = (int) (products.size() * 0.05);
        int overstockCount = (int) (products.size() * 0.08);
        
        List<Product> shuffledProducts = new ArrayList<>(products);
        Collections.shuffle(shuffledProducts);
        
        for (int i = 0; i < lowStockCount; i++) {
            lowStockProducts.add(shuffledProducts.get(i).getId());
        }
        for (int i = lowStockCount; i < lowStockCount + outOfStockCount; i++) {
            outOfStockProducts.add(shuffledProducts.get(i).getId());
        }
        for (int i = lowStockCount + outOfStockCount; i < lowStockCount + outOfStockCount + overstockCount; i++) {
            overstockProducts.add(shuffledProducts.get(i).getId());
        }

        // Für jedes Produkt: Bestand in ein oder mehreren Lagern anlegen
        for (Product p : products) {
            int baseStock;
            int minStock;
            int maxStock;
            
            if (outOfStockProducts.contains(p.getId())) {
                // Out-of-Stock: 0 im Hauptlager, evtl. kleine Mengen in anderen
                baseStock = 0;
                minStock = 20;
                maxStock = 100;
                allStock.add(createStockWithQuantity(wh1, p, 0, minStock, maxStock));
                
                // Eventuell noch 1-3 Stück in einem anderen Lager
                if (random.nextDouble() < 0.3) {
                    allStock.add(createStockWithQuantity(wh2, p, random.nextInt(3), minStock, maxStock));
                }
            } else if (lowStockProducts.contains(p.getId())) {
                // Low-Stock: unter Minimum
                minStock = 20 + random.nextInt(15);
                maxStock = 150 + random.nextInt(100);
                baseStock = random.nextInt(minStock);  // Unter Minimum!
                allStock.add(createStockWithQuantity(wh1, p, baseStock, minStock, maxStock));
                
                // Evtl. auch in anderen Lagern niedrig
                if (random.nextDouble() < 0.4) {
                    allStock.add(createStockWithQuantity(wh2, p, random.nextInt(10), minStock, maxStock));
                }
            } else if (overstockProducts.contains(p.getId())) {
                // Überbestand: weit über Maximum
                minStock = 15 + random.nextInt(10);
                maxStock = 100 + random.nextInt(50);
                baseStock = maxStock + 50 + random.nextInt(150);  // Über Maximum!
                allStock.add(createStockWithQuantity(wh1, p, baseStock, minStock, maxStock));
                
                // Auch in anderen Lagern hohe Bestände
                if (random.nextDouble() < 0.7) {
                    allStock.add(createStockWithQuantity(wh2, p, maxStock + random.nextInt(50), minStock, maxStock));
                }
                if (random.nextDouble() < 0.5) {
                    allStock.add(createStockWithQuantity(wh3, p, maxStock + random.nextInt(30), minStock, maxStock));
                }
            } else {
                // Normal: gesunder Bestand
                minStock = 10 + random.nextInt(20);
                maxStock = 200 + random.nextInt(300);
                baseStock = minStock + random.nextInt(maxStock - minStock);
                allStock.add(createStockWithQuantity(wh1, p, baseStock, minStock, maxStock));
                
                // Mit 60% auch in Hamburg
                if (random.nextDouble() < 0.6) {
                    int stock2 = 20 + random.nextInt(100);
                    allStock.add(createStockWithQuantity(wh2, p, stock2, minStock, maxStock));
                }
                
                // Mit 40% auch in Berlin
                if (random.nextDouble() < 0.4) {
                    int stock3 = 10 + random.nextInt(80);
                    allStock.add(createStockWithQuantity(wh3, p, stock3, minStock, maxStock));
                }
                
                // Mit 25% auch in Stuttgart
                if (random.nextDouble() < 0.25) {
                    int stock4 = 10 + random.nextInt(60);
                    allStock.add(createStockWithQuantity(wh4, p, stock4, minStock, maxStock));
                }
            }
        }

        warehouseStockRepository.saveAll(allStock);

        // Viel mehr Bewegungen erstellen (200 statt 50)
        String[] movementReasons = {
                "Lieferung vom Hersteller",
                "Kundenbestellung",
                "Inventurkorrektur",
                "Retoure vom Kunden",
                "Transfer zwischen Lagern",
                "Saisonale Auffüllung",
                "Beschädigte Ware aussortiert",
                "Express-Nachlieferung",
                "Qualitätskontrolle",
                "Rückrufaktion"
        };

        // Wareneingang und -ausgang über 90 Tage
        for (int i = 0; i < 150; i++) {
            WarehouseStock stock = allStock.get(random.nextInt(allStock.size()));
            StockMovement m = new StockMovement();

            StockMovement.MovementType type = random.nextBoolean() ? 
                    StockMovement.MovementType.GOODS_RECEIPT : 
                    StockMovement.MovementType.GOODS_ISSUE;

            m.setMovementType(type);
            m.setProduct(stock.getProduct());
            m.setQuantity(1 + random.nextInt(20));
            m.setReason(movementReasons[random.nextInt(movementReasons.length)]);
            m.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(90)));

            if (type == StockMovement.MovementType.GOODS_RECEIPT) {
                m.setTargetWarehouse(stock.getWarehouse());
                m.setReferenceType(StockMovement.ReferenceType.PURCHASE_ORDER);
                m.setReferenceNumber("PO-" + (10000 + random.nextInt(90000)));
            } else {
                m.setSourceWarehouse(stock.getWarehouse());
                m.setReferenceType(StockMovement.ReferenceType.ORDER);
                m.setReferenceNumber("ORD-" + (10000 + random.nextInt(90000)));
            }

            movements.add(m);
        }

        // Transfers zwischen Lagern (50 statt 10)
        for (int i = 0; i < 50; i++) {
            Product p = products.get(random.nextInt(products.size()));
            Warehouse from = warehouses.get(random.nextInt(warehouses.size()));
            Warehouse to = warehouses.get(random.nextInt(warehouses.size()));
            if (from.getId().equals(to.getId())) continue;

            StockMovement m = new StockMovement();
            m.setMovementType(StockMovement.MovementType.TRANSFER);
            m.setProduct(p);
            m.setSourceWarehouse(from);
            m.setTargetWarehouse(to);
            m.setQuantity(5 + random.nextInt(30));
            m.setReason("Bestandsausgleich " + from.getCity() + " → " + to.getCity());
            m.setReferenceType(StockMovement.ReferenceType.MANUAL);
            m.setReferenceNumber("TRF-" + (10000 + random.nextInt(90000)));
            m.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(60)));
            movements.add(m);
        }
        
        // Inventurkorrekturen
        for (int i = 0; i < 20; i++) {
            WarehouseStock stock = allStock.get(random.nextInt(allStock.size()));
            StockMovement m = new StockMovement();
            m.setMovementType(StockMovement.MovementType.INVENTORY_ADJUSTMENT);
            m.setProduct(stock.getProduct());
            m.setTargetWarehouse(stock.getWarehouse());
            m.setQuantity(random.nextInt(10) - 5);  // Kann positiv oder negativ sein
            m.setReason("Inventurkorrektur - " + (random.nextBoolean() ? "Mehr gefunden" : "Differenz festgestellt"));
            m.setReferenceType(StockMovement.ReferenceType.MANUAL);
            m.setReferenceNumber("INV-" + (10000 + random.nextInt(90000)));
            m.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(30)));
            movements.add(m);
        }

        stockMovementRepository.saveAll(movements);
        log.info("Seeded {} warehouses, {} stock entries ({} low-stock, {} out-of-stock, {} overstock), {} movements", 
                warehouses.size(), allStock.size(), 
                lowStockProducts.size(), outOfStockProducts.size(), overstockProducts.size(),
                movements.size());
    }

    private WarehouseStock createStock(Warehouse warehouse, Product product, int quantity, Random r) {
        return createStockWithQuantity(warehouse, product, quantity, 
                10 + random.nextInt(20), 200 + random.nextInt(300));
    }
    
    private WarehouseStock createStockWithQuantity(Warehouse warehouse, Product product, int quantity, int minStock, int maxStock) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouse(warehouse);
        stock.setProduct(product);
        stock.setQuantity(quantity);
        stock.setMinStock(minStock);
        stock.setMaxStock(maxStock);
        
        // Zufälligen Lagerplatz generieren
        char row = (char) ('A' + random.nextInt(10));
        int shelf = 1 + random.nextInt(20);
        int position = 1 + random.nextInt(5);
        stock.setBinLocation(String.format("%c-%02d-%d", row, shelf, position));
        
        return stock;
    }
}