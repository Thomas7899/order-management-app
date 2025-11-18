// order-management/src/main/java/com/thomas/order_management/config/DataLoader.java
package com.thomas.order_management.config;

import com.github.javafaker.Faker;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import com.thomas.order_management.service.ReviewEmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewEmbeddingService embeddingService;
    private final ReviewEmbeddingRepository embeddingRepository;

    @Value("${app.dev.generate-embeddings:true}")
    private boolean generateEmbeddings;

    @Value("${app.dev.reset-on-start:true}")
    private boolean resetOnStart;

    private final Faker faker = new Faker(Locale.GERMANY);

    public DataLoader(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            ProductReviewRepository reviewRepository,
            ReviewEmbeddingService embeddingService,
            ReviewEmbeddingRepository embeddingRepository
    ) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
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

        // Kategorie: Elektronik
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
                "GoPro HERO12 Black - Action-Kamera"
        );
        for (String name : elektronikNamen) {
            String desc = generateAmazonLikeDescription(catElektronik, name);
            Product p = createProduct(name, desc, catElektronik, faker.number().randomDouble(2, 100, 3000));
            list.add(p);
        }

        // Kategorie: Möbel
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
                "Kompakter Garderobenschrank 'Entry'"
        );
        for (String name : moebelNamen) {
            String desc = generateAmazonLikeDescription(catMoebel, name);
            Product p = createProduct(name, desc, catMoebel, faker.number().randomDouble(2, 80, 2000));
            list.add(p);
        }
        
        // Kategorie: Beleuchtung
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
                "Kinderzimmer-Deckenlampe 'Sternenhimmel'"
        );
        for (String name : lichtNamen) {
            String desc = generateAmazonLikeDescription(catLicht, name);
            Product p = createProduct(name, desc, catLicht, faker.number().randomDouble(2, 20, 450));
            list.add(p);
        }

        // Kategorie: Bürobedarf
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
                "Post-it Haftnotizen (Cube, Neonfarben)"
        );
        for (String name : bueroNamen) {
            String desc = generateAmazonLikeDescription(catBuero, name);
            Product p = createProduct(name, desc, catBuero, faker.number().randomDouble(2, 10, 300));
            list.add(p);
        }
        
        // Fülle mit einigen zufälligen Produkten auf, falls gewünscht
        // (Für dieses Beispiel lassen wir es bei den spezifischen Produkten)

        Collections.shuffle(list);
        return productRepository.saveAll(list);
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
        Random r = new Random();

        for (int i = 0; i < 300; i++) {
            Customer c = customers.get(r.nextInt(customers.size()));
            
            String orderNumber;
            do {
                orderNumber = "ORD-" + faker.number().numberBetween(10000, 99999);
            } while (!uniqueOrderNumbers.add(orderNumber)); 

            Order o = new Order(c, orderNumber);
            OrderStatus status = OrderStatus.values()[r.nextInt(OrderStatus.values().length)];
            o.setStatus(status);
            o.setOrderDate(LocalDateTime.now().minusDays(r.nextInt(365)));

            int itemCount = 1 + r.nextInt(4);
            for (int j = 0; j < itemCount; j++) {
                Product p = products.get(r.nextInt(products.size()));
                int qty = 1 + r.nextInt(3);
                o.addItem(p, qty);
            }

            o.setTotalAmount(o.calculateTotalAmount());
            orders.add(o);
        }
        orderRepository.saveAll(orders);
    }

    private void seedReviews(List<Product> products, List<Customer> customers) {
        List<ProductReview> reviews = new ArrayList<>();
        Random r = new Random();

        for (int i = 0; i < 400; i++) {
            Product product = products.get(r.nextInt(products.size()));
            Customer customer = customers.get(r.nextInt(customers.size()));

            int rating = r.nextInt(1, 6);
            
            String comment = generateRealisticComment(product, rating);

            ProductReview review = new ProductReview();
            review.setProduct(product);
            review.setCustomer(customer);
            review.setRating(rating);
            review.setComment(comment);
            review.setCreatedAt(LocalDateTime.now().minusDays(r.nextInt(180)));
            reviews.add(review);
        }

        if (products.size() > 10 && !customers.isEmpty()) {
            Product anomalyProduct = products.get(10); 
            Customer c = customers.get(0); 

            for (int i = 0; i < 15; i++) {
                ProductReview anomalyReview = new ProductReview();
                anomalyReview.setProduct(anomalyProduct);
                anomalyReview.setCustomer(c);
                anomalyReview.setRating(1); 
                anomalyReview.setComment("Direkt nach dem Auspacken defekt! Was soll das?"); 
                anomalyReview.setCreatedAt(LocalDateTime.now().minusDays(r.nextInt(5))); 
                reviews.add(anomalyReview);
            }
        }

        List<ProductReview> savedReviews = reviewRepository.saveAll(reviews);

        if (generateEmbeddings) {
            savedReviews.forEach(embeddingService::createEmbedding);
        }
    }

    private String generateRealisticComment(Product product, int rating) {
        String category = product.getCategory();
        Random r = new Random();

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
                "Bürobedarf", List.of("Endlich Ordnung auf dem Schreibtisch!", "Schreibt perfekt, ohne zu schmieren.", "Sehr ergonomisch, super für lange Arbeitstage.")
        );
        Map<String, List<String>> cat_1 = Map.of(
                "Elektronik", List.of("Der Akku war nach 2 Tagen kaputt.", "Lässt sich nicht verbinden.", "Schon beim Auspacken ein Defekt."),
                "Möbel", List.of("Teile haben gefehlt!", "Der Lack ist zerkratzt.", "Die Anleitung war unbrauchbar."),
                "Beleuchtung", List.of("Viel zu dunkel.", "Hat nach 2 Stunden geflackert.", "Wackelkontakt."),
                "Bürobedarf", List.of("Die Tinte ist ausgelaufen.", "Ist direkt zerbrochen.", "Papierstau nach 5 Seiten.")
        );

        if (r.nextBoolean()) {
            if (rating == 5 && cat_5.containsKey(category)) {
                List<String> comments = cat_5.get(category);
                return comments.get(r.nextInt(comments.size()));
            }
            if (rating == 1 && cat_1.containsKey(category)) {
                List<String> comments = cat_1.get(category);
                return comments.get(r.nextInt(comments.size()));
            }
        }

        switch (rating) {
            case 1: return generic_1[r.nextInt(generic_1.length)];
            case 2: return generic_2[r.nextInt(generic_2.length)];
            case 3: return generic_3[r.nextInt(generic_3.length)];
            case 4: return generic_4[r.nextInt(generic_4.length)];
            case 5: return generic_5[r.nextInt(generic_5.length)];
            default: return "Ganz okay.";
        }
    }
}