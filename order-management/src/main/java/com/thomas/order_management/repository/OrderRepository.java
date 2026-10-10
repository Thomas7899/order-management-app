// order-management/src/main/java/com/thomas/order_management/repository/OrderRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.Order;
import com.thomas.order_management.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByCustomerIdOrderByOrderDateDesc(Long customerId);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.customer.id = :customerId AND o.status IN :statuses")
    List<Order> findByCustomerIdAndStatusIn(@Param("customerId") Long customerId, @Param("statuses") Collection<OrderStatus> statuses);

    List<Order> findByStatusIn(Collection<OrderStatus> statuses);

    long countByStatus(OrderStatus status);

    long countByOrderDateBetween(LocalDateTime start, LocalDateTime end);

    List<Order> findTop10ByOrderByOrderDateDesc();

    @Query("SELECT o FROM Order o WHERE o.status IN :statuses AND o.requestedDeliveryDate < CURRENT_DATE")
    List<Order> findOverdueOrders(@Param("statuses") Collection<OrderStatus> statuses);

    @Query(value = """
        SELECT EXTRACT(YEAR FROM order_date) as year, EXTRACT(MONTH FROM order_date) as month,
               COUNT(*) as order_count, COALESCE(SUM(net_amount), 0) as order_value
        FROM orders
        WHERE order_date >= :startDate AND status <> 'CANCELLED'
        GROUP BY EXTRACT(YEAR FROM order_date), EXTRACT(MONTH FROM order_date)
        ORDER BY year, month
        """, nativeQuery = true)
    List<Object[]> getMonthlyOrderIntake(@Param("startDate") LocalDateTime startDate);
}
