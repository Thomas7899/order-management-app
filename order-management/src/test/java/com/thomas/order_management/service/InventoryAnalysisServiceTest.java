package com.thomas.order_management.service;

import com.thomas.order_management.dto.inventory.InventoryAnalysisDto.*;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.StockMovement.MovementType;
import com.thomas.order_management.model.StockMovement.ReferenceType;
import com.thomas.order_management.model.Warehouse;
import com.thomas.order_management.model.WarehouseStock;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.WarehouseRepository;
import com.thomas.order_management.repository.WarehouseStockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Bestandsanalyse")
class InventoryAnalysisServiceTest {

    @Nested
    @DisplayName("Verbrauch je Woche")
    class WeeklyDemandTest {

        @Test
        @DisplayName("Gleichmäßiger Verbrauch: Tagesbedarf, stabiler Trend, hohe Verlässlichkeit")
        void shouldHandleSteadyDemand() {
            var demand = new InventoryAnalysisService.WeeklyDemand(new double[]{7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7});
            assertThat(demand.smoothedDaily()).isCloseTo(1.0, within(0.001));
            assertThat(demand.trend()).isEqualTo("STABLE");
            assertThat(demand.confidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Steigender Verbrauch wird erkannt und stärker gewichtet")
        void shouldDetectRisingDemand() {
            var demand = new InventoryAnalysisService.WeeklyDemand(new double[]{2, 2, 2, 2, 2, 2, 2, 2, 6, 7, 8, 9});
            assertThat(demand.trend()).isEqualTo("RISING");
            assertThat(demand.smoothedDaily()).isGreaterThan(average(demand) / 7);
        }

        private double average(InventoryAnalysisService.WeeklyDemand demand) {
            return java.util.Arrays.stream(demand.weeks()).average().orElse(0);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @DisplayName("Analyse mit Bestandsdaten")
    class WithData {

        @Autowired private InventoryAnalysisService service;
        @Autowired private StockPostingService postingService;
        @Autowired private ProductRepository productRepository;
        @Autowired private WarehouseRepository warehouseRepository;
        @Autowired private WarehouseStockRepository stockRepository;

        @Test
        @DisplayName("Schlägt Nachschub unter Meldebestand vor und meldet Ladenhüter")
        void shouldRecommendReorderAndFlagSlowMover() {
            String suffix = UUID.randomUUID().toString().substring(0, 6);
            Warehouse warehouse = warehouseRepository.save(new Warehouse("W" + suffix.substring(0, 3), "Testlager " + suffix));

            Product fast = product("Schnelldreher " + suffix, "F" + suffix);
            Product slow = product("Ladenhüter " + suffix, "S" + suffix);
            LocalDate today = LocalDate.now();
            post(MovementType.INITIAL_ENTRY, fast, null, warehouse, 40, today.minusDays(83));
            post(MovementType.INITIAL_ENTRY, slow, null, warehouse, 20, today.minusDays(83));
            // 12 Wochen lang 3 Stück pro Woche ausgeliefert: 40 - 36 = 4 Stück Restbestand
            for (int week = 0; week < 12; week++) {
                post(MovementType.DELIVERY_ISSUE, fast, warehouse, null, 3, today.minusDays(80 - week * 7L));
            }
            WarehouseStock stock = stockRepository.findByWarehouseIdAndProductId(warehouse.getId(), fast.getId()).orElseThrow();
            stock.setMinStock(5);
            stock.setMaxStock(20);
            stockRepository.save(stock);

            Report report = service.analyze();

            ReorderRecommendation reorder = report.reorderRecommendations().stream()
                    .filter(r -> r.productId().equals(fast.getId())).findFirst().orElseThrow();
            assertThat(reorder.currentStock()).isEqualTo(4);
            assertThat(reorder.recommendedOrderQuantity()).isGreaterThanOrEqualTo(16);
            assertThat(reorder.dailyDemand()).isCloseTo(3 / 7.0, within(0.05));
            assertThat(reorder.urgency()).isIn("MEDIUM", "HIGH");
            assertThat(reorder.estimatedCost()).isEqualByComparingTo(new BigDecimal("60.00").multiply(BigDecimal.valueOf(reorder.recommendedOrderQuantity())));

            DemandForecast forecast = report.demandForecasts().stream()
                    .filter(f -> f.productId().equals(fast.getId())).findFirst().orElseThrow();
            assertThat(forecast.predictedDemand30Days()).isBetween(11, 15);
            assertThat(forecast.trendDirection()).isEqualTo("STABLE");

            assertThat(report.anomalies()).anyMatch(a -> a.productId().equals(slow.getId()) && a.anomalyType().equals("SLOW_MOVER"));
            assertThat(report.narrativeSource()).isEqualTo("REGELN");
            assertThat(report.executiveSummary()).isNotBlank();
            assertThat(report.healthScore().grade()).isNotBlank();
        }

        private Product product(String name, String number) {
            Product p = new Product(name, null, new BigDecimal("100.00"), 0);
            p.setMaterialNumber(number);
            p.setCostPrice(new BigDecimal("60.00"));
            return productRepository.save(p);
        }

        private void post(MovementType type, Product product, Warehouse source, Warehouse target, int qty, LocalDate date) {
            postingService.post(new StockPostingService.Posting(type, product, source, target, qty, date.atTime(10, 0),
                    "Test", ReferenceType.MANUAL, null));
        }
    }
}
