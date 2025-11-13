package com.thomas.order_management.dto;

import java.math.BigDecimal;

/**
 * DTOs for analytics endpoints to ensure type safety.
 */
public class AnalyticsDto {

    public record InventoryAnalysisDto(
            String category,
            Long productCount,
            Long totalStock,
            BigDecimal inventoryValue,
            BigDecimal avgProductValue
    ) {}

    public record PriceDistributionDto(
            String priceCategory,
            Long productCount,
            BigDecimal averagePrice
    ) {}

    public record PerformanceMetricsDto(
            long totalProducts,
            long activeProducts,
            int categoryCount,
            long queryExecutionTimeMs
    ) {}
}