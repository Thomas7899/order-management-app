// order-management/src/main/java/com/thomas/order_management/dto/ReviewDTO.java
package com.thomas.order_management.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewDTO(
        Long id,
        String comment,
        int rating,
        LocalDateTime createdAt,

        Long productId,
        String productName,
        BigDecimal productPrice,

        Long customerId,
        String customerName,

        Long orderId,
        Long orderItemId,

        // Einordnung (Stimmung, Themen) und Antwort des Kundenservice
        String sentiment,
        List<String> themes,
        String replyText,
        LocalDateTime repliedAt,
        String repliedBy
) {}
