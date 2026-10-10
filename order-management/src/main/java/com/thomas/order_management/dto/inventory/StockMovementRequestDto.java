// order-management/src/main/java/com/thomas/order_management/dto/inventory/StockMovementRequestDto.java
package com.thomas.order_management.dto.inventory;

import com.thomas.order_management.model.StockMovement;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StockMovementRequestDto {

    @NotNull(message = "Bewegungsart ist erforderlich")
    private StockMovement.MovementType movementType;

    @NotNull(message = "Material ist erforderlich")
    private Long productId;

    private Long sourceWarehouseId;
    private Long targetWarehouseId;

    /** Menge; bei Inventurdifferenzen die gezählte Menge. */
    @NotNull(message = "Menge ist erforderlich")
    @Min(value = 0, message = "Menge darf nicht negativ sein")
    private Integer quantity;

    @Size(max = 255)
    private String reason;

    @Size(max = 35)
    private String referenceNumber;

    private StockMovement.ReferenceType referenceType;

    /** Buchungsdatum; leer = heute. */
    private LocalDate postingDate;
}
