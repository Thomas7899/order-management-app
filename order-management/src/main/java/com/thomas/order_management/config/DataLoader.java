package com.thomas.order_management.config;

import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    public DataLoader(CustomerRepository customerRepository,
                     ProductRepository productRepository,
                     OrderRepository orderRepository,
                     OrderItemRepository orderItemRepository,
                     UserRepository userRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            // Nur laden wenn Datenbank leer ist
            if (customerRepository.count() == 0) {
                loadSampleData();
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Laden der Beispieldaten: " + e.getMessage());
            e.printStackTrace();
            // Nicht das ganze System zum Absturz bringen
        }
    }

    private void loadSampleData() {
        System.out.println("Lade Beispieldaten...");

        // 1. Beispiel-Kunden erstellen
        List<Customer> customers = Arrays.asList(
            createCustomer("Max", "Mustermann", "max.mustermann@email.com", "+49 123 456789", 
                          "Musterstraße 1", "München", "80331", "Deutschland"),
            createCustomer("Anna", "Schmidt", "anna.schmidt@email.com", "+49 987 654321",
                          "Hauptstraße 15", "Berlin", "10115", "Deutschland"),
            createCustomer("Thomas", "Weber", "thomas.weber@email.com", "+49 555 123456",
                          "Gartenweg 8", "Hamburg", "20095", "Deutschland"),
            createCustomer("Lisa", "Meyer", "lisa.meyer@email.com", "+49 777 987654",
                          "Kirchplatz 3", "Köln", "50667", "Deutschland"),
            createCustomer("Michael", "Fischer", "michael.fischer@email.com", "+49 333 555777",
                          "Bahnhofstraße 12", "Frankfurt", "60311", "Deutschland")
        );
        customerRepository.saveAll(customers);

        // 2. Beispiel-Produkte erstellen
        List<Product> products = Arrays.asList(
            createProduct("Laptop Pro 15\"", "Hochleistungs-Laptop für Profis", new BigDecimal("1299.99"), 15, "Elektronik", "/images/laptop.jpg"),
            createProduct("Wireless Maus", "Ergonomische kabellose Maus", new BigDecimal("29.99"), 50, "Elektronik", "/images/mouse.jpg"),
            createProduct("Tastatur Mechanisch", "Gaming-Tastatur mit mechanischen Switches", new BigDecimal("89.99"), 25, "Elektronik", "/images/keyboard.jpg"),
            createProduct("Monitor 27\"", "4K UHD Monitor mit HDR", new BigDecimal("399.99"), 8, "Elektronik", "/images/monitor.jpg"),
            createProduct("Webcam HD", "Full HD Webcam für Videokonferenzen", new BigDecimal("79.99"), 30, "Elektronik", "/images/webcam.jpg"),
            createProduct("Schreibtischstuhl", "Ergonomischer Bürostuhl", new BigDecimal("249.99"), 12, "Möbel", "/images/chair.jpg"),
            createProduct("Schreibtisch", "Höhenverstellbarer Schreibtisch", new BigDecimal("599.99"), 5, "Möbel", "/images/desk.jpg"),
            createProduct("Tischlampe LED", "Dimmbare LED-Schreibtischlampe", new BigDecimal("39.99"), 20, "Beleuchtung", "/images/lamp.jpg"),
            createProduct("Notizbuch A4", "Hochwertiges Notizbuch kariert", new BigDecimal("12.99"), 3, "Bürobedarf", "/images/notebook.jpg"),
            createProduct("Kugelschreiber Set", "Set aus 5 hochwertigen Kugelschreibern", new BigDecimal("19.99"), 40, "Bürobedarf", "/images/pens.jpg")
        );
        productRepository.saveAll(products);

        // 3. Beispiel-Bestellungen erstellen
        createSampleOrders(customers, products);

        // 4. Beispiel-Users erstellen (falls noch keine vorhanden)
        if (userRepository.count() == 0) {
            createSampleUsers();
        }

        System.out.println("Beispieldaten erfolgreich geladen!");
        System.out.println("- " + customers.size() + " Kunden");
        System.out.println("- " + products.size() + " Produkte");
        System.out.println("- Dashboard ist jetzt mit Daten gefüllt!");
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
        // Bestellung 1 - Geliefert
        Order order1 = new Order(customers.get(0), "ORD-2024-001");
        order1.setStatus(OrderStatus.DELIVERED);
        order1.setOrderDate(LocalDateTime.now().minusDays(5));
        order1.setShippingAddress("Musterstraße 1, 80331 München");
        order1.setBillingAddress("Musterstraße 1, 80331 München");
        
        // Erstmal mit 0 speichern
        order1.setTotalAmount(BigDecimal.ZERO);
        order1 = orderRepository.save(order1);

        OrderItem item1 = new OrderItem(order1, products.get(0), 1, products.get(0).getPrice());
        OrderItem item2 = new OrderItem(order1, products.get(1), 2, products.get(1).getPrice());
        orderItemRepository.saveAll(Arrays.asList(item1, item2));
        
        // Jetzt korrekte Summe berechnen und aktualisieren
        order1.setTotalAmount(calculateOrderTotal(Arrays.asList(item1, item2)));
        orderRepository.save(order1);

        // Bestellung 2 - In Bearbeitung
        Order order2 = new Order(customers.get(1), "ORD-2024-002");
        order2.setStatus(OrderStatus.PROCESSING);
        order2.setOrderDate(LocalDateTime.now().minusDays(2));
        order2.setShippingAddress("Hauptstraße 15, 10115 Berlin");
        order2.setTotalAmount(BigDecimal.ZERO);
        order2 = orderRepository.save(order2);

        OrderItem item3 = new OrderItem(order2, products.get(3), 1, products.get(3).getPrice());
        OrderItem item4 = new OrderItem(order2, products.get(7), 1, products.get(7).getPrice());
        orderItemRepository.saveAll(Arrays.asList(item3, item4));
        
        order2.setTotalAmount(calculateOrderTotal(Arrays.asList(item3, item4)));
        orderRepository.save(order2);

        // Bestellung 3 - Ausstehend
        Order order3 = new Order(customers.get(2), "ORD-2024-003");
        order3.setStatus(OrderStatus.PENDING);
        order3.setOrderDate(LocalDateTime.now().minusHours(3));
        order3.setShippingAddress("Gartenweg 8, 20095 Hamburg");
        order3.setTotalAmount(BigDecimal.ZERO);
        order3 = orderRepository.save(order3);

        OrderItem item5 = new OrderItem(order3, products.get(5), 1, products.get(5).getPrice());
        OrderItem item6 = new OrderItem(order3, products.get(6), 1, products.get(6).getPrice());
        orderItemRepository.saveAll(Arrays.asList(item5, item6));
        
        order3.setTotalAmount(calculateOrderTotal(Arrays.asList(item5, item6)));
        orderRepository.save(order3);

        // Bestellung 4 - Bestätigt
        Order order4 = new Order(customers.get(3), "ORD-2024-004");
        order4.setStatus(OrderStatus.CONFIRMED);
        order4.setOrderDate(LocalDateTime.now().minusHours(8));
        order4.setTotalAmount(BigDecimal.ZERO);
        order4 = orderRepository.save(order4);

        OrderItem item7 = new OrderItem(order4, products.get(2), 1, products.get(2).getPrice());
        orderItemRepository.save(item7);
        
        order4.setTotalAmount(calculateOrderTotal(Arrays.asList(item7)));
        orderRepository.save(order4);
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
}