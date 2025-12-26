// src/main/java/com/thomas/order_management/controller/CustomerController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.controller.assembler.CustomerModelAssembler;
import com.thomas.order_management.dto.CustomerDTO;
import com.thomas.order_management.dto.CustomerRequestDTO;
import com.thomas.order_management.service.CustomerService;
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
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerModelAssembler assembler;

    @GetMapping
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<CustomerDTO>>> getAllCustomers() {
        List<EntityModel<CustomerDTO>> customers = customerService.getAllCustomers().stream()
                .map(customerDto -> assembler.toModel(Objects.requireNonNull(customerDto, "customerDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(customers,
                linkTo(methodOn(CustomerController.class).getAllCustomers()).withSelfRel()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<CustomerDTO>> getCustomerById(@PathVariable Long id) {
        // Service wirft Exception wenn nicht gefunden -> GlobalExceptionHandler fängt das ab
        // Daher können wir hier direkt mappen
        CustomerDTO customer = Objects.requireNonNull(customerService.getCustomerById(id), "customer");
        return ResponseEntity.ok(assembler.toModel(customer));
    }

    @PostMapping
    @SuppressWarnings("null")
    public ResponseEntity<EntityModel<CustomerDTO>> createCustomer(@RequestBody CustomerRequestDTO customerDto) {
        CustomerDTO createdCustomer = Objects.requireNonNull(customerService.createCustomer(customerDto), "createdCustomer");
        EntityModel<CustomerDTO> model = assembler.toModel(createdCustomer);

        return ResponseEntity
                .created(model.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(model);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<CustomerDTO>> updateCustomer(@PathVariable Long id, @RequestBody CustomerRequestDTO customerDetails) {
        CustomerDTO updatedCustomer = Objects.requireNonNull(customerService.updateCustomer(id, customerDetails), "updatedCustomer");
        return ResponseEntity.ok(assembler.toModel(updatedCustomer));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<CustomerDTO>>> searchCustomers(@RequestParam String query) {
        List<EntityModel<CustomerDTO>> customers = customerService.searchCustomers(query).stream()
                .map(customerDto -> assembler.toModel(Objects.requireNonNull(customerDto, "customerDto")))
                .toList();
        
        return ResponseEntity.ok(CollectionModel.of(customers));
    }
}
