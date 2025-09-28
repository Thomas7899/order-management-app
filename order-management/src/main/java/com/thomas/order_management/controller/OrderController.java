package com.thomas.order_management.controller;

import com.thomas.order_management.model.Order;
import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.repository.OrderRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // Alle Bestellungen abrufen (neueste zuerst)
    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAllOrderByOrderDateDesc();
    }

    // Bestellung nach ID abrufen
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        Optional<Order> order = orderRepository.findById(id);
        return order.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }

    // Bestellung nach Bestellnummer abrufen
    @GetMapping("/order-number/{orderNumber}")
    public ResponseEntity<Order> getOrderByOrderNumber(@PathVariable String orderNumber) {
        Optional<Order> order = orderRepository.findByOrderNumber(orderNumber);
        return order.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }

    // Bestellung erstellen
    @PostMapping
    public Order createOrder(@RequestBody Order order) {
        // Bestellnummer generieren falls nicht vorhanden
        if (order.getOrderNumber() == null || order.getOrderNumber().isEmpty()) {
            order.setOrderNumber(generateOrderNumber());
        }
        
        // Gesamtbetrag berechnen falls nicht gesetzt
        if (order.getTotalAmount() == null) {
            order.setTotalAmount(order.calculateTotalAmount());
        }
        
        return orderRepository.save(order);
    }

    // Bestellung aktualisieren
    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable Long id, @RequestBody Order orderDetails) {
        Optional<Order> optionalOrder = orderRepository.findById(id);
        
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            order.setStatus(orderDetails.getStatus());
            order.setNotes(orderDetails.getNotes());
            order.setShippingAddress(orderDetails.getShippingAddress());
            order.setBillingAddress(orderDetails.getBillingAddress());
            
            // Gesamtbetrag neu berechnen
            order.setTotalAmount(order.calculateTotalAmount());
            
            return ResponseEntity.ok(orderRepository.save(order));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Bestellstatus aktualisieren
    @PatchMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        Optional<Order> optionalOrder = orderRepository.findById(id);
        
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            order.setStatus(status);
            return ResponseEntity.ok(orderRepository.save(order));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Bestellung löschen
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        if (orderRepository.existsById(id)) {
            orderRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Bestellungen eines Kunden
    @GetMapping("/customer/{customerId}")
    public List<Order> getOrdersByCustomer(@PathVariable Long customerId) {
        return orderRepository.findByCustomerIdOrderByOrderDateDesc(customerId);
    }

    // Bestellungen nach Status
    @GetMapping("/status/{status}")
    public List<Order> getOrdersByStatus(@PathVariable OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    // Bestellungen in Zeitraum
    @GetMapping("/period")
    public List<Order> getOrdersInPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return orderRepository.findByOrderDateBetween(startDate, endDate);
    }

    // Umsatz nach Status
    @GetMapping("/revenue/status/{status}")
    public BigDecimal getRevenueByStatus(@PathVariable OrderStatus status) {
        BigDecimal revenue = orderRepository.getTotalAmountByStatus(status);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    // Umsatz in Zeitraum
    @GetMapping("/revenue/period")
    public BigDecimal getRevenueInPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        BigDecimal revenue = orderRepository.getTotalRevenueInPeriod(startDate, endDate);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    // Anzahl Bestellungen nach Status
    @GetMapping("/count/status/{status}")
    public long getOrderCountByStatus(@PathVariable OrderStatus status) {
        return orderRepository.countByStatus(status);
    }

    // Hilfsmethode zur Generierung einer Bestellnummer
    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis();
    }
}