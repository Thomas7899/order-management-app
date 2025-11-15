// order-management/src/main/java/com/thomas/order_management/config/DataLoader.java
package com.thomas.order_management.config;

import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import com.thomas.order_management.service.ReviewEmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    /**
     * Embeddings sollen in der DEV-Umgebung standardmäßig erzeugt werden.
     */
    @Value("${app.dev.generate-embeddings:true}")
    private boolean generateEmbeddings;

    /**
     * Wenn true: bei jedem Start DEV-Daten zurücksetzen und neu seeden.
     * Für Produktion unbedingt auf false setzen bzw. eigenes Profil verwenden.
     */
    @Value("${app.dev.reset-on-start:true}")
    private boolean resetOnStart;

    public DataLoader(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            ProductReviewRepository reviewRepository,
            ReviewEmbeddingService embeddingService
    ) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    public void run(String... args) {

        System.out.println("🔍 Dev seed startup...");

        if (resetOnStart) {
            System.out.println("⚠️ app.dev.reset-on-start=true → clearing and reseeding dev data...");
            clearAll();
            loadSampleData();
            System.out.println("🎉 Dev seed done (reset mode).");
            return;
        }

        boolean hasAnyData =
                customerRepository.count() > 0 ||
                productRepository.count() > 0 ||
                userRepository.count() > 0 ||
                orderRepository.count() > 0 ||
                reviewRepository.count() > 0;

        if (hasAnyData) {
            System.out.println("✅ Existing data detected and reset-on-start=false → skipping seeding.");
            return;
        }

        System.out.println("🌱 No data found → seeding initial dataset...");
        loadSampleData();
        System.out.println("🎉 Dev seed done.");
    }

    private void clearAll() {
        // Reihenfolge beachten wegen FK-Constraints
        reviewRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void loadSampleData() {
        List<Customer> customers = seedCustomers();
        List<User> users = seedUsers();
        List<Product> products = seedProducts();
        seedOrders(customers, products);
        seedReviews(products, customers);
    }

    // -------------------------------- CUSTOMERS --------------------------------
    private List<Customer> seedCustomers() {

        List<String> firstNames = Arrays.asList(
                "Max","Julia","Anna","Tim","Laura","Jan","Lisa","Tom","Sophie","Leon"
        );

        List<String> lastNames = Arrays.asList(
                "Müller","Schulz","Becker","Fischer","Weber"
        );

        List<Customer> customers = new ArrayList<>();
        Random r = new Random();

        for (int i = 0; i < 100; i++) {
            String fn = firstNames.get(r.nextInt(firstNames.size()));
            String ln = lastNames.get(r.nextInt(lastNames.size()));
            String email = (fn + "." + ln + i + "@example.com").toLowerCase();

            Customer c = new Customer(fn, ln, email);
            customers.add(c);
        }

        return customerRepository.saveAll(customers);
    }

    // -------------------------------- USERS --------------------------------
    private List<User> seedUsers() {

        List<User> list = Arrays.asList(
                new User("Marie Reuter", "marie@example.com", "User"),
                new User("Jonas Meier", "jonas@example.com", "User"),
                new User("Paul Richter", "paul@example.com", "User"),
                new User("Admin", "admin@example.com", "Admin")
        );

        return userRepository.saveAll(list);
    }

    // -------------------------------- PRODUCTS --------------------------------
    private List<Product> seedProducts() {

        List<Product> list = new ArrayList<>();

        Product p1 = new Product(
                "Laptop Pro 15\"",
                "Leistungsstarker Laptop für Entwickler & Designer",
                BigDecimal.valueOf(1299.99),
                20
        );
        // Kategorie passend zur Trend-Analyse
        p1.setCategory("Elektronik");

        Product p2 = new Product(
                "Ultrabook Air 13\"",
                "Leichtes Ultrabook mit langer Akkulaufzeit",
                BigDecimal.valueOf(999.00),
                20
        );
        p2.setCategory("Elektronik");

        Product p3 = new Product(
                "Gaming Maus RGB",
                "Gaming-Maus mit 10.000 DPI und RGB-Beleuchtung",
                BigDecimal.valueOf(59.99),
                50
        );
        p3.setCategory("Zubehör");

        list.add(p1);
        list.add(p2);
        list.add(p3);

        return productRepository.saveAll(list);
    }

    // -------------------------------- ORDERS --------------------------------
    private void seedOrders(List<Customer> customers, List<Product> products) {

        List<Order> orders = new ArrayList<>();
        Random r = new Random();

        for (int i = 0; i < 200; i++) {

            Customer c = customers.get(r.nextInt(customers.size()));

            Order o = new Order(c, "ORD-" + (10000 + i));

            // Mischung unterschiedlicher Status für Dashboard-Statistik
            OrderStatus status;
            int s = r.nextInt(5);
            switch (s) {
                case 0 -> status = OrderStatus.DELIVERED;
                case 1 -> status = OrderStatus.SHIPPED;
                case 2 -> status = OrderStatus.CONFIRMED;
                case 3 -> status = OrderStatus.PENDING;
                default -> status = OrderStatus.CANCELLED;
            }
            o.setStatus(status);

            // Einige Bestellungen heute / kürzlich, Rest über letztes Jahr verteilt
            if (i < 5) {
                // ein paar „heutige“ Bestellungen
                o.setOrderDate(LocalDateTime.now().minusHours(r.nextInt(23)));
            } else {
                o.setOrderDate(LocalDateTime.now().minusDays(r.nextInt(365)));
            }

            int itemCount = 1 + r.nextInt(3);

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

    // -------------------------------- REVIEWS --------------------------------
    private void seedReviews(List<Product> products, List<Customer> customers) {

        List<ProductReview> reviews = new ArrayList<>();
        Random r = new Random();

        String[] positive = {
                "Great product, very fast shipping.",
                "Really love this laptop, works great for development.",
                "Excellent quality, would definitely buy again.",
                "Super zufrieden, alles wie beschrieben!",
                "Top Preis-Leistungs-Verhältnis, klare Empfehlung."
        };

        String[] negative = {
                "Very bad quality, broke after a week.",
                "Not worth the price, I am disappointed.",
                "Slow delivery and poor support.",
                "Qualität leider sehr schlecht, nicht zu empfehlen.",
                "Erwartungen nicht erfüllt, würde ich nicht wieder kaufen."
        };

        String[] neutral = {
            "Product is okay, does the job.",
            "Average quality, nothing special.",
            "Packaging was fine, product as described.",
            "Insgesamt in Ordnung, aber nichts Besonderes.",
            "Standardqualität, für den Preis akzeptabel."
        };

        for (int i = 0; i < 200; i++) {

            Product product = products.get(r.nextInt(products.size()));
            Customer customer = customers.get(r.nextInt(customers.size()));

            ProductReview review = new ProductReview();
            review.setProduct(product);
            review.setCustomer(customer);

            // Stimmung bestimmen und passende Bewertung setzen
            int mood = r.nextInt(3);
            String comment;
            int rating;

            if (mood == 0) { // positiv
                comment = positive[r.nextInt(positive.length)];
                rating = 4 + r.nextInt(2); // 4–5
            } else if (mood == 1) { // negativ
                comment = negative[r.nextInt(negative.length)];
                rating = 1 + r.nextInt(2); // 1–2
            } else { // neutral
                comment = neutral[r.nextInt(neutral.length)];
                rating = 2 + r.nextInt(3); // 2–4
            }

            review.setComment(comment);
            review.setRating(rating);

            // Für Trend-Analysen: Fokus auf letzte 90 Tage
            review.setCreatedAt(LocalDateTime.now().minusDays(r.nextInt(90)));

            reviews.add(review);
        }

        List<ProductReview> savedReviews = reviewRepository.saveAll(reviews);

        if (generateEmbeddings) {
            System.out.println("🧠 Creating embeddings for " + savedReviews.size() + " reviews...");
            savedReviews.forEach(embeddingService::createEmbedding);
        } else {
            System.out.println("⚠️ Embeddings disabled in DEV mode (app.dev.generate-embeddings=false).");
        }
    }
}
