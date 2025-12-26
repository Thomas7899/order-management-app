// order-management/src/main/java/com/thomas/order_management/dto/reporting/KpiDashboardDto.java
package com.thomas.order_management.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KpiDashboardDto {
    
    // Finanz-KPIs
    private FinancialKpis financial;
    
    // Operations-KPIs
    private OperationalKpis operational;
    
    // Kunden-KPIs
    private CustomerKpis customer;
    
    // Lager-KPIs
    private InventoryKpis inventory;
    
    // Trends
    private List<TrendData> revenueTrend;
    private List<TrendData> orderTrend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialKpis {
        private BigDecimal totalRevenue;
        private BigDecimal revenueThisMonth;
        private BigDecimal revenueLastMonth;
        private BigDecimal revenueGrowth;
        private BigDecimal averageOrderValue;
        private BigDecimal grossMargin;
        private BigDecimal revenuePerCustomer;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationalKpis {
        private long totalOrders;
        private long ordersThisMonth;
        private long pendingOrders;
        private long processingOrders;
        private long shippedOrders;
        private long deliveredOrders;
        private long cancelledOrders;
        private BigDecimal fulfillmentRate;
        private BigDecimal cancellationRate;
        private BigDecimal averageOrdersPerDay;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerKpis {
        private long totalCustomers;
        private long newCustomersThisMonth;
        private long activeCustomers;
        private BigDecimal customerRetentionRate;
        private BigDecimal averagePurchaseFrequency;
        private BigDecimal customerLifetimeValue;
        private Map<String, Long> customersByCountry;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryKpis {
        private long totalProducts;
        private long activeProducts;
        private BigDecimal totalInventoryValue;
        private long lowStockItems;
        private long outOfStockItems;
        private BigDecimal stockTurnoverRate;
        private BigDecimal averageStockLevel;
        private Map<String, BigDecimal> inventoryByCategory;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        private String period;
        private BigDecimal value;
        private BigDecimal previousValue;
        private BigDecimal changePercentage;
    }
}
