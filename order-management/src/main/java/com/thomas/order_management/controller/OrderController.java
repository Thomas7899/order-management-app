// src/main/java/com/thomas/order_management/controller/OrderController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.controller.assembler.OrderModelAssembler;
import com.thomas.order_management.dto.OrderDto;
import com.thomas.order_management.model.Order;
import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "Bestellungsverwaltung - CRUD-Operationen für Bestellungen")
public class OrderController {

    private final OrderService orderService;
    private final OrderModelAssembler assembler;

    @Operation(summary = "Alle Bestellungen abrufen", description = "Gibt eine Liste aller Bestellungen zurück, sortiert nach Bestelldatum (absteigend)")
    @ApiResponse(responseCode = "200", description = "Erfolgreich - Liste der Bestellungen")
    @GetMapping
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<OrderDto>>> getAllOrders() {
        List<EntityModel<OrderDto>> orders = orderService.getAllOrders().stream()
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(orders,
                linkTo(methodOn(OrderController.class).getAllOrders()).withSelfRel()));
    }

    @Operation(summary = "Bestellung nach ID abrufen", description = "Gibt eine einzelne Bestellung anhand ihrer ID zurück")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bestellung gefunden"),
        @ApiResponse(responseCode = "404", description = "Bestellung nicht gefunden", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<OrderDto>> getOrderById(
            @Parameter(description = "ID der Bestellung") @PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Bestellung nach Bestellnummer abrufen")
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<EntityModel<OrderDto>> getOrderByOrderNumber(
            @Parameter(description = "Bestellnummer (z.B. ORD-123456)") @PathVariable String orderNumber) {
        return orderService.getOrderByOrderNumber(orderNumber)
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Neue Bestellung erstellen", description = "Erstellt eine neue Bestellung. Die Bestellnummer wird automatisch generiert falls nicht angegeben.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Bestellung erfolgreich erstellt"),
        @ApiResponse(responseCode = "400", description = "Ungültige Eingabedaten", content = @Content)
    })
    @PostMapping
    @SuppressWarnings("null")
    public ResponseEntity<EntityModel<OrderDto>> createOrder(@RequestBody Order order) {
        OrderDto createdOrder = Objects.requireNonNull(orderService.createOrder(order), "createdOrder");
        EntityModel<OrderDto> entityModel = assembler.toModel(createdOrder);

        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

    @Operation(summary = "Bestellung aktualisieren")
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<OrderDto>> updateOrder(@PathVariable Long id, @RequestBody Order orderDetails) {
        return orderService.updateOrder(id, orderDetails)
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Bestellstatus aktualisieren", description = "Ändert nur den Status einer Bestellung (z.B. PENDING → SHIPPED)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<EntityModel<OrderDto>> updateOrderStatus(
            @PathVariable Long id, 
            @Parameter(description = "Neuer Status", schema = @Schema(implementation = OrderStatus.class)) 
            @RequestParam OrderStatus status) {
        return orderService.updateOrderStatus(id, status)
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Bestellung löschen")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Bestellung erfolgreich gelöscht"),
        @ApiResponse(responseCode = "404", description = "Bestellung nicht gefunden")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        if (orderService.deleteOrder(id)) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Bestellungen eines Kunden abrufen")
    @GetMapping("/customer/{customerId}")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<OrderDto>>> getOrdersByCustomer(@PathVariable Long customerId) {
        List<EntityModel<OrderDto>> orders = orderService.getOrdersByCustomer(customerId).stream()
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .toList();
        return ResponseEntity.ok(CollectionModel.of(orders));
    }

    @Operation(summary = "Bestellungen nach Status filtern")
    @GetMapping("/status/{status}")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<OrderDto>>> getOrdersByStatus(@PathVariable OrderStatus status) {
        List<EntityModel<OrderDto>> orders = orderService.getOrdersByStatus(status).stream()
                .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .toList();
        return ResponseEntity.ok(CollectionModel.of(orders));
    }

    @Operation(summary = "Bestellungen in Zeitraum filtern")
    @GetMapping("/filter")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<OrderDto>>> getOrdersInPeriod(
            @Parameter(description = "Startdatum (ISO 8601)", example = "2024-01-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "Enddatum (ISO 8601)", example = "2024-12-31T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<EntityModel<OrderDto>> orders = orderService.getOrdersInPeriod(startDate, endDate).stream()
            .map(orderDto -> assembler.toModel(Objects.requireNonNull(orderDto, "orderDto")))
                .toList();
        return ResponseEntity.ok(CollectionModel.of(orders));
    }

    @Operation(summary = "Umsatz nach Bestellstatus", description = "Berechnet den Gesamtumsatz aller Bestellungen mit dem angegebenen Status")
    @GetMapping("/revenue/status/{status}")
    public BigDecimal getRevenueByStatus(@PathVariable OrderStatus status) {
        return orderService.getRevenueByStatus(status);
    }

    @Operation(summary = "Umsatz in Zeitraum", description = "Berechnet den Gesamtumsatz im angegebenen Zeitraum")
    @GetMapping("/revenue/period")
    public BigDecimal getRevenueInPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return orderService.getRevenueInPeriod(startDate, endDate);
    }

    @Operation(summary = "Anzahl Bestellungen nach Status")
    @GetMapping("/count/status/{status}")
    public long getOrderCountByStatus(@PathVariable OrderStatus status) {
        return orderService.getOrderCountByStatus(status);
    }
}