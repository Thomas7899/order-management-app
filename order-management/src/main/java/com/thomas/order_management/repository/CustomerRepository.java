// CustomerRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    // Neue Methoden für Reporting
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt > :date")
    long countByCreatedAtAfter(@Param("date") LocalDateTime date);

    @Query("""
        SELECT COUNT(DISTINCT o.customer.id) FROM Order o 
        WHERE o.orderDate > :date
        """)
    long countActiveCustomers(@Param("date") LocalDateTime date);

    @Query(value = """
        SELECT c.country as country, COUNT(*) as count
        FROM customers c
        WHERE c.country IS NOT NULL
        GROUP BY c.country
        ORDER BY count DESC
        """, nativeQuery = true)
    List<Object[]> getCustomerCountByCountry();

    default Map<String, Long> countByCountry() {
        List<Object[]> results = getCustomerCountByCountry();
        return results.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }
}