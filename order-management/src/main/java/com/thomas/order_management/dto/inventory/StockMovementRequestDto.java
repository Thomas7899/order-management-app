// order-management/src/main/java/com/thomas/order_management/dto/inventory/StockMovementRequestDto.java
package com.thomas.order_management.dto.inventory;

import com.thomas.order_management.model.StockMovement;
import lombok.Data;

@Data
public class StockMovementRequestDto {
    private StockMovement.MovementType movementType;
    private Long productId;
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private Integer quantity;
    private String reason;
    private String referenceNumber;
    private StockMovement.ReferenceType referenceType;
}
