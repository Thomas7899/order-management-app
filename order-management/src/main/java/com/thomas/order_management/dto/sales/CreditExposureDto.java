package com.thomas.order_management.dto.sales;

import java.math.BigDecimal;

public record CreditExposureDto(
        Long customerId,
        String customerNumber,
        BigDecimal creditLimit,
        BigDecimal openOrderValue,
        BigDecimal openItemsValue,
        BigDecimal totalExposure,
        BigDecimal availableCredit,
        BigDecimal utilizationPercent
) {
}
