// order-management/src/main/java/com/thomas/order_management/dto/reporting/KpiDashboardDto.java
package com.thomas.order_management.dto.reporting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Kennzahlen aus Auftrags-, Liefer-, Faktura- und Bestandsdaten. Umsätze sind fakturierte
 * Nettoumsätze nach Stornos, Kosten stammen aus der statistischen Kondition VPRS.
 */
public record KpiDashboardDto(
        FinancialKpis financial,
        OperationalKpis operational,
        CustomerKpis customer,
        InventoryKpis inventory,
        List<TrendData> revenueTrend,
        List<TrendData> orderTrend
) {

    public record FinancialKpis(
            BigDecimal revenueLast12Months,
            BigDecimal revenueThisMonth,
            BigDecimal revenueLastMonth,
            BigDecimal revenueGrowth,
            BigDecimal revenueYearToDate,
            BigDecimal averageOrderValue,
            BigDecimal grossMarginAmount,
            BigDecimal grossMarginPercent,
            BigDecimal revenuePerCustomer,
            BigDecimal openReceivables,
            BigDecimal overdueReceivables,
            BigDecimal daysSalesOutstanding
    ) {
    }

    public record OperationalKpis(
            long totalOrders,
            long ordersThisMonth,
            BigDecimal orderIntakeThisMonth,
            long openOrders,
            long inProcessOrders,
            long completedOrders,
            long cancelledOrders,
            long creditBlockedOrders,
            BigDecimal onTimeDeliveryRate,
            BigDecimal averageLeadTimeDays,
            BigDecimal cancellationRate,
            BigDecimal averageOrdersPerDay
    ) {
    }

    public record CustomerKpis(
            long totalCustomers,
            long newCustomersThisMonth,
            long activeCustomers,
            BigDecimal activeCustomerRate,
            BigDecimal averagePurchaseFrequency,
            BigDecimal customerLifetimeValue,
            Map<String, Long> customersByCountry
    ) {
    }

    public record InventoryKpis(
            long totalProducts,
            long activeProducts,
            BigDecimal totalInventoryValue,
            long lowStockItems,
            long outOfStockItems,
            BigDecimal stockTurnoverRate,
            BigDecimal daysOfSupply,
            long backlogItems,
            Map<String, BigDecimal> inventoryByCategory
    ) {
    }

    public record TrendData(
            String period,
            BigDecimal value,
            BigDecimal previousValue,
            BigDecimal changePercentage
    ) {
    }
}
