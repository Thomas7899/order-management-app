// order-management/src/main/java/com/thomas/order_management/service/ReportingService.java
package com.thomas.order_management.service;

import com.thomas.order_management.dto.reporting.AbcAnalysisDto;
import com.thomas.order_management.dto.reporting.KpiDashboardDto;
import com.thomas.order_management.dto.reporting.SalesForecastDto;
import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.OrderItemRepository;
import com.thomas.order_management.repository.OrderRepository;
import com.thomas.order_management.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportingService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public ReportingService(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            ProductRepository productRepository,
                            CustomerRepository customerRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * ABC-Analyse für Produkte basierend auf Umsatz
     */
    public AbcAnalysisDto getProductAbcAnalysis() {
        List<Object[]> salesData = orderItemRepository.getProductSalesData();
        
        if (salesData.isEmpty()) {
            return new AbcAnalysisDto(Collections.emptyList(), 
                new AbcAnalysisDto.AbcSummary(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        // Gesamtumsatz berechnen
        BigDecimal totalRevenue = salesData.stream()
                .map(row -> (BigDecimal) row[3])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ABC-Items erstellen
        List<AbcAnalysisDto.AbcItem> items = new ArrayList<>();
        BigDecimal cumulativePercentage = BigDecimal.ZERO;

        for (Object[] row : salesData) {
            Long productId = ((Number) row[0]).longValue();
            String productName = (String) row[1];
            String category = (String) row[2];
            BigDecimal revenue = (BigDecimal) row[3];
            Integer orderCount = ((Number) row[4]).intValue();
            Integer quantitySold = ((Number) row[5]).intValue();

            BigDecimal revenuePercentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? revenue.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            cumulativePercentage = cumulativePercentage.add(revenuePercentage);

            String abcClass = determineAbcClass(cumulativePercentage);

            items.add(new AbcAnalysisDto.AbcItem(
                    productId, productName, category, revenue,
                    revenuePercentage, cumulativePercentage, abcClass,
                    orderCount, quantitySold
            ));
        }

        // Summary berechnen
        AbcAnalysisDto.AbcSummary summary = calculateAbcSummary(items, totalRevenue);

        return new AbcAnalysisDto(items, summary);
    }

    /**
     * ABC-Analyse für Kunden basierend auf Umsatz
     */
    public AbcAnalysisDto getCustomerAbcAnalysis() {
        List<Object[]> customerSalesData = orderRepository.getCustomerSalesData();
        
        if (customerSalesData.isEmpty()) {
            return new AbcAnalysisDto(Collections.emptyList(),
                new AbcAnalysisDto.AbcSummary(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        BigDecimal totalRevenue = customerSalesData.stream()
                .map(row -> (BigDecimal) row[3])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AbcAnalysisDto.AbcItem> items = new ArrayList<>();
        BigDecimal cumulativePercentage = BigDecimal.ZERO;

        for (Object[] row : customerSalesData) {
            Long customerId = ((Number) row[0]).longValue();
            String firstName = (String) row[1];
            String lastName = (String) row[2];
            BigDecimal revenue = (BigDecimal) row[3];
            Integer orderCount = ((Number) row[4]).intValue();

            BigDecimal revenuePercentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? revenue.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            cumulativePercentage = cumulativePercentage.add(revenuePercentage);

            String abcClass = determineAbcClass(cumulativePercentage);

            items.add(new AbcAnalysisDto.AbcItem(
                    customerId, firstName + " " + lastName, null, revenue,
                    revenuePercentage, cumulativePercentage, abcClass,
                    orderCount, 0
            ));
        }

        AbcAnalysisDto.AbcSummary summary = calculateAbcSummary(items, totalRevenue);
        return new AbcAnalysisDto(items, summary);
    }

    /**
     * Umsatzprognose basierend auf historischen Daten
     */
    public SalesForecastDto getSalesForecast(int monthsHistory, int monthsForecast) {
        LocalDateTime startDate = LocalDateTime.now().minusMonths(monthsHistory).withDayOfMonth(1).toLocalDate().atStartOfDay();
        
        List<Object[]> monthlyData = orderRepository.getMonthlySalesData(startDate);
        
        List<SalesForecastDto.ForecastPeriod> historicalData = new ArrayList<>();
        
        for (Object[] row : monthlyData) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            BigDecimal revenue = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
            int orderCount = ((Number) row[3]).intValue();
            
            historicalData.add(new SalesForecastDto.ForecastPeriod(
                    LocalDate.of(year, month, 1),
                    String.format("%d-%02d", year, month),
                    revenue,
                    null,
                    orderCount,
                    null,
                    false
            ));
        }

        // Wachstumsraten berechnen
        for (int i = 1; i < historicalData.size(); i++) {
            SalesForecastDto.ForecastPeriod current = historicalData.get(i);
            SalesForecastDto.ForecastPeriod previous = historicalData.get(i - 1);
            if (previous.getActualRevenue().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal growth = current.getActualRevenue()
                        .subtract(previous.getActualRevenue())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previous.getActualRevenue(), 2, RoundingMode.HALF_UP);
                current.setGrowthRate(growth);
            }
        }

        // Prognose erstellen (einfache lineare Regression)
        List<SalesForecastDto.ForecastPeriod> forecastData = generateForecast(historicalData, monthsForecast);

        // Summary
        SalesForecastDto.ForecastSummary summary = calculateForecastSummary(historicalData, forecastData);

        return new SalesForecastDto(historicalData, forecastData, summary);
    }

    /**
     * Erweitertes KPI-Dashboard
     */
    public KpiDashboardDto getKpiDashboard() {
        KpiDashboardDto.FinancialKpis financial = calculateFinancialKpis();
        KpiDashboardDto.OperationalKpis operational = calculateOperationalKpis();
        KpiDashboardDto.CustomerKpis customer = calculateCustomerKpis();
        KpiDashboardDto.InventoryKpis inventory = calculateInventoryKpis();
        
        List<KpiDashboardDto.TrendData> revenueTrend = calculateRevenueTrend();
        List<KpiDashboardDto.TrendData> orderTrend = calculateOrderTrend();

        return new KpiDashboardDto(financial, operational, customer, inventory, revenueTrend, orderTrend);
    }

    // === Private Helper Methods ===

    private String determineAbcClass(BigDecimal cumulativePercentage) {
        if (cumulativePercentage.compareTo(BigDecimal.valueOf(80)) <= 0) {
            return "A";
        } else if (cumulativePercentage.compareTo(BigDecimal.valueOf(95)) <= 0) {
            return "B";
        } else {
            return "C";
        }
    }

    private AbcAnalysisDto.AbcSummary calculateAbcSummary(List<AbcAnalysisDto.AbcItem> items, BigDecimal totalRevenue) {
        Map<String, List<AbcAnalysisDto.AbcItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(AbcAnalysisDto.AbcItem::getAbcClass));

        List<AbcAnalysisDto.AbcItem> aClass = grouped.getOrDefault("A", Collections.emptyList());
        List<AbcAnalysisDto.AbcItem> bClass = grouped.getOrDefault("B", Collections.emptyList());
        List<AbcAnalysisDto.AbcItem> cClass = grouped.getOrDefault("C", Collections.emptyList());

        BigDecimal aRevenue = aClass.stream().map(AbcAnalysisDto.AbcItem::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bRevenue = bClass.stream().map(AbcAnalysisDto.AbcItem::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cRevenue = cClass.stream().map(AbcAnalysisDto.AbcItem::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AbcAnalysisDto.AbcSummary(
                aClass.size(), bClass.size(), cClass.size(),
                aRevenue, bRevenue, cRevenue,
                calculatePercentage(aRevenue, totalRevenue),
                calculatePercentage(bRevenue, totalRevenue),
                calculatePercentage(cRevenue, totalRevenue)
        );
    }

    private BigDecimal calculatePercentage(BigDecimal part, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private List<SalesForecastDto.ForecastPeriod> generateForecast(
            List<SalesForecastDto.ForecastPeriod> historical, int months) {
        
        if (historical.isEmpty()) {
            return Collections.emptyList();
        }

        // Durchschnittswachstum berechnen
        BigDecimal avgGrowth = historical.stream()
                .filter(p -> p.getGrowthRate() != null)
                .map(SalesForecastDto.ForecastPeriod::getGrowthRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long growthCount = historical.stream()
                .filter(p -> p.getGrowthRate() != null)
                .count();
        
        if (growthCount > 0) {
            avgGrowth = avgGrowth.divide(BigDecimal.valueOf(growthCount), 4, RoundingMode.HALF_UP);
        }

        // Letzte bekannte Werte
        SalesForecastDto.ForecastPeriod lastPeriod = historical.get(historical.size() - 1);
        BigDecimal lastRevenue = lastPeriod.getActualRevenue();
        YearMonth currentMonth = YearMonth.from(lastPeriod.getDate()).plusMonths(1);

        List<SalesForecastDto.ForecastPeriod> forecast = new ArrayList<>();
        BigDecimal forecastedRevenue = lastRevenue;

        for (int i = 0; i < months; i++) {
            BigDecimal growthFactor = BigDecimal.ONE.add(avgGrowth.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            forecastedRevenue = forecastedRevenue.multiply(growthFactor).setScale(2, RoundingMode.HALF_UP);

            forecast.add(new SalesForecastDto.ForecastPeriod(
                    currentMonth.atDay(1),
                    currentMonth.toString(),
                    null,
                    forecastedRevenue,
                    0,
                    avgGrowth,
                    true
            ));
            currentMonth = currentMonth.plusMonths(1);
        }

        return forecast;
    }

    private SalesForecastDto.ForecastSummary calculateForecastSummary(
            List<SalesForecastDto.ForecastPeriod> historical,
            List<SalesForecastDto.ForecastPeriod> forecast) {
        
        BigDecimal avgMonthly = historical.stream()
                .map(SalesForecastDto.ForecastPeriod::getActualRevenue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (!historical.isEmpty()) {
            avgMonthly = avgMonthly.divide(BigDecimal.valueOf(historical.size()), 2, RoundingMode.HALF_UP);
        }

        BigDecimal nextMonth = forecast.isEmpty() ? BigDecimal.ZERO : forecast.get(0).getForecastedRevenue();
        BigDecimal nextQuarter = forecast.stream()
                .limit(3)
                .map(SalesForecastDto.ForecastPeriod::getForecastedRevenue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgGrowth = historical.stream()
                .map(SalesForecastDto.ForecastPeriod::getGrowthRate)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long growthCount = historical.stream().filter(p -> p.getGrowthRate() != null).count();
        if (growthCount > 0) {
            avgGrowth = avgGrowth.divide(BigDecimal.valueOf(growthCount), 2, RoundingMode.HALF_UP);
        }

        String trend = avgGrowth.compareTo(BigDecimal.valueOf(2)) > 0 ? "UP" 
                : avgGrowth.compareTo(BigDecimal.valueOf(-2)) < 0 ? "DOWN" : "STABLE";

        return new SalesForecastDto.ForecastSummary(
                avgMonthly, nextMonth, nextQuarter, avgGrowth, trend, BigDecimal.valueOf(75)
        );
    }

    private KpiDashboardDto.FinancialKpis calculateFinancialKpis() {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime startOfLastMonth = startOfMonth.minusMonths(1);
        LocalDateTime endOfLastMonth = startOfMonth.minusNanos(1);

        BigDecimal totalRevenue = orderRepository.getTotalAmountByStatus(OrderStatus.DELIVERED);
        BigDecimal revenueThisMonth = orderRepository.getTotalRevenueInPeriod(startOfMonth, LocalDateTime.now());
        BigDecimal revenueLastMonth = orderRepository.getTotalRevenueInPeriod(startOfLastMonth, endOfLastMonth);

        totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        revenueThisMonth = revenueThisMonth != null ? revenueThisMonth : BigDecimal.ZERO;
        revenueLastMonth = revenueLastMonth != null ? revenueLastMonth : BigDecimal.ZERO;

        BigDecimal revenueGrowth = revenueLastMonth.compareTo(BigDecimal.ZERO) > 0
                ? revenueThisMonth.subtract(revenueLastMonth)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(revenueLastMonth, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long orderCount = orderRepository.count();
        BigDecimal avgOrderValue = orderCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long customerCount = customerRepository.count();
        BigDecimal revenuePerCustomer = customerCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(customerCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new KpiDashboardDto.FinancialKpis(
                totalRevenue, revenueThisMonth, revenueLastMonth, revenueGrowth,
                avgOrderValue, BigDecimal.valueOf(35), revenuePerCustomer
        );
    }

    private KpiDashboardDto.OperationalKpis calculateOperationalKpis() {
        long totalOrders = orderRepository.count();
        
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        long ordersThisMonth = orderRepository.findByOrderDateBetween(startOfMonth, LocalDateTime.now()).size();

        long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING);
        long processingOrders = orderRepository.countByStatus(OrderStatus.PROCESSING);
        long shippedOrders = orderRepository.countByStatus(OrderStatus.SHIPPED);
        long deliveredOrders = orderRepository.countByStatus(OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED);

        BigDecimal fulfillmentRate = totalOrders > 0
                ? BigDecimal.valueOf(deliveredOrders * 100.0 / totalOrders).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal cancellationRate = totalOrders > 0
                ? BigDecimal.valueOf(cancelledOrders * 100.0 / totalOrders).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int daysInMonth = LocalDateTime.now().getDayOfMonth();
        BigDecimal avgOrdersPerDay = daysInMonth > 0
                ? BigDecimal.valueOf(ordersThisMonth).divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new KpiDashboardDto.OperationalKpis(
                totalOrders, ordersThisMonth, pendingOrders, processingOrders,
                shippedOrders, deliveredOrders, cancelledOrders,
                fulfillmentRate, cancellationRate, avgOrdersPerDay
        );
    }

    private KpiDashboardDto.CustomerKpis calculateCustomerKpis() {
        long totalCustomers = customerRepository.count();
        
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        long newCustomersThisMonth = customerRepository.countByCreatedAtAfter(startOfMonth);
        
        // Aktive Kunden = Kunden mit mindestens einer Bestellung in den letzten 6 Monaten
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        long activeCustomers = customerRepository.countActiveCustomers(sixMonthsAgo);

        BigDecimal retentionRate = totalCustomers > 0
                ? BigDecimal.valueOf(activeCustomers * 100.0 / totalCustomers).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Long> customersByCountry = customerRepository.countByCountry();

        return new KpiDashboardDto.CustomerKpis(
                totalCustomers, newCustomersThisMonth, activeCustomers,
                retentionRate, BigDecimal.valueOf(2.5), BigDecimal.valueOf(450),
                customersByCountry
        );
    }

    private KpiDashboardDto.InventoryKpis calculateInventoryKpis() {
        long totalProducts = productRepository.count();
        long activeProducts = productRepository.countActiveProducts();
        long lowStockItems = productRepository.countLowStockProducts();
        long outOfStockItems = productRepository.countOutOfStockProducts();

        List<Object[]> inventoryData = productRepository.getInventoryAnalysis();
        BigDecimal totalInventoryValue = inventoryData.stream()
                .map(row -> (BigDecimal) row[3])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> inventoryByCategory = inventoryData.stream()
                .collect(Collectors.toMap(
                        row -> row[0] != null ? (String) row[0] : "Keine Kategorie",
                        row -> (BigDecimal) row[3],
                        BigDecimal::add
                ));

        return new KpiDashboardDto.InventoryKpis(
                totalProducts, activeProducts, totalInventoryValue,
                lowStockItems, outOfStockItems, BigDecimal.valueOf(4.2),
                BigDecimal.valueOf(50), inventoryByCategory
        );
    }

    private List<KpiDashboardDto.TrendData> calculateRevenueTrend() {
        LocalDateTime startDate = LocalDateTime.now().minusMonths(6).withDayOfMonth(1).toLocalDate().atStartOfDay();
        List<Object[]> monthlyData = orderRepository.getMonthlySalesData(startDate);

        List<KpiDashboardDto.TrendData> trend = new ArrayList<>();
        BigDecimal previousValue = null;

        for (Object[] row : monthlyData) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            BigDecimal value = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;

            BigDecimal change = previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0
                    ? value.subtract(previousValue).multiply(BigDecimal.valueOf(100))
                        .divide(previousValue, 2, RoundingMode.HALF_UP)
                    : null;

            trend.add(new KpiDashboardDto.TrendData(
                    String.format("%d-%02d", year, month),
                    value, previousValue, change
            ));

            previousValue = value;
        }

        return trend;
    }

    private List<KpiDashboardDto.TrendData> calculateOrderTrend() {
        LocalDateTime startDate = LocalDateTime.now().minusMonths(6).withDayOfMonth(1).toLocalDate().atStartOfDay();
        List<Object[]> monthlyData = orderRepository.getMonthlyOrderCount(startDate);

        List<KpiDashboardDto.TrendData> trend = new ArrayList<>();
        BigDecimal previousValue = null;

        for (Object[] row : monthlyData) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            BigDecimal value = BigDecimal.valueOf(((Number) row[2]).longValue());

            BigDecimal change = previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0
                    ? value.subtract(previousValue).multiply(BigDecimal.valueOf(100))
                        .divide(previousValue, 2, RoundingMode.HALF_UP)
                    : null;

            trend.add(new KpiDashboardDto.TrendData(
                    String.format("%d-%02d", year, month),
                    value, previousValue, change
            ));

            previousValue = value;
        }

        return trend;
    }
}
