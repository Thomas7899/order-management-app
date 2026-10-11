package com.thomas.order_management.dto;

import com.thomas.order_management.model.TaxCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Materialstamm in der API-Darstellung.
 */
public record ProductDto(
        Long id,
        String materialNumber,
        String name,
        String description,
        BigDecimal price,
        BigDecimal costPrice,
        Integer stockQuantity,
        String category,
        String unitOfMeasure,
        TaxCode taxCode,
        String imageUrl,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
