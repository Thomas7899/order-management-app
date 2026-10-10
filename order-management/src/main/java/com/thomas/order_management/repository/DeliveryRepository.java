package com.thomas.order_management.repository;

import com.thomas.order_management.model.Delivery;
import com.thomas.order_management.model.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {

    Optional<Delivery> findByDeliveryNumber(String deliveryNumber);

    List<Delivery> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    long countByStatus(Delivery.Status status);

    List<Delivery> findByStatusOrderByCreatedAtAsc(Delivery.Status status);

    List<Delivery> findByStatusAndGoodsIssueDateGreaterThanEqual(Delivery.Status status, java.time.LocalDateTime since);

    @Query("SELECT d FROM Delivery d WHERE d.status = com.thomas.order_management.model.Delivery.Status.GOODS_ISSUED AND d.billingStatus = :billingStatus ORDER BY d.goodsIssueDate ASC")
    List<Delivery> findBillingDueList(@Param("billingStatus") ProcessingStatus billingStatus);

    /** Menge in Lieferungen, deren Warenausgang noch nicht gebucht ist. */
    @Query("""
        SELECT COALESCE(SUM(di.quantity), 0) FROM DeliveryItem di
        WHERE di.product.id = :productId
          AND di.delivery.status = com.thomas.order_management.model.Delivery.Status.CREATED
        """)
    long sumOpenDeliveryQuantity(@Param("productId") Long productId);

    /** Lieferungen ohne Warenausgang je Lagerort und Material. */
    @Query("""
        SELECT COALESCE(SUM(di.quantity), 0) FROM DeliveryItem di
        WHERE di.product.id = :productId
          AND di.delivery.warehouse.id = :warehouseId
          AND di.delivery.status = com.thomas.order_management.model.Delivery.Status.CREATED
        """)
    long sumOpenDeliveryQuantity(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);
}
