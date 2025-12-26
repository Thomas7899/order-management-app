package com.thomas.order_management.repository;

import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.Order;
import com.thomas.order_management.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstests für OrderRepository.
 * Testet JPA-Queries mit einer eingebetteten H2-Datenbank.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("OrderRepository Integration Tests")
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    private Customer testCustomer;
    private Order order1;
    private Order order2;

    @BeforeEach
    void setUp() {
        // Test-Customer erstellen und persistieren
        testCustomer = new Customer("Test", "User", "test@example.com");
        entityManager.persist(testCustomer);

        // Test-Orders erstellen
        order1 = new Order(testCustomer, "ORD-001");
        order1.setStatus(OrderStatus.DELIVERED);
        order1.setTotalAmount(new BigDecimal("100.00"));
        order1.setOrderDate(LocalDateTime.now().minusDays(5));
        entityManager.persist(order1);

        order2 = new Order(testCustomer, "ORD-002");
        order2.setStatus(OrderStatus.PENDING);
        order2.setTotalAmount(new BigDecimal("250.00"));
        order2.setOrderDate(LocalDateTime.now().minusDays(2));
        entityManager.persist(order2);

        entityManager.flush();
    }

    @Nested
    @DisplayName("findByOrderNumber Tests")
    class FindByOrderNumberTests {

        @Test
        @DisplayName("Sollte Bestellung nach Bestellnummer finden")
        void shouldFindOrderByOrderNumber() {
            // When
            Optional<Order> found = orderRepository.findByOrderNumber("ORD-001");

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().getOrderNumber()).isEqualTo("ORD-001");
            assertThat(found.get().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("Sollte Empty Optional zurückgeben für nicht existierende Bestellnummer")
        void shouldReturnEmptyForNonExistentOrderNumber() {
            // When
            Optional<Order> found = orderRepository.findByOrderNumber("ORD-999");

            // Then
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatus Tests")
    class FindByStatusTests {

        @Test
        @DisplayName("Sollte Bestellungen nach Status finden")
        void shouldFindOrdersByStatus() {
            // When
            List<Order> deliveredOrders = orderRepository.findByStatus(OrderStatus.DELIVERED);
            List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING);

            // Then
            assertThat(deliveredOrders).hasSize(1);
            assertThat(pendingOrders).hasSize(1);
            assertThat(deliveredOrders.get(0).getOrderNumber()).isEqualTo("ORD-001");
            assertThat(pendingOrders.get(0).getOrderNumber()).isEqualTo("ORD-002");
        }

        @Test
        @DisplayName("Sollte leere Liste zurückgeben wenn kein Order mit Status existiert")
        void shouldReturnEmptyListForNonExistentStatus() {
            // When
            List<Order> cancelledOrders = orderRepository.findByStatus(OrderStatus.CANCELLED);

            // Then
            assertThat(cancelledOrders).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByCustomerId Tests")
    class FindByCustomerIdTests {

        @Test
        @DisplayName("Sollte alle Bestellungen eines Kunden finden")
        void shouldFindOrdersByCustomerId() {
            // When
            List<Order> customerOrders = orderRepository.findByCustomerId(testCustomer.getId());

            // Then
            assertThat(customerOrders).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getTotalAmountByStatus Tests")
    class GetTotalAmountByStatusTests {

        @Test
        @DisplayName("Sollte Gesamtumsatz nach Status berechnen")
        void shouldCalculateTotalAmountByStatus() {
            // When
            BigDecimal deliveredTotal = orderRepository.getTotalAmountByStatus(OrderStatus.DELIVERED);
            BigDecimal pendingTotal = orderRepository.getTotalAmountByStatus(OrderStatus.PENDING);

            // Then
            assertThat(deliveredTotal).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(pendingTotal).isEqualByComparingTo(new BigDecimal("250.00"));
        }

        @Test
        @DisplayName("Sollte null zurückgeben wenn keine Bestellungen mit Status existieren")
        void shouldReturnNullForNoOrdersWithStatus() {
            // When
            BigDecimal cancelledTotal = orderRepository.getTotalAmountByStatus(OrderStatus.CANCELLED);

            // Then
            assertThat(cancelledTotal).isNull();
        }
    }

    @Nested
    @DisplayName("countByStatus Tests")
    class CountByStatusTests {

        @Test
        @DisplayName("Sollte Anzahl der Bestellungen nach Status zählen")
        void shouldCountOrdersByStatus() {
            // When
            long deliveredCount = orderRepository.countByStatus(OrderStatus.DELIVERED);
            long pendingCount = orderRepository.countByStatus(OrderStatus.PENDING);
            long cancelledCount = orderRepository.countByStatus(OrderStatus.CANCELLED);

            // Then
            assertThat(deliveredCount).isEqualTo(1);
            assertThat(pendingCount).isEqualTo(1);
            assertThat(cancelledCount).isZero();
        }
    }

    @Nested
    @DisplayName("findByOrderDateBetween Tests")
    class FindByOrderDateBetweenTests {

        @Test
        @DisplayName("Sollte Bestellungen im Zeitraum finden")
        void shouldFindOrdersInDateRange() {
            // Given
            LocalDateTime startDate = LocalDateTime.now().minusDays(10);
            LocalDateTime endDate = LocalDateTime.now();

            // When
            List<Order> ordersInRange = orderRepository.findByOrderDateBetween(startDate, endDate);

            // Then
            assertThat(ordersInRange).hasSize(2);
        }

        @Test
        @DisplayName("Sollte leere Liste für Zeitraum ohne Bestellungen zurückgeben")
        void shouldReturnEmptyListForDateRangeWithNoOrders() {
            // Given
            LocalDateTime startDate = LocalDateTime.now().minusYears(2);
            LocalDateTime endDate = LocalDateTime.now().minusYears(1);

            // When
            List<Order> ordersInRange = orderRepository.findByOrderDateBetween(startDate, endDate);

            // Then
            assertThat(ordersInRange).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllOrderByOrderDateDesc Tests")
    class FindAllOrderByOrderDateDescTests {

        @Test
        @DisplayName("Sollte Bestellungen absteigend nach Datum sortieren")
        void shouldReturnOrdersSortedByDateDescending() {
            // When
            List<Order> orders = orderRepository.findAllOrderByOrderDateDesc();

            // Then
            assertThat(orders).hasSize(2);
            // Neuere Bestellung (order2) sollte zuerst kommen
            assertThat(orders.get(0).getOrderNumber()).isEqualTo("ORD-002");
            assertThat(orders.get(1).getOrderNumber()).isEqualTo("ORD-001");
        }
    }
}
