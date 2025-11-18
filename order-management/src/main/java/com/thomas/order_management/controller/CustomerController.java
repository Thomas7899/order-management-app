// src/main/java/com/thomas/order_management/controller/CustomerController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.model.Customer;
import com.thomas.order_management.repository.CustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/customers") 
public class CustomerController {

    private final CustomerRepository customerRepository;

    public CustomerController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    // GET /customers
    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    // GET /customers/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        Optional<Customer> customer = customerRepository.findById(id);
        return customer.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /customers
    @PostMapping
    public Customer createCustomer(@RequestBody Customer customer) {
        return customerRepository.save(customer);
    }

    // PUT /customers/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(
            @PathVariable Long id,
            @RequestBody Customer customerDetails
    ) {
        Optional<Customer> optionalCustomer = customerRepository.findById(id);

        if (optionalCustomer.isPresent()) {
            Customer customer = optionalCustomer.get();

            customer.setFirstName(customerDetails.getFirstName());
            customer.setLastName(customerDetails.getLastName());
            customer.setEmail(customerDetails.getEmail());
            customer.setPhone(customerDetails.getPhone());
            customer.setAddress(customerDetails.getAddress());
            customer.setCity(customerDetails.getCity());
            customer.setZipCode(customerDetails.getZipCode());
            customer.setCountry(customerDetails.getCountry());

            return ResponseEntity.ok(customerRepository.save(customer));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // DELETE /customers/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        if (customerRepository.existsById(id)) {
            customerRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // GET /customers/email?email=...
    @GetMapping("/email")
    public ResponseEntity<Customer> getCustomerByEmail(@RequestParam String email) {
        Optional<Customer> customer = customerRepository.findByEmail(email);
        return customer.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /customers/search?query=...
    @GetMapping("/search")
    public List<Customer> searchCustomers(@RequestParam String query) {
        return customerRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }

    // GET /customers/count
    @GetMapping("/count")
    public long getCustomerCount() {
        return customerRepository.countCustomers();
    }
}
