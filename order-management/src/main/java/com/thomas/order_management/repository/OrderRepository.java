// order-management/src/main/java/com/thomas/order_management/repository/OrderRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.Order;
import com.thomas.order_management.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    Optional<Order> findByOrderNumber(String orderNumber);
    
    List<Order> findByCustomerId(Long customerId);
    
    List<Order> findByStatus(OrderStatus status);
    
    List<Order> findByOrderDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT o FROM Order o WHERE o.customer.id = :customerId ORDER BY o.orderDate DESC")
    List<Order> findByCustomerIdOrderByOrderDateDesc(Long customerId);
    
    @Query("SELECT o FROM Order o ORDER BY o.orderDate DESC")
    List<Order> findAllOrderByOrderDateDesc();
    
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = :status")
    BigDecimal getTotalAmountByStatus(OrderStatus status);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    long countByStatus(OrderStatus status);
    
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.orderDate >= :startDate AND o.orderDate <= :endDate")
    BigDecimal getTotalRevenueInPeriod(LocalDateTime startDate, LocalDateTime endDate);

    // Neue Methoden für Reporting

    // Monatliche Umsatzdaten für Prognose
    @Query(value = """
        SELECT EXTRACT(YEAR FROM order_date) as year, EXTRACT(MONTH FROM order_date) as month,
               SUM(total_amount) as revenue, COUNT(*) as order_count
        FROM orders
        WHERE order_date >= :startDate AND status = 'DELIVERED'
        GROUP BY EXTRACT(YEAR FROM order_date), EXTRACT(MONTH FROM order_date)
        ORDER BY year, month
        """, nativeQuery = true)
    List<Object[]> getMonthlySalesData(@Param("startDate") LocalDateTime startDate);

    // Monatliche Bestellanzahl
    @Query(value = """
        SELECT EXTRACT(YEAR FROM order_date) as year, EXTRACT(MONTH FROM order_date) as month,
               COUNT(*) as order_count
        FROM orders
        WHERE order_date >= :startDate
        GROUP BY EXTRACT(YEAR FROM order_date), EXTRACT(MONTH FROM order_date)
        ORDER BY year, month
        """, nativeQuery = true)
    List<Object[]> getMonthlyOrderCount(@Param("startDate") LocalDateTime startDate);

    // Kunden-ABC-Analyse
    @Query(value = """
        SELECT c.id, c.first_name, c.last_name, 
               COALESCE(SUM(o.total_amount), 0) as total_revenue,
               COUNT(o.id) as order_count
        FROM customers c
        LEFT JOIN orders o ON c.id = o.customer_id AND o.status = 'DELIVERED'
        GROUP BY c.id, c.first_name, c.last_name
        ORDER BY total_revenue DESC
        """, nativeQuery = true)
    List<Object[]> getCustomerSalesData();
}