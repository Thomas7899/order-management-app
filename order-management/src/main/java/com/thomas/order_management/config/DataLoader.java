// src/main/java/com/thomas/order_management/config/DataLoader.java
package com.thomas.order_management.config;

import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import com.thomas.order_management.service.ReviewEmbeddingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewEmbeddingService embeddingService;

    public DataLoader(CustomerRepository customerRepository,
                      ProductRepository productRepository,
                      OrderRepository orderRepository,
                      OrderItemRepository orderItemRepository,
                      UserRepository userRepository,
                      ProductReviewRepository reviewRepository,
                      ReviewEmbeddingService embeddingService) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    public void run(String... args) {
        try {
            if (customerRepository.count() == 0) {
                loadSampleData();
            } else {
                System.out.println("✅ Database already contains sample data. Skipping seeding.");
            }
        } catch (Exception e) {
            System.err.println("❌ Fehler beim Laden der Beispieldaten: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadSampleData() {
        System.out.println("🌱 Lade Beispieldaten...");

        List<Customer> customers = createSampleCustomers();
        customerRepository.saveAll(customers);

        List<Product> products = createSampleProducts();
        productRepository.saveAll(products);

        createSampleOrders(customers, products);

        if (userRepository.count() == 0) {
            createSampleUsers();
        }

        createSampleReviews(products);

        System.out.println("✅ Beispieldaten erfolgreich geladen!");
        System.out.println("- " + customers.size() + " Kunden");
        System.out.println("- " + products.size() + " Produkte");
        System.out.println("- " + orderRepository.count() + " Bestellungen");
        System.out.println("- " + reviewRepository.count() + " Produktbewertungen");
    }

    private List<Customer> createSampleCustomers() {
        return Arrays.asList(
                createCustomer("Max", "Mustermann", "max.mustermann@email.com", "+49 123 456789", "Musterstraße 1", "München", "80331", "Deutschland"),
                createCustomer("Anna", "Schmidt", "anna.schmidt@email.com", "+49 987 654321", "Hauptstraße 15", "Berlin", "10115", "Deutschland"),
                createCustomer("Thomas", "Weber", "thomas.weber@email.com", "+49 555 123456", "Gartenweg 8", "Hamburg", "20095", "Deutschland"),
                createCustomer("Lisa", "Meyer", "lisa.meyer@email.com", "+49 777 987654", "Kirchplatz 3", "Köln", "50667", "Deutschland"),
                createCustomer("Michael", "Fischer", "michael.fischer@email.com", "+49 333 555777", "Bahnhofstraße 12", "Frankfurt", "60311", "Deutschland"),
                createCustomer("Sabine", "Keller", "sabine.keller@email.com", "+49 160 112233", "Schulallee 42", "Stuttgart", "70173", "Deutschland"),
                createCustomer("Jürgen", "Bauer", "juergen.bauer@email.com", "+49 171 445566", "Am Markt 1", "Leipzig", "04109", "Deutschland"),
                createCustomer("Katrin", "Wolf", "katrin.wolf@email.com", "+49 151 778899", "Uferweg 23", "Düsseldorf", "40213", "Deutschland"),
                createCustomer("Stefan", "Neumann", "stefan.neumann@email.com", "+49 176 123123", "Parkring 5", "Dresden", "01067", "Deutschland"),
                createCustomer("Julia", "Richter", "julia.richter@email.com", "+49 163 456456", "Bergstraße 11", "Nürnberg", "90402", "Deutschland")
        );
    }

    private List<Product> createSampleProducts() {
        return Arrays.asList(
                createProduct("Laptop Pro 15\"", "Hochleistungs-Laptop für Profis", new BigDecimal("1299.99"), 15, "Elektronik", "/images/laptop.jpg"),
                createProduct("Wireless Maus", "Ergonomische kabellose Maus", new BigDecimal("29.99"), 50, "Elektronik", "/images/mouse.jpg"),
                createProduct("Tastatur Mechanisch", "Gaming-Tastatur mit mechanischen Switches", new BigDecimal("89.99"), 25, "Elektronik", "/images/keyboard.jpg"),
                createProduct("Monitor 27\"", "4K UHD Monitor mit HDR", new BigDecimal("399.99"), 8, "Elektronik", "/images/monitor.jpg"),
                createProduct("Webcam HD", "Full HD Webcam für Videokonferenzen", new BigDecimal("79.99"), 30, "Elektronik", "/images/webcam.jpg"),
                createProduct("Schreibtischstuhl", "Ergonomischer Bürostuhl", new BigDecimal("249.99"), 12, "Möbel", "/images/chair.jpg"),
                createProduct("Schreibtisch", "Höhenverstellbarer Schreibtisch", new BigDecimal("599.99"), 5, "Möbel", "/images/desk.jpg"),
                createProduct("Tischlampe LED", "Dimmbare LED-Schreibtischlampe", new BigDecimal("39.99"), 20, "Beleuchtung", "/images/lamp.jpg"),
                createProduct("Notizbuch A4", "Hochwertiges Notizbuch kariert", new BigDecimal("12.99"), 3, "Bürobedarf", "/images/notebook.jpg"),
                createProduct("Kugelschreiber Set", "Set aus 5 hochwertigen Kugelschreibern", new BigDecimal("19.99"), 40, "Bürobedarf", "/images/pens.jpg"),
                createProduct("Kaffeebecher 'Code'", "Keramikbecher mit lustigem Spruch", new BigDecimal("14.99"), 100, "Bürobedarf", "/images/mug.jpg"),
                createProduct("Noise-Cancelling Kopfhörer", "Premium Kopfhörer für ungestörtes Arbeiten", new BigDecimal("349.00"), 22, "Elektronik", "/images/headphones.jpg"),
                createProduct("Ergonomische Fußstütze", "Verbessert die Haltung am Schreibtisch", new BigDecimal("45.50"), 35, "Möbel", "/images/footrest.jpg"),
                createProduct("Whiteboard 120x90cm", "Magnetisches Whiteboard für Notizen", new BigDecimal("75.00"), 18, "Bürobedarf", "/images/whiteboard.jpg"),
                createProduct("Pflanze 'Monstera'", "Pflegeleichte Zimmerpflanze fürs Büro", new BigDecimal("25.00"), 30, "Dekoration", "/images/plant.jpg"),
                createProduct("USB-C Hub", "Adapter mit HDMI, USB 3.0 und SD-Kartenleser", new BigDecimal("59.90"), 60, "Elektronik", "/images/hub.jpg"),
                createProduct("Laptop-Ständer", "Erhöht den Laptop für bessere Ergonomie", new BigDecimal("35.00"), 45, "Zubehör", "/images/laptopstand.jpg"),
                createProduct("Aktenvernichter", "Sicherheitsstufe P-4", new BigDecimal("129.99"), 10, "Bürobedarf", "/images/shredder.jpg"),
                createProduct("Kaffeemaschine 'Espresso'", "Vollautomat für perfekten Kaffee", new BigDecimal("799.00"), 7, "Küche", "/images/coffeemachine.jpg"),
                createProduct("Wandkalender 2025", "Jahresplaner für die Wand", new BigDecimal("9.99"), 150, "Bürobedarf", "/images/calendar.jpg")
        );
    }

    private Customer createCustomer(String firstName, String lastName, String email, String phone,
                                    String address, String city, String zipCode, String country) {
        Customer customer = new Customer(firstName, lastName, email);
        customer.setPhone(phone);
        customer.setAddress(address);
        customer.setCity(city);
        customer.setZipCode(zipCode);
        customer.setCountry(country);
        return customer;
    }

    private Product createProduct(String name, String description, BigDecimal price, int stock, String category, String imageUrl) {
        Product product = new Product(name, description, price, stock);
        product.setCategory(category);
        product.setImageUrl(imageUrl);
        return product;
    }

    private void createSampleOrders(List<Customer> customers, List<Product> products) {
        Random random = new Random();
        OrderStatus[] statuses = OrderStatus.values();

        for (int i = 1; i <= 50; i++) {
            Customer randomCustomer = customers.get(random.nextInt(customers.size()));
            long randomDaysAgo = ThreadLocalRandom.current().nextLong(1, 365);
            LocalDateTime randomOrderDate = LocalDateTime.now().minusDays(randomDaysAgo).minusHours(random.nextInt(24));
            OrderStatus randomStatus = statuses[random.nextInt(statuses.length)];

            Collections.shuffle(products);
            int numberOfItems = random.nextInt(4) + 1;
            List<Product> itemsForOrder = products.subList(0, numberOfItems);
            createOrderWithItems(randomCustomer, itemsForOrder, randomStatus, randomOrderDate, i);
        }
    }

    private void createOrderWithItems(Customer customer, List<Product> products, OrderStatus status, LocalDateTime orderDate, int orderIndex) {
        Order order = new Order(customer, "ORD-" + (2024000 + orderIndex));
        order.setStatus(status);
        order.setOrderDate(orderDate);
        order.setShippingAddress(customer.getAddress() + ", " + customer.getZipCode() + " " + customer.getCity());
        order.setBillingAddress(customer.getAddress() + ", " + customer.getZipCode() + " " + customer.getCity());
        order.setTotalAmount(BigDecimal.ZERO);

        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (Product product : products) {
            int quantity = new Random().nextInt(3) + 1;
            orderItems.add(new OrderItem(savedOrder, product, quantity, product.getPrice()));
        }
        orderItemRepository.saveAll(orderItems);

        BigDecimal totalAmount = calculateOrderTotal(orderItems);
        savedOrder.setTotalAmount(totalAmount);
        orderRepository.save(savedOrder);
    }

    private BigDecimal calculateOrderTotal(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void createSampleUsers() {
        User admin = new User();
        admin.setName("Administrator");
        admin.setEmail("admin@company.com");
        admin.setRole("Admin");

        User manager = new User();
        manager.setName("Max Manager");
        manager.setEmail("manager@company.com");
        manager.setRole("Manager");

        userRepository.saveAll(Arrays.asList(admin, manager));
    }

    private void createSampleReviews(List<Product> products) {
        if (reviewRepository.count() > 0) {
            System.out.println("✅ Reviews already exist, skipping...");
            return;
        }

        System.out.println("🌱 Generating sample product reviews with embeddings...");

        List<ProductReview> reviews = new ArrayList<>();

        ProductReview r1 = new ProductReview();
        r1.setProduct(products.get(0));
        r1.setComment("Excellent build quality, but a bit heavy.");
        r1.setRating(4);
        reviews.add(r1);

        ProductReview r2 = new ProductReview();
        r2.setProduct(products.get(1));
        r2.setComment("Smooth tracking and comfortable grip. Great value!");
        r2.setRating(5);
        reviews.add(r2);

        ProductReview r3 = new ProductReview();
        r3.setProduct(products.get(3));
        r3.setComment("Crisp display and vibrant colors. Worth the price.");
        r3.setRating(5);
        reviews.add(r3);

        reviewRepository.saveAll(reviews);

        for (ProductReview r : reviews) {
            embeddingService.createEmbedding(r);
        }

        System.out.println("✅ Reviews and embeddings successfully created!");
    }
}
