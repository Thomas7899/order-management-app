package com.thomas.order_management.repository;

import com.thomas.order_management.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    Optional<Customer> findByEmail(String email);
    
    List<Customer> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName);
    
    @Query("SELECT c FROM Customer c WHERE c.city = :city")
    List<Customer> findByCity(String city);
    
    @Query("SELECT COUNT(c) FROM Customer c")
    long countCustomers();
}