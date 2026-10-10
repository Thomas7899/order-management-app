package com.thomas.order_management.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record StockSettingsRequest(
        @Min(value = 0, message = "Mindestbestand darf nicht negativ sein") Integer minStock,
        @Min(value = 0, message = "Höchstbestand darf nicht negativ sein") Integer maxStock,
        @Size(max = 20) String binLocation
) {
}
