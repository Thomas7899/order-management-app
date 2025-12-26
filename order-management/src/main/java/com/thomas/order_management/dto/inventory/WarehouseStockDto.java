// order-management/src/main/java/com/thomas/order_management/dto/inventory/WarehouseStockDto.java
package com.thomas.order_management.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseStockDto {
    private Long id;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long productId;
    private String productName;
    private String productCategory;
    private BigDecimal productPrice;
    private Integer quantity;
    private Integer minStock;
    private Integer maxStock;
    private String binLocation;
    private LocalDateTime lastCountedAt;
    
    // Berechnete Werte
    private BigDecimal stockValue;
    private String stockStatus; // "OK", "LOW", "OUT", "OVER"
    private Integer reorderQuantity;
}
