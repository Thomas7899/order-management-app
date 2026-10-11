package com.thomas.order_management.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detailsicht eines Kundenauftrags (Kopf, Positionen, Preiselemente).
 */
public record OrderDto(
        Long id,
        Long version,
        String orderNumber,
        String orderType,
        PartnerDto customer,
        LocalDateTime orderDate,
        LocalDate requestedDeliveryDate,
        String customerReference,
        CodeText paymentTerms,
        String currency,
        CodeText status,
        CodeText deliveryStatus,
        CodeText billingStatus,
        CodeText creditStatus,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String notes,
        String shippingAddress,
        String billingAddress,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<Item> items
) {

    public record Item(
            Long id,
            int itemNumber,
            Long productId,
            String materialNumber,
            String productName,
            String unitOfMeasure,
            int quantity,
            int confirmedQuantity,
            int deliveredQuantity,
            int billedQuantity,
            int openQuantity,
            BigDecimal unitPrice,
            BigDecimal itemDiscountPercent,
            BigDecimal netAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal grossAmount,
            CodeText rejectionReason,
            List<Condition> conditions
    ) {
    }

    public record Condition(
            int stepNumber,
            String conditionType,
            String description,
            BigDecimal rate,
            String rateUnit,
            BigDecimal conditionValue,
            boolean statistical
    ) {
    }
}
