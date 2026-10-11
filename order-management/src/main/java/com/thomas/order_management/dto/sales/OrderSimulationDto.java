package com.thomas.order_management.dto.sales;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ergebnis einer Auftragssimulation: Preise, Verfügbarkeit und Kreditprüfung, ohne zu sichern.
 */
public record OrderSimulationDto(
        List<Item> items,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currency,
        CodeText creditStatus,
        CreditExposureDto creditExposure,
        CodeText paymentTerms,
        List<Message> messages
) {

    public record Item(
            int itemNumber,
            Long productId,
            String materialNumber,
            String productName,
            String unitOfMeasure,
            int quantity,
            int confirmedQuantity,
            int availableQuantity,
            BigDecimal unitPrice,
            BigDecimal netAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal grossAmount,
            List<OrderDto.Condition> conditions
    ) {
    }

    /** Meldung mit Typ S (Erfolg), I (Info), W (Warnung) oder E (Fehler). */
    public record Message(String type, String messageId, String text) {
    }
}
