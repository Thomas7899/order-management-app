// order-management/src/main/java/com/thomas/order_management/repository/WarehouseRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCode(String code);

    List<Warehouse> findByActiveTrue();

    Optional<Warehouse> findByIsDefaultTrue();

    @Query("SELECT w FROM Warehouse w WHERE w.active = true ORDER BY w.isDefault DESC, w.name")
    List<Warehouse> findAllActiveOrderByDefault();

    @Query(value = """
        SELECT w.id, w.code, w.name, 
               COUNT(ws.id) as product_count,
               COALESCE(SUM(ws.quantity), 0) as total_stock,
               COALESCE(SUM(ws.quantity * p.price), 0) as inventory_value
        FROM warehouses w
        LEFT JOIN warehouse_stocks ws ON w.id = ws.warehouse_id
        LEFT JOIN products p ON ws.product_id = p.id
        WHERE w.active = true
        GROUP BY w.id, w.code, w.name
        ORDER BY inventory_value DESC
        """, nativeQuery = true)
    List<Object[]> getWarehouseOverview();
}
