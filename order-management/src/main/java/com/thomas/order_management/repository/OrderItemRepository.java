package com.thomas.order_management.repository;

import com.thomas.order_management.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> findByProductId(Long productId);

    /** Für Aufträge reservierte Menge: bestätigt, aber noch nicht in eine Lieferung übernommen. */
    @Query("""
        SELECT COALESCE(SUM(oi.confirmedQuantity - oi.deliveredQuantity), 0)
        FROM OrderItem oi
        WHERE oi.product.id = :productId
          AND oi.rejectionReason IS NULL
          AND oi.confirmedQuantity > oi.deliveredQuantity
          AND (:excludeItemId IS NULL OR oi.id <> :excludeItemId)
        """)
    long sumOpenConfirmedQuantity(@Param("productId") Long productId, @Param("excludeItemId") Long excludeItemId);

    /** Nicht bestätigte Restmengen offener Positionen (Rückstand). */
    @Query("""
        SELECT oi FROM OrderItem oi
        WHERE oi.rejectionReason IS NULL
          AND oi.confirmedQuantity < oi.quantity
          AND oi.order.status IN (com.thomas.order_management.model.OrderStatus.OPEN,
                                  com.thomas.order_management.model.OrderStatus.IN_PROCESS)
        ORDER BY oi.order.orderDate ASC
        """)
    List<OrderItem> findBacklogItems();

    /** Fakturierte Käufe: Material-ID, Debitor-ID, Positions-ID, Auftragsdatum. */
    @Query("SELECT oi.product.id, oi.order.customer.id, oi.id, oi.order.orderDate FROM OrderItem oi WHERE oi.billedQuantity > 0")
    List<Object[]> findBilledPurchases();

    @Query("""
        SELECT oi FROM OrderItem oi
        WHERE oi.rejectionReason IS NULL
          AND oi.order.status IN (com.thomas.order_management.model.OrderStatus.OPEN,
                                  com.thomas.order_management.model.OrderStatus.IN_PROCESS)
        """)
    List<OrderItem> findItemsOfOpenOrders();
}
