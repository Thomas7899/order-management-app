// order-management/src/main/java/com/thomas/order_management/service/ReportingService.java
package com.thomas.order_management.service;

import com.thomas.order_management.dto.reporting.AbcAnalysisDto;
import com.thomas.order_management.dto.reporting.KpiDashboardDto;
import com.thomas.order_management.dto.reporting.SalesForecastDto;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Auswertungen auf Basis fakturierter Umsätze (nach Stornos), Lieferungen und Bestände.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InvoiceRepository invoiceRepository;
    private final DeliveryRepository deliveryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final WarehouseStockRepository stockRepository;

    public ReportingService(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            InvoiceRepository invoiceRepository,
                            DeliveryRepository deliveryRepository,
                            ProductRepository productRepository,
                            CustomerRepository customerRepository,
                            WarehouseStockRepository stockRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.invoiceRepository = invoiceRepository;
        this.deliveryRepository = deliveryRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.stockRepository = stockRepository;
    }

    // ------------------------------------------------------------------
    // ABC-Analyse
    // ------------------------------------------------------------------

    /** ABC-Analyse der Materialien nach fakturiertem Nettoumsatz (A ≤ 80 %, B ≤ 95 %, C Rest). */
    public AbcAnalysisDto getProductAbcAnalysis() {
        List<AbcAnalysisDto.AbcItem> items = new ArrayList<>();
        for (Object[] row : invoiceRepository.getProductRevenue()) {
            items.add(new AbcAnalysisDto.AbcItem(
                    ((Number) row[0]).longValue(), (String) row[1], (String) row[2], (String) row[3],
                    toBigDecimal(row[4]), null, null, null,
                    ((Number) row[5]).intValue(), ((Number) row[6]).intValue()));
        }
        return classify(items);
    }

    /** ABC-Analyse der Debitoren nach fakturiertem Nettoumsatz. */
    public AbcAnalysisDto getCustomerAbcAnalysis() {
        List<AbcAnalysisDto.AbcItem> items = new ArrayList<>();
        for (Object[] row : invoiceRepository.getCustomerRevenue()) {
            items.add(new AbcAnalysisDto.AbcItem(
                    ((Number) row[0]).longValue(), (String) row[1], row[2] + " " + row[3], null,
                    toBigDecimal(row[4]), null, null, null,
                    ((Number) row[5]).intValue(), 0));
        }
        return classify(items);
    }

    private AbcAnalysisDto classify(List<AbcAnalysisDto.AbcItem> items) {
        BigDecimal total = items.stream().map(AbcAnalysisDto.AbcItem::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (items.isEmpty() || total.signum() == 0) {
            return new AbcAnalysisDto(List.of(), new AbcAnalysisDto.AbcSummary(0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        BigDecimal cumulative = BigDecimal.ZERO;
        for (AbcAnalysisDto.AbcItem item : items) {
            BigDecimal share = percent(item.getRevenue(), total);
            // Die Klasse richtet sich nach dem kumulierten Anteil vor dem Element, damit das
            // Element, das die 80-%-Grenze überschreitet, noch zur Klasse A zählt.
            String abcClass = cumulative.compareTo(BigDecimal.valueOf(80)) < 0 ? "A"
                    : cumulative.compareTo(BigDecimal.valueOf(95)) < 0 ? "B" : "C";
            cumulative = cumulative.add(share);
            item.setRevenuePercentage(share);
            item.setCumulativePercentage(cumulative.min(HUNDRED));
            item.setAbcClass(abcClass);
        }

        Map<String, List<AbcAnalysisDto.AbcItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(AbcAnalysisDto.AbcItem::getAbcClass));
        BigDecimal a = revenue(grouped.get("A"));
        BigDecimal b = revenue(grouped.get("B"));
        BigDecimal c = revenue(grouped.get("C"));
        return new AbcAnalysisDto(items, new AbcAnalysisDto.AbcSummary(
                size(grouped.get("A")), size(grouped.get("B")), size(grouped.get("C")),
                a, b, c, percent(a, total), percent(b, total), percent(c, total)));
    }

    // ------------------------------------------------------------------
    // Umsatzprognose
    // ------------------------------------------------------------------

    /**
     * Prognose des fakturierten Nettoumsatzes per linearer Regression über abgeschlossene Monate.
     * Die Konfidenz ergibt sich aus 100 − mittlerer absoluter prozentualer Fehler (MAPE) der Anpassung.
     */
    public SalesForecastDto getSalesForecast(int monthsHistory, int monthsForecast) {
        YearMonth current = YearMonth.now();
        YearMonth first = current.minusMonths(monthsHistory);

        Map<YearMonth, Object[]> byMonth = new HashMap<>();
        for (Object[] row : invoiceRepository.getMonthlyRevenue(first.atDay(1))) {
            byMonth.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()), row);
        }

        List<SalesForecastDto.ForecastPeriod> history = new ArrayList<>();
        BigDecimal previous = null;
        for (YearMonth m = first; !m.isAfter(current); m = m.plusMonths(1)) {
            Object[] row = byMonth.get(m);
            BigDecimal revenue = row != null ? toBigDecimal(row[2]) : BigDecimal.ZERO;
            int orders = row != null ? ((Number) row[3]).intValue() : 0;
            BigDecimal growth = previous != null && previous.signum() > 0
                    ? revenue.subtract(previous).multiply(HUNDRED).divide(previous, 2, RoundingMode.HALF_UP) : null;
            history.add(new SalesForecastDto.ForecastPeriod(m.atDay(1), m.toString(), revenue, null, orders, growth, false));
            previous = revenue;
        }

        // Nur abgeschlossene Monate in die Regression einbeziehen; der laufende Monat ist unvollständig.
        List<SalesForecastDto.ForecastPeriod> complete = history.subList(0, Math.max(0, history.size() - 1));
        int n = complete.size();
        double[] y = complete.stream().mapToDouble(p -> p.getActualRevenue().doubleValue()).toArray();
        double slope = 0;
        double intercept = n > 0 ? Arrays.stream(y).average().orElse(0) : 0;
        if (n >= 2) {
            double meanX = (n - 1) / 2.0;
            double meanY = intercept;
            double num = 0, den = 0;
            for (int i = 0; i < n; i++) {
                num += (i - meanX) * (y[i] - meanY);
                den += (i - meanX) * (i - meanX);
            }
            slope = den > 0 ? num / den : 0;
            intercept = meanY - slope * meanX;
        }

        double mape = 0;
        int mapeCount = 0;
        for (int i = 0; i < n; i++) {
            if (y[i] > 0) {
                mape += Math.abs(y[i] - (intercept + slope * i)) / y[i];
                mapeCount++;
            }
        }
        double confidence = mapeCount > 0 ? Math.max(0, 100 - (mape / mapeCount) * 100) : 0;

        List<SalesForecastDto.ForecastPeriod> forecast = new ArrayList<>();
        for (int k = 0; k < monthsForecast; k++) {
            YearMonth m = current.plusMonths(k);
            double value = Math.max(0, intercept + slope * (n + k));
            forecast.add(new SalesForecastDto.ForecastPeriod(m.atDay(1), m.toString(), null,
                    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP), 0, null, true));
        }

        BigDecimal average = n > 0
                ? BigDecimal.valueOf(Arrays.stream(y).average().orElse(0)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal monthlyTrend = average.signum() > 0
                ? BigDecimal.valueOf(slope).multiply(HUNDRED).divide(average, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String direction = monthlyTrend.compareTo(BigDecimal.ONE) > 0 ? "UP"
                : monthlyTrend.compareTo(BigDecimal.ONE.negate()) < 0 ? "DOWN" : "STABLE";
        BigDecimal nextMonth = forecast.isEmpty() ? BigDecimal.ZERO : forecast.get(0).getForecastedRevenue();
        BigDecimal nextQuarter = forecast.stream().limit(3).map(SalesForecastDto.ForecastPeriod::getForecastedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SalesForecastDto(history, forecast, new SalesForecastDto.ForecastSummary(
                average, nextMonth, nextQuarter, monthlyTrend, direction,
                BigDecimal.valueOf(confidence).setScale(1, RoundingMode.HALF_UP),
                "Lineare Regression über " + n + " abgeschlossene Monate"));
    }

    // ------------------------------------------------------------------
    // KPI-Dashboard
    // ------------------------------------------------------------------

    public KpiDashboardDto getKpiDashboard() {
        LocalDate today = LocalDate.now();
        return new KpiDashboardDto(
                financialKpis(today),
                operationalKpis(today),
                customerKpis(today),
                inventoryKpis(today),
                revenueTrend(today),
                orderTrend(today));
    }

    private KpiDashboardDto.FinancialKpis financialKpis(LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate yearAgo = today.minusMonths(12).plusDays(1);

        BigDecimal revenue12m = invoiceRepository.sumNetRevenue(yearAgo, today);
        BigDecimal thisMonth = invoiceRepository.sumNetRevenue(monthStart, today);
        BigDecimal lastMonth = invoiceRepository.sumNetRevenue(lastMonthStart, monthStart.minusDays(1));
        // Vergleich mit dem gleichen Zeitraum des Vormonats, sonst wirkt jeder laufende Monat wie ein Einbruch.
        LocalDate lastMonthSamePeriodEnd = lastMonthStart.plusDays(Math.min(today.getDayOfMonth(), lastMonthStart.lengthOfMonth()) - 1);
        BigDecimal lastMonthSamePeriod = invoiceRepository.sumNetRevenue(lastMonthStart, lastMonthSamePeriodEnd);
        BigDecimal growth = lastMonthSamePeriod.signum() > 0
                ? thisMonth.subtract(lastMonthSamePeriod).multiply(HUNDRED).divide(lastMonthSamePeriod, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal ytd = invoiceRepository.sumNetRevenue(today.withDayOfYear(1), today);

        long billedOrders = invoiceRepository.countBilledOrders(yearAgo, today);
        BigDecimal avgOrderValue = billedOrders > 0
                ? revenue12m.divide(BigDecimal.valueOf(billedOrders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal cost12m = invoiceRepository.sumCost(yearAgo, today);
        BigDecimal margin = revenue12m.subtract(cost12m);
        BigDecimal marginPercent = revenue12m.signum() > 0 ? percent(margin, revenue12m) : BigDecimal.ZERO;

        long billedCustomers = invoiceRepository.countBilledCustomers(yearAgo, today);
        BigDecimal revenuePerCustomer = billedCustomers > 0
                ? revenue12m.divide(BigDecimal.valueOf(billedCustomers), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        List<Invoice> openItems = invoiceRepository.findOpenItems();
        BigDecimal open = openItems.stream().map(Invoice::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = openItems.stream().filter(i -> i.isOverdue(today))
                .map(Invoice::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gross90 = invoiceRepository.sumGrossRevenue(today.minusDays(89), today);
        BigDecimal dso = gross90.signum() > 0
                ? open.multiply(BigDecimal.valueOf(90)).divide(gross90, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return new KpiDashboardDto.FinancialKpis(revenue12m, thisMonth, lastMonth, growth, ytd, avgOrderValue,
                margin, marginPercent, revenuePerCustomer, open, overdue, dso);
    }

    private KpiDashboardDto.OperationalKpis operationalKpis(LocalDate today) {
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        long totalOrders = orderRepository.count();
        long ordersThisMonth = orderRepository.countByOrderDateBetween(monthStart, today.plusDays(1).atStartOfDay());

        BigDecimal intakeThisMonth = orderRepository.getMonthlyOrderIntake(monthStart).stream()
                .filter(r -> ((Number) r[0]).intValue() == today.getYear() && ((Number) r[1]).intValue() == today.getMonthValue())
                .map(r -> toBigDecimal(r[3]))
                .findFirst().orElse(BigDecimal.ZERO);

        long open = orderRepository.countByStatus(OrderStatus.OPEN);
        long inProcess = orderRepository.countByStatus(OrderStatus.IN_PROCESS);
        long completed = orderRepository.countByStatus(OrderStatus.COMPLETED);
        long cancelled = orderRepository.countByStatus(OrderStatus.CANCELLED);
        long creditBlocked = orderRepository.findByStatusIn(List.of(OrderStatus.OPEN, OrderStatus.IN_PROCESS)).stream()
                .filter(o -> o.getCreditStatus() == CreditStatus.BLOCKED).count();

        List<Delivery> recentDeliveries = deliveryRepository.findByStatusAndGoodsIssueDateGreaterThanEqual(
                Delivery.Status.GOODS_ISSUED, today.minusDays(90).atStartOfDay());
        long onTime = recentDeliveries.stream()
                .filter(d -> d.getOrder().getRequestedDeliveryDate() == null
                        || !d.getGoodsIssueDate().toLocalDate().isAfter(d.getOrder().getRequestedDeliveryDate()))
                .count();
        BigDecimal onTimeRate = recentDeliveries.isEmpty() ? BigDecimal.ZERO
                : percent(BigDecimal.valueOf(onTime), BigDecimal.valueOf(recentDeliveries.size()));
        double avgLeadTime = recentDeliveries.stream()
                .mapToLong(d -> ChronoUnit.DAYS.between(d.getOrder().getOrderDate().toLocalDate(), d.getGoodsIssueDate().toLocalDate()))
                .average().orElse(0);

        BigDecimal cancellationRate = totalOrders > 0
                ? percent(BigDecimal.valueOf(cancelled), BigDecimal.valueOf(totalOrders)) : BigDecimal.ZERO;
        BigDecimal perDay = BigDecimal.valueOf(ordersThisMonth)
                .divide(BigDecimal.valueOf(today.getDayOfMonth()), 2, RoundingMode.HALF_UP);

        return new KpiDashboardDto.OperationalKpis(totalOrders, ordersThisMonth, intakeThisMonth, open, inProcess,
                completed, cancelled, creditBlocked, onTimeRate,
                BigDecimal.valueOf(avgLeadTime).setScale(1, RoundingMode.HALF_UP), cancellationRate, perDay);
    }

    private KpiDashboardDto.CustomerKpis customerKpis(LocalDate today) {
        long total = customerRepository.count();
        long newThisMonth = customerRepository.countByCreatedAtAfter(today.withDayOfMonth(1).atStartOfDay());
        long active = customerRepository.countActiveCustomers(today.minusMonths(6).atStartOfDay());
        BigDecimal activeRate = total > 0 ? percent(BigDecimal.valueOf(active), BigDecimal.valueOf(total)) : BigDecimal.ZERO;

        LocalDate yearAgo = today.minusMonths(12).plusDays(1);
        long billedCustomers12m = invoiceRepository.countBilledCustomers(yearAgo, today);
        long billedOrders12m = invoiceRepository.countBilledOrders(yearAgo, today);
        BigDecimal frequency = billedCustomers12m > 0
                ? BigDecimal.valueOf(billedOrders12m).divide(BigDecimal.valueOf(billedCustomers12m), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long billedCustomersTotal = invoiceRepository.countBilledCustomersTotal();
        BigDecimal clv = billedCustomersTotal > 0
                ? invoiceRepository.sumNetRevenueTotal().divide(BigDecimal.valueOf(billedCustomersTotal), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new KpiDashboardDto.CustomerKpis(total, newThisMonth, active, activeRate, frequency, clv,
                customerRepository.countByCountry());
    }

    private KpiDashboardDto.InventoryKpis inventoryKpis(LocalDate today) {
        List<Product> products = productRepository.findAll();
        long activeProducts = products.stream().filter(p -> Boolean.TRUE.equals(p.getActive())).count();
        long outOfStock = products.stream().filter(p -> Boolean.TRUE.equals(p.getActive()) && p.getStockQuantity() == 0).count();

        Map<String, BigDecimal> byCategory = new TreeMap<>();
        BigDecimal inventoryValue = BigDecimal.ZERO;
        for (Product p : products) {
            BigDecimal value = p.getValuationPrice().multiply(BigDecimal.valueOf(p.getStockQuantity()));
            inventoryValue = inventoryValue.add(value);
            byCategory.merge(p.getCategory() != null ? p.getCategory() : "Ohne Warengruppe", value, BigDecimal::add);
        }

        BigDecimal cogs12m = invoiceRepository.sumCost(today.minusMonths(12).plusDays(1), today);
        BigDecimal turnover = inventoryValue.signum() > 0
                ? cogs12m.divide(inventoryValue, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal daysOfSupply = turnover.signum() > 0
                ? BigDecimal.valueOf(365).divide(turnover, 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return new KpiDashboardDto.InventoryKpis(products.size(), activeProducts, inventoryValue,
                stockRepository.findLowStockItems().size(), outOfStock, turnover, daysOfSupply,
                orderItemRepository.findBacklogItems().size(), byCategory);
    }

    private List<KpiDashboardDto.TrendData> revenueTrend(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        YearMonth first = current.minusMonths(11);
        Map<YearMonth, BigDecimal> values = new HashMap<>();
        for (Object[] row : invoiceRepository.getMonthlyRevenue(first.atDay(1))) {
            values.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()), toBigDecimal(row[2]));
        }
        return trend(first, current, values);
    }

    private List<KpiDashboardDto.TrendData> orderTrend(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        YearMonth first = current.minusMonths(11);
        Map<YearMonth, BigDecimal> values = new HashMap<>();
        for (Object[] row : orderRepository.getMonthlyOrderIntake(first.atDay(1).atStartOfDay())) {
            values.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                    BigDecimal.valueOf(((Number) row[2]).longValue()));
        }
        return trend(first, current, values);
    }

    private List<KpiDashboardDto.TrendData> trend(YearMonth first, YearMonth last, Map<YearMonth, BigDecimal> values) {
        List<KpiDashboardDto.TrendData> result = new ArrayList<>();
        BigDecimal previous = null;
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            BigDecimal value = values.getOrDefault(m, BigDecimal.ZERO);
            BigDecimal change = previous != null && previous.signum() > 0
                    ? value.subtract(previous).multiply(HUNDRED).divide(previous, 2, RoundingMode.HALF_UP) : null;
            result.add(new KpiDashboardDto.TrendData(m.toString(), value, previous, change));
            previous = value;
        }
        return result;
    }

    // ------------------------------------------------------------------

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) return BigDecimal.ZERO;
        return part.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal revenue(List<AbcAnalysisDto.AbcItem> items) {
        return items == null ? BigDecimal.ZERO
                : items.stream().map(AbcAnalysisDto.AbcItem::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
