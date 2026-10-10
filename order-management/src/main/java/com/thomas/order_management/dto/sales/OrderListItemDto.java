package com.thomas.order_management.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Zeile der Auftragsliste (ohne Positionen).
 */
public record OrderListItemDto(
        Long id,
        String orderNumber,
        PartnerDto customer,
        LocalDateTime orderDate,
        LocalDate requestedDeliveryDate,
        String customerReference,
        CodeText status,
        CodeText deliveryStatus,
        CodeText billingStatus,
        CodeText creditStatus,
        BigDecimal netAmount,
        BigDecimal totalAmount,
        String currency,
        int itemCount
) {
}
