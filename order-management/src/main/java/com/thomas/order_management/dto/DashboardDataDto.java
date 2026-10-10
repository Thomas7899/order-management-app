package com.thomas.order_management.dto;

import com.thomas.order_management.dto.inventory.WarehouseStockDto;
import com.thomas.order_management.dto.sales.OrderListItemDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Kennzahlen für das Launchpad. Umsätze sind fakturierte Nettoumsätze (nach Stornos).
 */
public record DashboardDataDto(
        DashboardDataDto.Stats stats,
        DashboardDataDto.RecentActivity recentActivity
) {

    public record Stats(
            long totalCustomers,
            long totalProducts,
            long totalOrders,
            Map<String, Long> ordersByStatus,
            BigDecimal totalRevenue,
            BigDecimal openOrderValue,
            BigDecimal todayRevenue,
            BigDecimal monthRevenue,
            long lowStockProductsCount,
            long openDeliveries,
            long billingDueCount,
            long openReceivablesCount,
            BigDecimal openReceivables,
            long overdueReceivablesCount,
            BigDecimal overdueReceivables,
            long creditBlockedOrders,
            long backlogItems,
            long ordersToday
    ) {
    }

    public record RecentActivity(List<OrderListItemDto> recentOrders, List<WarehouseStockDto> lowStockItems) {
    }
}
