// order-management/src/main/java/com/thomas/order_management/repository/WarehouseStockRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.WarehouseStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {

    Optional<WarehouseStock> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    List<WarehouseStock> findByWarehouseId(Long warehouseId);

    List<WarehouseStock> findByProductId(Long productId);

    @Query("SELECT ws FROM WarehouseStock ws WHERE ws.quantity <= ws.minStock")
    List<WarehouseStock> findLowStockItems();

    @Query("SELECT ws FROM WarehouseStock ws WHERE ws.quantity > ws.maxStock")
    List<WarehouseStock> findOverStockItems();

    @Query("SELECT ws FROM WarehouseStock ws WHERE ws.warehouse.id = :warehouseId AND ws.quantity <= ws.minStock")
    List<WarehouseStock> findLowStockItemsByWarehouse(@Param("warehouseId") Long warehouseId);

    @Query("SELECT SUM(ws.quantity) FROM WarehouseStock ws WHERE ws.product.id = :productId")
    Integer getTotalStockByProduct(@Param("productId") Long productId);

    @Query(value = """
        SELECT p.id, p.name, p.category,
               COALESCE(SUM(ws.quantity), 0) as total_stock,
               COUNT(DISTINCT ws.warehouse_id) as warehouse_count,
               p.price * COALESCE(SUM(ws.quantity), 0) as stock_value
        FROM products p
        LEFT JOIN warehouse_stocks ws ON p.id = ws.product_id
        WHERE p.active = true
        GROUP BY p.id, p.name, p.category, p.price
        ORDER BY stock_value DESC
        """, nativeQuery = true)
    List<Object[]> getStockOverviewByProduct();

    @Modifying
    @Query("UPDATE WarehouseStock ws SET ws.quantity = ws.quantity + :delta WHERE ws.id = :stockId")
    int adjustStock(@Param("stockId") Long stockId, @Param("delta") Integer delta);

    @Query(value = """
        SELECT ws.bin_location, COUNT(*) as item_count, SUM(ws.quantity) as total_quantity
        FROM warehouse_stocks ws
        WHERE ws.warehouse_id = :warehouseId AND ws.bin_location IS NOT NULL
        GROUP BY ws.bin_location
        ORDER BY ws.bin_location
        """, nativeQuery = true)
    List<Object[]> getBinLocationSummary(@Param("warehouseId") Long warehouseId);
}
