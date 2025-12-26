// order-management/src/main/java/com/thomas/order_management/controller/InventoryController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.inventory.*;
import com.thomas.order_management.service.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // === Warehouse Endpoints ===

    @GetMapping("/warehouses")
    public ResponseEntity<List<WarehouseDto>> getAllWarehouses() {
        return ResponseEntity.ok(inventoryService.getAllWarehouses());
    }

    @GetMapping("/warehouses/active")
    public ResponseEntity<List<WarehouseDto>> getActiveWarehouses() {
        return ResponseEntity.ok(inventoryService.getActiveWarehouses());
    }

    @GetMapping("/warehouses/{id}")
    public ResponseEntity<WarehouseDto> getWarehouse(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getWarehouseById(id));
    }

    @PostMapping("/warehouses")
    public ResponseEntity<WarehouseDto> createWarehouse(@RequestBody WarehouseDto dto) {
        return ResponseEntity.ok(inventoryService.createWarehouse(dto));
    }

    @PutMapping("/warehouses/{id}")
    public ResponseEntity<WarehouseDto> updateWarehouse(@PathVariable Long id, @RequestBody WarehouseDto dto) {
        return ResponseEntity.ok(inventoryService.updateWarehouse(id, dto));
    }

    @GetMapping("/warehouses/overview")
    public ResponseEntity<List<Object[]>> getWarehouseOverview() {
        return ResponseEntity.ok(inventoryService.getWarehouseOverview());
    }

    // === Stock Endpoints ===

    @GetMapping("/stock/warehouse/{warehouseId}")
    public ResponseEntity<List<WarehouseStockDto>> getStockByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(inventoryService.getStockByWarehouse(warehouseId));
    }

    @GetMapping("/stock/product/{productId}")
    public ResponseEntity<List<WarehouseStockDto>> getStockByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getStockByProduct(productId));
    }

    @GetMapping("/stock/low")
    public ResponseEntity<List<WarehouseStockDto>> getLowStockItems() {
        return ResponseEntity.ok(inventoryService.getLowStockItems());
    }

    @GetMapping("/stock/over")
    public ResponseEntity<List<WarehouseStockDto>> getOverStockItems() {
        return ResponseEntity.ok(inventoryService.getOverStockItems());
    }

    @GetMapping("/stock/overview")
    public ResponseEntity<List<Object[]>> getStockOverview() {
        return ResponseEntity.ok(inventoryService.getStockOverview());
    }

    @PutMapping("/stock/warehouse/{warehouseId}/product/{productId}")
    public ResponseEntity<WarehouseStockDto> updateStock(
            @PathVariable Long warehouseId,
            @PathVariable Long productId,
            @RequestBody Map<String, Object> updates) {
        Integer quantity = updates.containsKey("quantity") ? (Integer) updates.get("quantity") : null;
        Integer minStock = updates.containsKey("minStock") ? (Integer) updates.get("minStock") : null;
        Integer maxStock = updates.containsKey("maxStock") ? (Integer) updates.get("maxStock") : null;
        String binLocation = updates.containsKey("binLocation") ? (String) updates.get("binLocation") : null;
        
        return ResponseEntity.ok(inventoryService.updateStock(warehouseId, productId, quantity, minStock, maxStock, binLocation));
    }

    @GetMapping("/stock/product/{productId}/total")
    public ResponseEntity<Integer> getTotalStock(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getTotalStockByProduct(productId));
    }

    // === Movement Endpoints ===

    @GetMapping("/movements")
    public ResponseEntity<Page<StockMovementDto>> getMovements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(inventoryService.getStockMovements(pageable));
    }

    @GetMapping("/movements/warehouse/{warehouseId}")
    public ResponseEntity<Page<StockMovementDto>> getMovementsByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(inventoryService.getStockMovementsByWarehouse(warehouseId, pageable));
    }

    @GetMapping("/movements/product/{productId}")
    public ResponseEntity<List<StockMovementDto>> getMovementsByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getStockMovementsByProduct(productId));
    }

    @PostMapping("/movements")
    public ResponseEntity<StockMovementDto> createMovement(@RequestBody StockMovementRequestDto request) {
        return ResponseEntity.ok(inventoryService.createStockMovement(request));
    }

    // === Statistics Endpoints ===

    @GetMapping("/statistics/movements")
    public ResponseEntity<List<Object[]>> getMovementStatistics(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(inventoryService.getMovementStatistics(days));
    }

    @GetMapping("/statistics/trend")
    public ResponseEntity<List<Object[]>> getDailyTrend(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(inventoryService.getDailyMovementTrend(days));
    }

    @GetMapping("/statistics/top-products")
    public ResponseEntity<List<Object[]>> getTopMovedProducts(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(inventoryService.getTopMovedProducts(days));
    }
}
