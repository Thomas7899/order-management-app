package com.thomas.order_management.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A comprehensive DTO for the main dashboard, combining stats and recent activity.
 */
public record DashboardDataDto(
        DashboardDataDto.Stats stats,
        DashboardDataDto.RecentActivity recentActivity
) {
    /**
     * DTO for the main dashboard statistics.
     */
    public record Stats(
            long totalCustomers, long totalProducts, long totalOrders,
            Map<String, Long> ordersByStatus,
            BigDecimal totalRevenue, BigDecimal pendingRevenue,
            BigDecimal todayRevenue, BigDecimal monthRevenue,
            long lowStockProductsCount
    ) {}

    /**
     * DTO for the recent activity feed on the dashboard.
     */
    public record RecentActivity(List<OrderDto> recentOrders, List<ProductDto> lowStockProducts) {}
}