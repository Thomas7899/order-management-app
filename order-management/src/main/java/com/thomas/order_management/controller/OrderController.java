// src/main/java/com/thomas/order_management/controller/OrderController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.controller.assembler.OrderModelAssembler;
import com.thomas.order_management.dto.OrderDto;
import com.thomas.order_management.model.Order;
import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderModelAssembler assembler;

    @GetMapping
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<OrderDto>>> getAllOrders() {
        List<EntityModel<OrderDto>> orders = orderService.getAllOrders().stream()
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(orders,
                linkTo(methodOn(OrderController.class).getAllOrders()).withSelfRel()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<OrderDto>> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<EntityModel<OrderDto>> getOrderByOrderNumber(@PathVariable String orderNumber) {
        return orderService.getOrderByOrderNumber(orderNumber)
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @SuppressWarnings("null")
    public ResponseEntity<EntityModel<OrderDto>> createOrder(@RequestBody Order order) {
        OrderDto createdOrder = Objects.requireNonNull(orderService.createOrder(order), "createdOrder");
        EntityModel<OrderDto> entityModel = assembler.toModel(createdOrder);

        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<OrderDto>> updateOrder(@PathVariable Long id, @RequestBody Order orderDetails) {
        return orderService.updateOrder(id, orderDetails)
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EntityModel<OrderDto>> updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        return orderService.updateOrderStatus(id, status)
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        if (orderService.deleteOrder(id)) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/customer/{customerId}")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<OrderDto>>> getOrdersByCustomer(@PathVariable Long customerId) {
        List<EntityModel<OrderDto>> orders = orderService.getOrdersByCustomer(customerId).stream()
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .toList();
        return ResponseEntity.ok(CollectionModel.of(orders));
    }

    @GetMapping("/status/{status}")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<OrderDto>>> getOrdersByStatus(@PathVariable OrderStatus status) {
        List<EntityModel<OrderDto>> orders = orderService.getOrdersByStatus(status).stream()
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .toList();
        return ResponseEntity.ok(CollectionModel.of(orders));
    }

    @GetMapping("/filter")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<OrderDto>>> getOrdersInPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<EntityModel<OrderDto>> orders = orderService.getOrdersInPeriod(startDate, endDate).stream()
            .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .toList();
        return ResponseEntity.ok(CollectionModel.of(orders));
    }

    @GetMapping("/revenue/status/{status}")
    public BigDecimal getRevenueByStatus(@PathVariable OrderStatus status) {
        return orderService.getRevenueByStatus(status);
    }

    @GetMapping("/revenue/period")
    public BigDecimal getRevenueInPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return orderService.getRevenueInPeriod(startDate, endDate);
    }

    @GetMapping("/count/status/{status}")
    public long getOrderCountByStatus(@PathVariable OrderStatus status) {
        return orderService.getOrderCountByStatus(status);
    }
}