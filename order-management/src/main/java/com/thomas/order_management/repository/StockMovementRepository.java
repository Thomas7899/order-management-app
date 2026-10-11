// order-management/src/main/java/com/thomas/order_management/repository/StockMovementRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    List<StockMovement> findByMovementNumberOrderByDocumentItemAsc(String movementNumber);

    List<StockMovement> findByProductIdOrderByPostingDateDescIdDesc(Long productId);

    List<StockMovement> findByReferenceTypeAndReferenceNumber(StockMovement.ReferenceType referenceType, String referenceNumber);

    @Query("SELECT sm FROM StockMovement sm WHERE sm.sourceWarehouse.id = :warehouseId OR sm.targetWarehouse.id = :warehouseId ORDER BY sm.postingDate DESC, sm.id DESC")
    Page<StockMovement> findByWarehouse(@Param("warehouseId") Long warehouseId, Pageable pageable);

    @Query(value = """
        SELECT movement_code, COUNT(*) as count, SUM(quantity) as total_quantity
        FROM stock_movements
        WHERE posting_date >= :startDate
        GROUP BY movement_code
        ORDER BY count DESC
        """, nativeQuery = true)
    List<Object[]> getMovementStatistics(@Param("startDate") LocalDateTime startDate);

    @Query(value = """
        SELECT CAST(posting_date AS DATE) as movement_date,
               SUM(CASE WHEN movement_code IN ('101','561','651','701','202','602','552') THEN quantity ELSE 0 END) as receipts,
               SUM(CASE WHEN movement_code IN ('102','562','652','702','201','601','551') THEN quantity ELSE 0 END) as issues
        FROM stock_movements
        WHERE posting_date >= :startDate
        GROUP BY CAST(posting_date AS DATE)
        ORDER BY movement_date
        """, nativeQuery = true)
    List<Object[]> getDailyMovementTrend(@Param("startDate") LocalDateTime startDate);

    @Query(value = """
        SELECT p.material_number, p.name, p.category,
               SUM(CASE WHEN sm.movement_code IN ('101','561') THEN sm.quantity WHEN sm.movement_code IN ('102','562') THEN -sm.quantity ELSE 0 END) as received,
               SUM(CASE WHEN sm.movement_code = '601' THEN sm.quantity WHEN sm.movement_code = '602' THEN -sm.quantity ELSE 0 END) as issued,
               SUM(CASE WHEN sm.movement_code = '651' THEN sm.quantity WHEN sm.movement_code = '652' THEN -sm.quantity ELSE 0 END) as returned
        FROM stock_movements sm
        JOIN products p ON sm.product_id = p.id
        WHERE sm.posting_date >= :startDate
        GROUP BY p.id, p.material_number, p.name, p.category
        ORDER BY issued DESC
        LIMIT 20
        """, nativeQuery = true)
    List<Object[]> getTopMovedProducts(@Param("startDate") LocalDateTime startDate);

    /** Ausgelieferte Menge (601 abzüglich Storno 602) je Material seit einem Stichtag. */
    @Query(value = """
        SELECT product_id,
               SUM(CASE WHEN movement_code = '601' THEN quantity WHEN movement_code = '602' THEN -quantity ELSE 0 END) as issued
        FROM stock_movements
        WHERE posting_date >= :startDate
        GROUP BY product_id
        """, nativeQuery = true)
    List<Object[]> getIssuedQuantityByProduct(@Param("startDate") LocalDateTime startDate);

    /** Bewegungen ausgewählter Bewegungsarten (z. B. 601/602) seit einem Stichtag, mit Material und Lagerorten. */
    @EntityGraph(attributePaths = {"product", "sourceWarehouse", "targetWarehouse"})
    List<StockMovement> findByPostingDateGreaterThanEqualAndMovementCodeIn(LocalDateTime from, Collection<String> codes);
}
