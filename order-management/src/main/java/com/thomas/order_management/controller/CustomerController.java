// src/main/java/com/thomas/order_management/controller/CustomerController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.controller.assembler.CustomerModelAssembler;
import com.thomas.order_management.dto.CustomerDTO;
import com.thomas.order_management.dto.CustomerRequestDTO;
import com.thomas.order_management.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Kundenverwaltung - CRUD-Operationen für Kunden")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerModelAssembler assembler;

    @Operation(summary = "Alle Kunden abrufen", description = "Gibt eine Liste aller registrierten Kunden zurück")
    @ApiResponse(responseCode = "200", description = "Erfolgreich - Liste der Kunden")
    @GetMapping
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<CustomerDTO>>> getAllCustomers() {
        List<EntityModel<CustomerDTO>> customers = customerService.getAllCustomers().stream()
                .map(customerDto -> assembler.toModel(Objects.requireNonNull(customerDto, "customerDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(customers,
                linkTo(methodOn(CustomerController.class).getAllCustomers()).withSelfRel()));
    }

    @Operation(summary = "Kunde nach ID abrufen", description = "Gibt einen einzelnen Kunden anhand seiner ID zurück")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Kunde gefunden"),
        @ApiResponse(responseCode = "404", description = "Kunde nicht gefunden", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<CustomerDTO>> getCustomerById(
            @Parameter(description = "ID des Kunden") @PathVariable Long id) {
        CustomerDTO customer = Objects.requireNonNull(customerService.getCustomerById(id), "customer");
        return ResponseEntity.ok(assembler.toModel(customer));
    }

    @Operation(summary = "Neuen Kunden erstellen", description = "Erstellt einen neuen Kunden. E-Mail muss eindeutig sein.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Kunde erfolgreich erstellt"),
        @ApiResponse(responseCode = "400", description = "Ungültige Eingabedaten", content = @Content),
        @ApiResponse(responseCode = "409", description = "E-Mail bereits vergeben", content = @Content)
    })
    @PostMapping
    @SuppressWarnings("null")
    public ResponseEntity<EntityModel<CustomerDTO>> createCustomer(@RequestBody CustomerRequestDTO customerDto) {
        CustomerDTO createdCustomer = Objects.requireNonNull(customerService.createCustomer(customerDto), "createdCustomer");
        EntityModel<CustomerDTO> model = assembler.toModel(createdCustomer);

        return ResponseEntity
                .created(model.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(model);
    }

    @Operation(summary = "Kunde aktualisieren", description = "Aktualisiert die Daten eines bestehenden Kunden")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Kunde erfolgreich aktualisiert"),
        @ApiResponse(responseCode = "404", description = "Kunde nicht gefunden", content = @Content),
        @ApiResponse(responseCode = "409", description = "E-Mail bereits vergeben", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<CustomerDTO>> updateCustomer(
            @Parameter(description = "ID des Kunden") @PathVariable Long id, 
            @RequestBody CustomerRequestDTO customerDetails) {
        CustomerDTO updatedCustomer = Objects.requireNonNull(customerService.updateCustomer(id, customerDetails), "updatedCustomer");
        return ResponseEntity.ok(assembler.toModel(updatedCustomer));
    }

    @Operation(summary = "Kunde löschen", description = "Löscht einen Kunden permanent aus dem System")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Kunde erfolgreich gelöscht"),
        @ApiResponse(responseCode = "404", description = "Kunde nicht gefunden")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kunden suchen", description = "Durchsucht Kunden nach Vor- oder Nachname (case-insensitive)")
    @GetMapping("/search")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<CustomerDTO>>> searchCustomers(
            @Parameter(description = "Suchbegriff (Vor- oder Nachname)", example = "Max") 
            @RequestParam String query) {
        List<EntityModel<CustomerDTO>> customers = customerService.searchCustomers(query).stream()
                .map(customerDto -> assembler.toModel(Objects.requireNonNull(customerDto, "customerDto")))
                .toList();
        
        return ResponseEntity.ok(CollectionModel.of(customers));
    }
}
