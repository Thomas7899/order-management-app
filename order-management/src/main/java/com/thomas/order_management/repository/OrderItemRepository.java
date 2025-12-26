package com.thomas.order_management.repository;

import com.thomas.order_management.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
    List<OrderItem> findByOrderId(Long orderId);
    
    List<OrderItem> findByProductId(Long productId);
    
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.id = :orderId")
    List<OrderItem> findOrderItemsByOrderId(Long orderId);
    
    @Query("SELECT SUM(oi.quantity) FROM OrderItem oi WHERE oi.product.id = :productId")
    Long getTotalQuantitySoldForProduct(Long productId);

    // ABC-Analyse: Produkt-Umsatzdaten
    @Query(value = """
        SELECT p.id, p.name, p.category, 
               COALESCE(SUM(oi.unit_price * oi.quantity), 0) as revenue,
               COUNT(DISTINCT oi.order_id) as order_count,
               COALESCE(SUM(oi.quantity), 0) as quantity_sold
        FROM products p
        LEFT JOIN order_items oi ON p.id = oi.product_id
        LEFT JOIN orders o ON oi.order_id = o.id AND o.status = 'DELIVERED'
        GROUP BY p.id, p.name, p.category
        ORDER BY revenue DESC
        """, nativeQuery = true)
    List<Object[]> getProductSalesData();
}