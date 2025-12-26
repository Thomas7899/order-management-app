// order-management/src/main/java/com/thomas/order_management/dto/inventory/StockMovementDto.java
package com.thomas.order_management.dto.inventory;

import com.thomas.order_management.model.StockMovement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementDto {
    private Long id;
    private String movementNumber;
    private StockMovement.MovementType movementType;
    private String movementTypeDisplay;
    
    private Long productId;
    private String productName;
    
    private Long sourceWarehouseId;
    private String sourceWarehouseCode;
    private String sourceWarehouseName;
    
    private Long targetWarehouseId;
    private String targetWarehouseCode;
    private String targetWarehouseName;
    
    private Integer quantity;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private String reason;
    private String referenceNumber;
    private StockMovement.ReferenceType referenceType;
    private String createdBy;
    private LocalDateTime createdAt;

    public String getMovementTypeDisplay() {
        if (movementType == null) return "";
        return switch (movementType) {
            case GOODS_RECEIPT -> "Wareneingang";
            case GOODS_ISSUE -> "Warenausgang";
            case TRANSFER -> "Umbuchung";
            case INVENTORY_ADJUSTMENT -> "Inventurkorrektur";
            case RETURN -> "Retoure";
            case SCRAP -> "Verschrottung";
        };
    }
}
