package com.thomas.order_management.repository;

import com.thomas.order_management.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstests für Auftrags-Repositories mit einer eingebetteten H2-Datenbank.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("OrderRepository Integration Tests")
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private Customer testCustomer;
    private Product product;
    private Order openOrder;
    private Order completedOrder;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer("Test", "User", "test@example.com");
        entityManager.persist(testCustomer);

        product = new Product("Testmaterial", "Beschreibung", new BigDecimal("10.00"), 0);
        product.setMaterialNumber("200001");
        entityManager.persist(product);

        openOrder = order("10000001", OrderStatus.OPEN, LocalDateTime.now().minusDays(2), 5, 3, 0);
        completedOrder = order("10000002", OrderStatus.COMPLETED, LocalDateTime.now().minusDays(5), 4, 4, 4);

        entityManager.flush();
    }

    private Order order(String number, OrderStatus status, LocalDateTime date, int qty, int confirmed, int delivered) {
        Order order = new Order(testCustomer, number);
        order.setStatus(status);
        order.setOrderDate(date);
        OrderItem item = new OrderItem(product, qty);
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setNetAmount(new BigDecimal(qty * 10));
        item.setConfirmedQuantity(confirmed);
        item.setDeliveredQuantity(delivered);
        order.addItem(item);
        order.recalculateTotals();
        entityManager.persist(order);
        return order;
    }

    @Nested
    @DisplayName("Belegnummer und Status")
    class LookupTests {

        @Test
        @DisplayName("Sollte Auftrag über Belegnummer finden")
        void shouldFindOrderByOrderNumber() {
            Optional<Order> found = orderRepository.findByOrderNumber("10000001");

            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(OrderStatus.OPEN);
            assertThat(found.get().getOrderItems()).extracting(OrderItem::getItemNumber).containsExactly(10);
        }

        @Test
        @DisplayName("Sollte leeres Optional für unbekannte Belegnummer liefern")
        void shouldReturnEmptyForNonExistentOrderNumber() {
            assertThat(orderRepository.findByOrderNumber("19999999")).isEmpty();
        }

        @Test
        @DisplayName("Sollte Aufträge nach Status zählen")
        void shouldCountOrdersByStatus() {
            assertThat(orderRepository.countByStatus(OrderStatus.OPEN)).isEqualTo(1);
            assertThat(orderRepository.countByStatus(OrderStatus.COMPLETED)).isEqualTo(1);
            assertThat(orderRepository.countByStatus(OrderStatus.CANCELLED)).isZero();
        }

        @Test
        @DisplayName("Sollte offene Aufträge eines Debitors finden")
        void shouldFindOpenOrdersOfCustomer() {
            List<Order> open = orderRepository.findByCustomerIdAndStatusIn(testCustomer.getId(),
                    EnumSet.of(OrderStatus.OPEN, OrderStatus.IN_PROCESS));

            assertThat(open).extracting(Order::getOrderNumber).containsExactly("10000001");
        }
    }

    @Nested
    @DisplayName("Zeitraum und Sortierung")
    class DateTests {

        @Test
        @DisplayName("Sollte Aufträge im Zeitraum zählen")
        void shouldCountOrdersInPeriod() {
            long count = orderRepository.countByOrderDateBetween(LocalDateTime.now().minusDays(3), LocalDateTime.now());

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Sollte neueste Aufträge zuerst liefern")
        void shouldReturnNewestOrdersFirst() {
            List<Order> orders = orderRepository.findTop10ByOrderByOrderDateDesc();

            assertThat(orders).extracting(Order::getOrderNumber).containsExactly("10000001", "10000002");
        }
    }

    @Nested
    @DisplayName("Reservierungen für die Verfügbarkeitsprüfung")
    class ReservationTests {

        @Test
        @DisplayName("Sollte bestätigte, noch nicht gelieferte Mengen summieren")
        void shouldSumOpenConfirmedQuantity() {
            assertThat(orderItemRepository.sumOpenConfirmedQuantity(product.getId(), null)).isEqualTo(3);
        }

        @Test
        @DisplayName("Sollte die Reservierung einer Position ausklammern können")
        void shouldExcludeOwnItem() {
            Long itemId = openOrder.getOrderItems().get(0).getId();

            assertThat(orderItemRepository.sumOpenConfirmedQuantity(product.getId(), itemId)).isZero();
        }

        @Test
        @DisplayName("Sollte abgesagte Positionen nicht als Reservierung zählen")
        void shouldIgnoreRejectedItems() {
            OrderItem item = openOrder.getOrderItems().get(0);
            item.setRejectionReason(RejectionReason.R10);
            entityManager.flush();

            assertThat(orderItemRepository.sumOpenConfirmedQuantity(product.getId(), null)).isZero();
        }

        @Test
        @DisplayName("Sollte nicht voll bestätigte Positionen offener Aufträge als Rückstand liefern")
        void shouldFindBacklogItems() {
            List<OrderItem> backlog = orderItemRepository.findBacklogItems();

            assertThat(backlog).hasSize(1);
            assertThat(backlog.get(0).getOrder().getOrderNumber()).isEqualTo("10000001");
        }
    }
}
