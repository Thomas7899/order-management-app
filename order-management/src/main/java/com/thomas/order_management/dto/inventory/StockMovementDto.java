// order-management/src/main/java/com/thomas/order_management/dto/inventory/StockMovementDto.java
package com.thomas.order_management.dto.inventory;

import com.thomas.order_management.model.StockMovement;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class StockMovementDto {
    private Long id;
    private String movementNumber;
    private StockMovement.MovementType movementType;
    private String movementCode;
    private String movementTypeDisplay;

    private Long productId;
    private String materialNumber;
    private String productName;

    private Long sourceWarehouseId;
    private String sourceWarehouseCode;
    private String sourceWarehouseName;

    private Long targetWarehouseId;
    private String targetWarehouseCode;
    private String targetWarehouseName;

    /** Vorzeichenbehaftete Bestandswirkung aus Sicht des betroffenen Lagerorts (Umlagerung: 0). */
    private Integer stockEffect;
    private Integer quantity;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private String reason;
    private String referenceNumber;
    private StockMovement.ReferenceType referenceType;

    private boolean reversed;
    private String reversedMovementNumber;

    private LocalDateTime postingDate;
    private String createdBy;
    private LocalDateTime createdAt;
}
