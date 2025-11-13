package com.thomas.order_management.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for a Product to prevent LazyInitializationException and expose only necessary data.
 */
public record ProductDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String category,
        String imageUrl,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}