package com.thomas.order_management.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTOs for the main dashboard to ensure type safety and clean architecture.
 */
public class DashboardDto {

    public record DashboardStatsDto(
            long totalCustomers,
            long totalProducts,
            long totalOrders,
            Map<String, Long> ordersByStatus,
            BigDecimal totalRevenue,
            BigDecimal pendingRevenue,
            BigDecimal todayRevenue,
            BigDecimal monthRevenue,
            long lowStockProductsCount
    ) {}

    public record RecentActivityDto(
            List<OrderDto> recentOrders,
            List<ProductDto> lowStockProducts
    ) {}
}