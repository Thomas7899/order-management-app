package com.thomas.order_management.dto.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DeliveryDto(
        Long id,
        Long version,
        String deliveryNumber,
        String deliveryType,
        Long orderId,
        String orderNumber,
        PartnerDto shipTo,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        CodeText status,
        CodeText billingStatus,
        LocalDate plannedGoodsIssueDate,
        LocalDateTime goodsIssueDate,
        String goodsIssueDocument,
        String shippingAddress,
        int totalQuantity,
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
            String binLocation
    ) {
    }
}
