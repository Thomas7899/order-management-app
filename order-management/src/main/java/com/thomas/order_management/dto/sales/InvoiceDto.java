package com.thomas.order_management.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceDto(
        Long id,
        Long version,
        String invoiceNumber,
        CodeText billingType,
        Long orderId,
        String orderNumber,
        Long deliveryId,
        String deliveryNumber,
        String referenceInvoiceNumber,
        PartnerDto payer,
        String billingAddress,
        LocalDate billingDate,
        CodeText paymentTerms,
        LocalDate dueDate,
        LocalDate cashDiscountDate,
        BigDecimal cashDiscountPercent,
        String currency,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        BigDecimal costAmount,
        BigDecimal marginAmount,
        CodeText status,
        boolean overdue,
        int daysOverdue,
        LocalDate paidAt,
        BigDecimal paidAmount,
        BigDecimal cashDiscountAmount,
        String createdBy,
        LocalDateTime createdAt,
        List<Item> items
) {

    public record Item(
            int itemNumber,
            int orderItemNumber,
            Long productId,
            String materialNumber,
            String productName,
            String unitOfMeasure,
            int quantity,
            BigDecimal unitNetPrice,
            BigDecimal netAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal grossAmount
    ) {
    }
}
