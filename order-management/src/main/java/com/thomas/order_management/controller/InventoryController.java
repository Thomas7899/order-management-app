// order-management/src/main/java/com/thomas/order_management/controller/InventoryController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.PageResponse;
import com.thomas.order_management.dto.inventory.*;
import com.thomas.order_management.dto.sales.AvailabilityDto;
import com.thomas.order_management.dto.sales.LogisticsRequests;
import com.thomas.order_management.service.InventoryService;
import com.thomas.order_management.service.sales.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Bestandsführung", description = "Lagerorte, Bestände, Verfügbarkeit und Materialbelege (MMBE/MIGO/MB51)")
public class InventoryController {

    private final InventoryService inventoryService;
    private final AvailabilityService availabilityService;

    public InventoryController(InventoryService inventoryService, AvailabilityService availabilityService) {
        this.inventoryService = inventoryService;
        this.availabilityService = availabilityService;
    }

    // === Lagerorte ===

    @GetMapping("/warehouses")
    public List<WarehouseDto> getAllWarehouses() {
        return inventoryService.getAllWarehouses();
    }

    @GetMapping("/warehouses/active")
    public List<WarehouseDto> getActiveWarehouses() {
        return inventoryService.getActiveWarehouses();
    }

    @GetMapping("/warehouses/{id}")
    public WarehouseDto getWarehouse(@PathVariable Long id) {
        return inventoryService.getWarehouseById(id);
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseDto createWarehouse(@Valid @RequestBody WarehouseDto dto) {
        return inventoryService.createWarehouse(dto);
    }

    @PutMapping("/warehouses/{id}")
    public WarehouseDto updateWarehouse(@PathVariable Long id, @Valid @RequestBody WarehouseDto dto) {
        return inventoryService.updateWarehouse(id, dto);
    }

    @GetMapping("/warehouses/overview")
    public List<Object[]> getWarehouseOverview() {
        return inventoryService.getWarehouseOverview();
    }

    // === Bestände ===

    @Operation(summary = "Bestandsübersicht je Material und Lagerort mit Verfügbarkeit (MMBE)")
    @GetMapping("/availability/{productId}")
    public AvailabilityDto getAvailability(@PathVariable Long productId) {
        return availabilityService.getAvailability(productId);
    }

    @GetMapping("/stock/warehouse/{warehouseId}")
    public List<WarehouseStockDto> getStockByWarehouse(@PathVariable Long warehouseId) {
        return inventoryService.getStockByWarehouse(warehouseId);
    }

    @GetMapping("/stock/product/{productId}")
    public List<WarehouseStockDto> getStockByProduct(@PathVariable Long productId) {
        return inventoryService.getStockByProduct(productId);
    }

    @GetMapping("/stock/low")
    public List<WarehouseStockDto> getLowStockItems() {
        return inventoryService.getLowStockItems();
    }

    @GetMapping("/stock/over")
    public List<WarehouseStockDto> getOverStockItems() {
        return inventoryService.getOverStockItems();
    }

    @GetMapping("/stock/overview")
    public List<Object[]> getStockOverview() {
        return inventoryService.getStockOverview();
    }

    @Operation(summary = "Lagerplatz, Mindest- und Höchstbestand pflegen",
            description = "Bestandsmengen sind nur über Materialbelege änderbar")
    @PutMapping("/stock/warehouse/{warehouseId}/product/{productId}")
    public WarehouseStockDto updateStockSettings(@PathVariable Long warehouseId, @PathVariable Long productId,
                                                 @Valid @RequestBody StockSettingsRequest request) {
        return inventoryService.updateStockSettings(warehouseId, productId,
                request.minStock(), request.maxStock(), request.binLocation());
    }

    @GetMapping("/stock/product/{productId}/total")
    public Integer getTotalStock(@PathVariable Long productId) {
        return inventoryService.getTotalStockByProduct(productId);
    }

    // === Materialbelege ===

    @Operation(summary = "Materialbelege suchen (MB51)")
    @GetMapping("/movements")
    public PageResponse<StockMovementDto> getMovements(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> movementCode,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20, sort = {"postingDate", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return inventoryService.searchMovements(new InventoryService.MovementSearch(search, movementCode, productId,
                warehouseId, dateFrom, dateTo), pageable);
    }

    @GetMapping("/movements/warehouse/{warehouseId}")
    public Page<StockMovementDto> getMovementsByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.getStockMovementsByWarehouse(warehouseId, PageRequest.of(page, size));
    }

    @GetMapping("/movements/product/{productId}")
    public List<StockMovementDto> getMovementsByProduct(@PathVariable Long productId) {
        return inventoryService.getStockMovementsByProduct(productId);
    }

    @Operation(summary = "Warenbewegung buchen (MIGO)")
    @PostMapping("/movements")
    public ResponseEntity<StockMovementDto> createMovement(@Valid @RequestBody StockMovementRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createStockMovement(request));
    }

    @Operation(summary = "Materialbeleg stornieren")
    @PostMapping("/movements/{id}/reverse")
    public StockMovementDto reverseMovement(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) LogisticsRequests.Cancel request) {
        return inventoryService.reverseMovement(id,
                request != null ? request.date() : null,
                request != null ? request.reason() : null);
    }

    // === Statistik ===

    @GetMapping("/statistics/movements")
    public List<Object[]> getMovementStatistics(@RequestParam(defaultValue = "30") int days) {
        return inventoryService.getMovementStatistics(days);
    }

    @GetMapping("/statistics/trend")
    public List<Object[]> getDailyTrend(@RequestParam(defaultValue = "30") int days) {
        return inventoryService.getDailyMovementTrend(days);
    }

    @GetMapping("/statistics/top-products")
    public List<Object[]> getTopMovedProducts(@RequestParam(defaultValue = "30") int days) {
        return inventoryService.getTopMovedProducts(days);
    }
}
