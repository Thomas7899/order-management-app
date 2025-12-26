package com.thomas.order_management.service;

import com.thomas.order_management.dto.OrderDto;
import com.thomas.order_management.mapper.OrderMapper;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.Order;
import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit Tests für OrderService.
 * Testet die Geschäftslogik isoliert von der Datenbank durch Mocking.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private OrderDto testOrderDto;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        // Test-Customer erstellen
        testCustomer = new Customer("Max", "Mustermann", "max@example.com");
        testCustomer.setId(1L);

        // Test-Order erstellen
        testOrder = new Order(testCustomer, "ORD-123456");
        testOrder.setId(1L);
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setTotalAmount(new BigDecimal("99.99"));
        testOrder.setOrderDate(LocalDateTime.now());

        // Test-DTO erstellen
        testOrderDto = new OrderDto();
        testOrderDto.setId(1L);
        testOrderDto.setOrderNumber("ORD-123456");
        testOrderDto.setStatus(OrderStatus.PENDING);
        testOrderDto.setTotalAmount(new BigDecimal("99.99"));
    }

    @Nested
    @DisplayName("getAllOrders Tests")
    class GetAllOrdersTests {

        @Test
        @DisplayName("Sollte alle Bestellungen zurückgeben")
        void shouldReturnAllOrders() {
            // Given
            List<Order> orders = Arrays.asList(testOrder);
            List<OrderDto> orderDtos = Arrays.asList(testOrderDto);

            when(orderRepository.findAllOrderByOrderDateDesc()).thenReturn(orders);
            when(orderMapper.toDtoList(orders)).thenReturn(orderDtos);

            // When
            List<OrderDto> result = orderService.getAllOrders();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOrderNumber()).isEqualTo("ORD-123456");
            verify(orderRepository).findAllOrderByOrderDateDesc();
            verify(orderMapper).toDtoList(orders);
        }

        @Test
        @DisplayName("Sollte leere Liste zurückgeben wenn keine Bestellungen existieren")
        void shouldReturnEmptyListWhenNoOrders() {
            // Given
            when(orderRepository.findAllOrderByOrderDateDesc()).thenReturn(Collections.emptyList());
            when(orderMapper.toDtoList(anyList())).thenReturn(Collections.emptyList());

            // When
            List<OrderDto> result = orderService.getAllOrders();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getOrderById Tests")
    class GetOrderByIdTests {

        @Test
        @DisplayName("Sollte Bestellung nach ID finden")
        void shouldFindOrderById() {
            // Given
            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
            when(orderMapper.toDto(testOrder)).thenReturn(testOrderDto);

            // When
            Optional<OrderDto> result = orderService.getOrderById(1L);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getOrderNumber()).isEqualTo("ORD-123456");
            verify(orderRepository).findById(1L);
        }

        @Test
        @DisplayName("Sollte Empty Optional zurückgeben wenn Bestellung nicht existiert")
        void shouldReturnEmptyWhenOrderNotFound() {
            // Given
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            // When
            Optional<OrderDto> result = orderService.getOrderById(999L);

            // Then
            assertThat(result).isEmpty();
            verify(orderRepository).findById(999L);
            verify(orderMapper, never()).toDto(any());
        }
    }

    @Nested
    @DisplayName("createOrder Tests")
    class CreateOrderTests {

        @Test
        @DisplayName("Sollte neue Bestellung mit generierter Bestellnummer erstellen")
        void shouldCreateOrderWithGeneratedOrderNumber() {
            // Given
            Order newOrder = new Order(testCustomer, null); // Ohne Bestellnummer
            newOrder.setTotalAmount(new BigDecimal("50.00"));

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            when(orderRepository.save(orderCaptor.capture())).thenReturn(testOrder);
            when(orderMapper.toDto(any(Order.class))).thenReturn(testOrderDto);

            // When
            OrderDto result = orderService.createOrder(newOrder);

            // Then
            assertThat(result).isNotNull();
            Order capturedOrder = orderCaptor.getValue();
            assertThat(capturedOrder.getOrderNumber()).startsWith("ORD-");
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Sollte existierende Bestellnummer beibehalten")
        void shouldKeepExistingOrderNumber() {
            // Given
            Order newOrder = new Order(testCustomer, "CUSTOM-ORD-001");
            newOrder.setTotalAmount(new BigDecimal("100.00"));

            when(orderRepository.save(any(Order.class))).thenReturn(newOrder);
            when(orderMapper.toDto(any(Order.class))).thenReturn(testOrderDto);

            // When
            orderService.createOrder(newOrder);

            // Then
            verify(orderRepository).save(argThat(order -> 
                "CUSTOM-ORD-001".equals(order.getOrderNumber())
            ));
        }
    }

    @Nested
    @DisplayName("updateOrderStatus Tests")
    class UpdateOrderStatusTests {

        @Test
        @DisplayName("Sollte Bestellstatus aktualisieren")
        void shouldUpdateOrderStatus() {
            // Given
            Order updatedOrder = new Order(testCustomer, "ORD-123456");
            updatedOrder.setId(1L);
            updatedOrder.setStatus(OrderStatus.SHIPPED);

            OrderDto updatedDto = new OrderDto();
            updatedDto.setId(1L);
            updatedDto.setStatus(OrderStatus.SHIPPED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
            when(orderMapper.toDto(any(Order.class))).thenReturn(updatedDto);

            // When
            Optional<OrderDto> result = orderService.updateOrderStatus(1L, OrderStatus.SHIPPED);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(OrderStatus.SHIPPED);
            verify(orderRepository).findById(1L);
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Sollte Empty Optional zurückgeben wenn Bestellung nicht existiert")
        void shouldReturnEmptyWhenOrderNotFoundForStatusUpdate() {
            // Given
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            // When
            Optional<OrderDto> result = orderService.updateOrderStatus(999L, OrderStatus.SHIPPED);

            // Then
            assertThat(result).isEmpty();
            verify(orderRepository).findById(999L);
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteOrder Tests")
    class DeleteOrderTests {

        @Test
        @DisplayName("Sollte Bestellung löschen wenn sie existiert")
        void shouldDeleteOrderWhenExists() {
            // Given
            when(orderRepository.existsById(1L)).thenReturn(true);
            doNothing().when(orderRepository).deleteById(1L);

            // When
            boolean result = orderService.deleteOrder(1L);

            // Then
            assertThat(result).isTrue();
            verify(orderRepository).existsById(1L);
            verify(orderRepository).deleteById(1L);
        }

        @Test
        @DisplayName("Sollte false zurückgeben wenn Bestellung nicht existiert")
        void shouldReturnFalseWhenOrderDoesNotExist() {
            // Given
            when(orderRepository.existsById(999L)).thenReturn(false);

            // When
            boolean result = orderService.deleteOrder(999L);

            // Then
            assertThat(result).isFalse();
            verify(orderRepository).existsById(999L);
            verify(orderRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("getRevenueByStatus Tests")
    class GetRevenueByStatusTests {

        @Test
        @DisplayName("Sollte Umsatz nach Status berechnen")
        void shouldCalculateRevenueByStatus() {
            // Given
            BigDecimal expectedRevenue = new BigDecimal("1500.00");
            when(orderRepository.getTotalAmountByStatus(OrderStatus.DELIVERED))
                .thenReturn(expectedRevenue);

            // When
            BigDecimal result = orderService.getRevenueByStatus(OrderStatus.DELIVERED);

            // Then
            assertThat(result).isEqualByComparingTo(expectedRevenue);
            verify(orderRepository).getTotalAmountByStatus(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("Sollte Zero zurückgeben wenn kein Umsatz vorhanden")
        void shouldReturnZeroWhenNoRevenue() {
            // Given
            when(orderRepository.getTotalAmountByStatus(OrderStatus.CANCELLED))
                .thenReturn(null);

            // When
            BigDecimal result = orderService.getRevenueByStatus(OrderStatus.CANCELLED);

            // Then
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("getOrdersByCustomer Tests")
    class GetOrdersByCustomerTests {

        @Test
        @DisplayName("Sollte alle Bestellungen eines Kunden zurückgeben")
        void shouldReturnOrdersForCustomer() {
            // Given
            List<Order> customerOrders = Arrays.asList(testOrder);
            List<OrderDto> customerOrderDtos = Arrays.asList(testOrderDto);

            when(orderRepository.findByCustomerIdOrderByOrderDateDesc(1L))
                .thenReturn(customerOrders);
            when(orderMapper.toDtoList(customerOrders)).thenReturn(customerOrderDtos);

            // When
            List<OrderDto> result = orderService.getOrdersByCustomer(1L);

            // Then
            assertThat(result).hasSize(1);
            verify(orderRepository).findByCustomerIdOrderByOrderDateDesc(1L);
        }
    }
}
