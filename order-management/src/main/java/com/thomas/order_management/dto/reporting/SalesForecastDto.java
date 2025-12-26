// order-management/src/main/java/com/thomas/order_management/dto/reporting/SalesForecastDto.java
package com.thomas.order_management.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesForecastDto {
    
    private List<ForecastPeriod> historicalData;
    private List<ForecastPeriod> forecastData;
    private ForecastSummary summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastPeriod {
        private LocalDate date;
        private String period; // "2024-01", "2024-Q1", etc.
        private BigDecimal actualRevenue;
        private BigDecimal forecastedRevenue;
        private Integer orderCount;
        private BigDecimal growthRate;
        private boolean isForecasted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastSummary {
        private BigDecimal averageMonthlyRevenue;
        private BigDecimal predictedNextMonth;
        private BigDecimal predictedNextQuarter;
        private BigDecimal growthTrend;
        private String trendDirection; // "UP", "DOWN", "STABLE"
        private BigDecimal confidenceLevel;
    }
}
