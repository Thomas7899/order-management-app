package com.thomas.order_management.config;

import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(CustomerRepository customerRepository, 
                                 ProductRepository productRepository,
                                 OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 UserRepository userRepository) {
        return args -> {
            // Nur initialisieren wenn noch keine Daten vorhanden sind
            if (customerRepository.count() == 0) {
                initializeData(customerRepository, productRepository, orderRepository, orderItemRepository, userRepository);
            }
        };
    }

    private void initializeData(CustomerRepository customerRepository,
                              ProductRepository productRepository,
                              OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              UserRepository userRepository) {
        
        // Beispiel-Users erstellen
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setName("Admin User");
            admin.setEmail("admin@example.com");
            admin.setRole("Admin");
            userRepository.save(admin);

            User manager = new User();
            manager.setName("Manager User");
            manager.setEmail("manager@example.com");
            manager.setRole("Manager");
            userRepository.save(manager);
        }

        // Beispiel-Kunden erstellen
        Customer customer1 = new Customer("Max", "Mustermann", "max.mustermann@email.com");
        customer1.setPhone("+49 123 456789");
        customer1.setAddress("Musterstraße 123");
        customer1.setCity("München");
        customer1.setZipCode("80333");
        customer1.setCountry("Deutschland");
        customerRepository.save(customer1);

        Customer customer2 = new Customer("Anna", "Schmidt", "anna.schmidt@email.com");
        customer2.setPhone("+49 987 654321");
        customer2.setAddress("Beispielweg 456");
        customer2.setCity("Berlin");
        customer2.setZipCode("10115");
        customer2.setCountry("Deutschland");
        customerRepository.save(customer2);

        Customer customer3 = new Customer("Tom", "Weber", "tom.weber@email.com");
        customer3.setPhone("+49 555 123456");
        customer3.setAddress("Testplatz 789");
        customer3.setCity("Hamburg");
        customer3.setZipCode("20095");
        customer3.setCountry("Deutschland");
        customerRepository.save(customer3);

        // Beispiel-Produkte erstellen
        Product laptop = new Product("MacBook Pro", "Hochleistungs-Laptop für Profis", new BigDecimal("2499.99"), 15);
        laptop.setCategory("Computer");
        laptop.setImageUrl("https://example.com/macbook.jpg");
        productRepository.save(laptop);

        Product smartphone = new Product("iPhone 15", "Neuestes iPhone Modell", new BigDecimal("999.99"), 25);
        smartphone.setCategory("Smartphone");
        smartphone.setImageUrl("https://example.com/iphone.jpg");
        productRepository.save(smartphone);

        Product tablet = new Product("iPad Air", "Leichtes und kraftvolles Tablet", new BigDecimal("699.99"), 20);
        tablet.setCategory("Tablet");
        tablet.setImageUrl("https://example.com/ipad.jpg");
        productRepository.save(tablet);

        Product headphones = new Product("AirPods Pro", "Wireless Kopfhörer mit Noise Cancellation", new BigDecimal("279.99"), 3); // Niedriger Lagerbestand
        headphones.setCategory("Audio");
        headphones.setImageUrl("https://example.com/airpods.jpg");
        productRepository.save(headphones);

        Product monitor = new Product("Dell UltraSharp Monitor", "27 Zoll 4K Monitor", new BigDecimal("449.99"), 12);
        monitor.setCategory("Monitor");
        monitor.setImageUrl("https://example.com/monitor.jpg");
        productRepository.save(monitor);

        Product keyboard = new Product("Logitech MX Keys", "Wireless Tastatur", new BigDecimal("99.99"), 1); // Sehr niedriger Lagerbestand
        keyboard.setCategory("Zubehör");
        keyboard.setImageUrl("https://example.com/keyboard.jpg");
        productRepository.save(keyboard);

        // Beispiel-Bestellungen erstellen
        Order order1 = new Order(customer1, "ORD-2024-001");
        order1.setStatus(OrderStatus.DELIVERED);
        order1.setShippingAddress(customer1.getAddress() + ", " + customer1.getCity());
        order1.setBillingAddress(customer1.getAddress() + ", " + customer1.getCity());
        order1.setOrderDate(LocalDateTime.now().minusDays(5));
        order1.setTotalAmount(BigDecimal.ZERO);
        orderRepository.save(order1);

        OrderItem orderItem1_1 = new OrderItem(order1, laptop, 1, laptop.getPrice());
        OrderItem orderItem1_2 = new OrderItem(order1, headphones, 2, headphones.getPrice());
        order1.setTotalAmount(orderItem1_1.getTotalPrice().add(orderItem1_2.getTotalPrice()));
        orderRepository.save(order1);
        orderItemRepository.saveAll(Arrays.asList(orderItem1_1, orderItem1_2));

        Order order2 = new Order(customer2, "ORD-2024-002");
        order2.setStatus(OrderStatus.PROCESSING);
        order2.setShippingAddress(customer2.getAddress() + ", " + customer2.getCity());
        order2.setBillingAddress(customer2.getAddress() + ", " + customer2.getCity());
        order2.setOrderDate(LocalDateTime.now().minusDays(2));
        order2.setTotalAmount(BigDecimal.ZERO);
        orderRepository.save(order2);

        OrderItem orderItem2_1 = new OrderItem(order2, smartphone, 1, smartphone.getPrice());
        OrderItem orderItem2_2 = new OrderItem(order2, tablet, 1, tablet.getPrice());
        order2.setTotalAmount(orderItem2_1.getTotalPrice().add(orderItem2_2.getTotalPrice()));
        orderRepository.save(order2);
        orderItemRepository.saveAll(Arrays.asList(orderItem2_1, orderItem2_2));

        Order order3 = new Order(customer3, "ORD-2024-003");
        order3.setStatus(OrderStatus.PENDING);
        order3.setShippingAddress(customer3.getAddress() + ", " + customer3.getCity());
        order3.setBillingAddress(customer3.getAddress() + ", " + customer3.getCity());
        order3.setOrderDate(LocalDateTime.now().minusHours(3));
        order3.setTotalAmount(BigDecimal.ZERO);
        orderRepository.save(order3);

        OrderItem orderItem3_1 = new OrderItem(order3, monitor, 2, monitor.getPrice());
        OrderItem orderItem3_2 = new OrderItem(order3, keyboard, 1, keyboard.getPrice());
        order3.setTotalAmount(orderItem3_1.getTotalPrice().add(orderItem3_2.getTotalPrice()));
        orderRepository.save(order3);
        orderItemRepository.saveAll(Arrays.asList(orderItem3_1, orderItem3_2));

        Order order4 = new Order(customer1, "ORD-2024-004");
        order4.setStatus(OrderStatus.SHIPPED);
        order4.setShippingAddress(customer1.getAddress() + ", " + customer1.getCity());
        order4.setBillingAddress(customer1.getAddress() + ", " + customer1.getCity());
        order4.setOrderDate(LocalDateTime.now().minusHours(12));
        order4.setTotalAmount(BigDecimal.ZERO);
        orderRepository.save(order4);

        OrderItem orderItem4_1 = new OrderItem(order4, smartphone, 1, smartphone.getPrice());
        order4.setTotalAmount(orderItem4_1.getTotalPrice());
        orderRepository.save(order4);
        orderItemRepository.save(orderItem4_1);

        System.out.println("✅ Beispieldaten erfolgreich initialisiert:");
        System.out.println("   - " + customerRepository.count() + " Kunden");
        System.out.println("   - " + productRepository.count() + " Produkte");
        System.out.println("   - " + orderRepository.count() + " Bestellungen");
        System.out.println("   - " + userRepository.count() + " Benutzer");
    }
}