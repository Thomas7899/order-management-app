// src/main/java/com/thomas/order_management/controller/OrderController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.OrderDto;
import com.thomas.order_management.model.Order;
import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Alle Bestellungen abrufen (neueste zuerst)
    @GetMapping
    public List<OrderDto> getAllOrders() {
        return orderService.getAllOrders();
    }

    // Bestellung nach ID abrufen
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Bestellung nach Bestellnummer abrufen
    @GetMapping("/order-number/{orderNumber}")
    public ResponseEntity<OrderDto> getOrderByOrderNumber(@PathVariable String orderNumber) {
        return orderService.getOrderByOrderNumber(orderNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Bestellung erstellen
    @PostMapping
    public OrderDto createOrder(@RequestBody Order order) {
        return orderService.createOrder(order);
    }

    // Bestellung aktualisieren
    @PutMapping("/{id}")
    public ResponseEntity<OrderDto> updateOrder(@PathVariable Long id, @RequestBody Order orderDetails) {
        return orderService.updateOrder(id, orderDetails)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Bestellstatus aktualisieren
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDto> updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        return orderService.updateOrderStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Bestellung löschen
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        if (orderService.deleteOrder(id)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Bestellungen eines Kunden
    @GetMapping("/customer/{customerId}")
    public List<OrderDto> getOrdersByCustomer(@PathVariable Long customerId) {
        return orderService.getOrdersByCustomer(customerId);
    }

    // Bestellungen nach Status
    @GetMapping("/status/{status}")
    public List<OrderDto> getOrdersByStatus(@PathVariable OrderStatus status) {
        return orderService.getOrdersByStatus(status);
    }

    // Bestellungen in Zeitraum
    @GetMapping("/period")
    public List<OrderDto> getOrdersInPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return orderService.getOrdersInPeriod(startDate, endDate);
    }

    // Umsatz nach Status
    @GetMapping("/revenue/status/{status}")
    public BigDecimal getRevenueByStatus(@PathVariable OrderStatus status) {
        return orderService.getRevenueByStatus(status);
    }

    // Umsatz in Zeitraum
    @GetMapping("/revenue/period")
    public BigDecimal getRevenueInPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return orderService.getRevenueInPeriod(startDate, endDate);
    }

    // Anzahl Bestellungen nach Status
    @GetMapping("/count/status/{status}")
    public long getOrderCountByStatus(@PathVariable OrderStatus status) {
        return orderService.getOrderCountByStatus(status);
    }
}