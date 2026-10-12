package com.thomas.order_management.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Belegfluss eines Auftrags (SAP: VA03 → Belegfluss): Auftragseingang → Auftrag → Lieferungen → Warenausgänge → Fakturen.
 */
public record DocumentFlowDto(String orderNumber, List<Node> nodes) {

    public enum DocumentType { SALES_ORDER_REQUEST, SALES_ORDER, DELIVERY, GOODS_ISSUE, GOODS_ISSUE_REVERSAL, INVOICE, CANCELLATION_INVOICE, PAYMENT }

    public record Node(
            DocumentType documentType,
            String documentTypeText,
            Long id,
            String number,
            String parentNumber,
            int level,
            LocalDateTime date,
            CodeText status,
            BigDecimal amount
    ) {
    }
}
