// order-management/src/main/java/com/thomas/order_management/repository/StockMovementRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Optional<StockMovement> findByMovementNumber(String movementNumber);

    List<StockMovement> findByProductId(Long productId);

    List<StockMovement> findByMovementType(StockMovement.MovementType movementType);

    List<StockMovement> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT sm FROM StockMovement sm WHERE sm.sourceWarehouse.id = :warehouseId OR sm.targetWarehouse.id = :warehouseId ORDER BY sm.createdAt DESC")
    Page<StockMovement> findByWarehouse(@Param("warehouseId") Long warehouseId, Pageable pageable);

    @Query("SELECT sm FROM StockMovement sm ORDER BY sm.createdAt DESC")
    Page<StockMovement> findAllOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = """
        SELECT movement_type, COUNT(*) as count, SUM(quantity) as total_quantity
        FROM stock_movements
        WHERE created_at >= :startDate
        GROUP BY movement_type
        ORDER BY count DESC
        """, nativeQuery = true)
    List<Object[]> getMovementStatistics(@Param("startDate") LocalDateTime startDate);

    @Query(value = """
        SELECT DATE(created_at) as movement_date, 
               movement_type,
               SUM(quantity) as daily_quantity
        FROM stock_movements
        WHERE created_at >= :startDate
        GROUP BY DATE(created_at), movement_type
        ORDER BY movement_date DESC
        """, nativeQuery = true)
    List<Object[]> getDailyMovementTrend(@Param("startDate") LocalDateTime startDate);

    @Query(value = """
        SELECT p.name, p.category, 
               SUM(CASE WHEN sm.movement_type = 'GOODS_RECEIPT' THEN sm.quantity ELSE 0 END) as received,
               SUM(CASE WHEN sm.movement_type = 'GOODS_ISSUE' THEN sm.quantity ELSE 0 END) as issued,
               SUM(CASE WHEN sm.movement_type = 'RETURN' THEN sm.quantity ELSE 0 END) as returned
        FROM stock_movements sm
        JOIN products p ON sm.product_id = p.id
        WHERE sm.created_at >= :startDate
        GROUP BY p.id, p.name, p.category
        ORDER BY (received + issued + returned) DESC
        LIMIT 20
        """, nativeQuery = true)
    List<Object[]> getTopMovedProducts(@Param("startDate") LocalDateTime startDate);
}
