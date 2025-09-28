package com.thomas.order_management.controller;

import com.thomas.order_management.dto.CategoryStatistics;
import com.thomas.order_management.dto.ProductRanking;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.service.ProductAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Analytics Controller für Advanced SQL Features
 * Demonstriert komplexe Database Queries und Performance-Optimierung
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "http://localhost:4200")
public class ProductAnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(ProductAnalyticsController.class);

    private final ProductAnalyticsService analyticsService;

    public ProductAnalyticsController(ProductAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    // ================ WINDOW FUNCTIONS ENDPOINTS ================

    /**
     * GET /api/analytics/product-rankings
     * Verwendet Window Functions für Produkt-Rankings
     */
    @GetMapping("/product-rankings")
    public ResponseEntity<List<ProductRanking>> getProductRankings() {
        logger.info("GET /api/analytics/product-rankings - Window Functions Demo");
        
        List<ProductRanking> rankings = analyticsService.getProductRankings();
        
        logger.info("Retrieved {} product rankings using window functions", rankings.size());
        return ResponseEntity.ok(rankings);
    }

    // ================ CTE (Common Table Expressions) ================

    /**
     * GET /api/analytics/category-statistics/advanced
     * Verwendet CTEs für komplexe Kategorie-Statistiken
     */
    @GetMapping("/category-statistics/advanced")
    public ResponseEntity<List<CategoryStatistics>> getAdvancedCategoryStatistics() {
        logger.info("GET /api/analytics/category-statistics/advanced - CTE Demo");
        
        List<CategoryStatistics> statistics = analyticsService.getAdvancedCategoryStatistics();
        
        logger.info("Retrieved advanced category statistics using CTE for {} categories", statistics.size());
        return ResponseEntity.ok(statistics);
    }

    // ================ SUBQUERIES & CORRELATED QUERIES ================

    /**
     * GET /api/analytics/products/above-average
     * Demonstriert korrelierte Subqueries
     */
    @GetMapping("/products/above-average")
    public ResponseEntity<List<Product>> getProductsAboveCategoryAverage() {
        logger.info("GET /api/analytics/products/above-average - Correlated Subquery Demo");
        
        List<Product> products = analyticsService.getProductsAboveCategoryAverage();
        
        logger.info("Found {} products above their category average using correlated subquery", products.size());
        return ResponseEntity.ok(products);
    }

    /**
     * GET /api/analytics/category-statistics
     * Kategorie-Statistiken mit HAVING-Klausel
     */
    @GetMapping("/category-statistics")
    public ResponseEntity<List<CategoryStatistics>> getCategoryStatistics(
            @RequestParam(defaultValue = "1") Long minProductCount) {
        
        logger.info("GET /api/analytics/category-statistics?minProductCount={} - HAVING Clause Demo", minProductCount);
        
        List<CategoryStatistics> statistics = analyticsService.getCategoryStatistics(minProductCount);
        
        logger.info("Retrieved category statistics for {} categories with min {} products", 
                   statistics.size(), minProductCount);
        return ResponseEntity.ok(statistics);
    }

    // ================ TIME-BASED ANALYTICS ================

    /**
     * GET /api/analytics/products/time-range
     * Zeitbasierte Produktanalyse
     */
    @GetMapping("/products/time-range")
    public ResponseEntity<List<Product>> getProductsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        logger.info("GET /api/analytics/products/time-range?startDate={}&endDate={} - Temporal Query Demo", 
                   startDate, endDate);
        
        List<Product> products = analyticsService.getProductsByTimeRange(startDate, endDate);
        
        logger.info("Found {} products created between {} and {}", products.size(), startDate, endDate);
        return ResponseEntity.ok(products);
    }

    /**
     * GET /api/analytics/trends/monthly
     * Monatliche Produkterstellung-Trends
     */
    @GetMapping("/trends/monthly")
    public ResponseEntity<Map<String, Long>> getMonthlyCreationTrends() {
        logger.info("GET /api/analytics/trends/monthly - Time Series Analysis");
        
        Map<String, Long> trends = analyticsService.getMonthlyCreationTrends();
        
        logger.info("Generated monthly creation trends for {} months", trends.size());
        return ResponseEntity.ok(trends);
    }

    // ================ SEARCH & PERFORMANCE ================

    /**
     * GET /api/analytics/search/advanced
     * Erweiterte Produktsuche mit Relevanz-Ranking
     */
    @GetMapping("/search/advanced")
    public ResponseEntity<Page<Product>> searchProductsAdvanced(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        logger.info("GET /api/analytics/search/advanced?query={}&page={}&size={} - Full-Text Search Demo", 
                   query, page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> results = analyticsService.searchProductsAdvanced(query, pageable);
        
        logger.info("Advanced search for '{}' returned {} results", query, results.getTotalElements());
        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/analytics/products/optimized
     * Performance-optimierte Kategoriesuche
     */
    @GetMapping("/products/optimized")
    public ResponseEntity<List<Product>> findProductsOptimized(
            @RequestParam String category,
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {
        
        logger.info("GET /api/analytics/products/optimized?category={}&minPrice={}&maxPrice={} - Index Optimization Demo", 
                   category, minPrice, maxPrice);
        
        List<Product> products = analyticsService.findProductsOptimized(category, minPrice, maxPrice);
        
        logger.info("Optimized search found {} products in category '{}' within price range [{}, {}]", 
                   products.size(), category, minPrice, maxPrice);
        return ResponseEntity.ok(products);
    }

    // ================ BUSINESS ANALYTICS ================

    /**
     * GET /api/analytics/inventory
     * Inventory Value Analysis
     */
    @GetMapping("/inventory")
    public ResponseEntity<List<Map<String, Object>>> getInventoryAnalysis() {
        logger.info("GET /api/analytics/inventory - Business Intelligence Demo");
        
        List<Map<String, Object>> analysis = analyticsService.getInventoryAnalysis();
        
        logger.info("Generated inventory analysis for {} categories", analysis.size());
        return ResponseEntity.ok(analysis);
    }

    /**
     * GET /api/analytics/price-distribution
     * Price Distribution Analysis
     */
    @GetMapping("/price-distribution")
    public ResponseEntity<List<Map<String, Object>>> getPriceDistributionAnalysis() {
        logger.info("GET /api/analytics/price-distribution - Statistical Analysis Demo");
        
        List<Map<String, Object>> analysis = analyticsService.getPriceDistributionAnalysis();
        
        logger.info("Generated price distribution analysis with {} price categories", analysis.size());
        return ResponseEntity.ok(analysis);
    }

    // ================ PERFORMANCE MONITORING ================

    /**
     * GET /api/analytics/performance
     * Database Performance Metrics
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        logger.info("GET /api/analytics/performance - Database Performance Monitoring");
        
        Map<String, Object> metrics = analyticsService.getPerformanceMetrics();
        
        logger.info("Generated performance metrics: queryTime={}ms, totalProducts={}", 
                   metrics.get("queryExecutionTimeMs"), metrics.get("totalProducts"));
        return ResponseEntity.ok(metrics);
    }

    // ================ COMPREHENSIVE DASHBOARD ================

    /**
     * GET /api/analytics/dashboard
     * Comprehensive Analytics Dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardAnalytics() {
        logger.info("GET /api/analytics/dashboard - Comprehensive Analytics Dashboard");
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> dashboard = analyticsService.getDashboardAnalytics();
        long executionTime = System.currentTimeMillis() - startTime;
        
        logger.info("Generated comprehensive dashboard analytics in {}ms", executionTime);
        return ResponseEntity.ok(dashboard);
    }

    // ================ ERROR HANDLING ================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAnalyticsException(Exception e) {
        logger.error("Analytics operation failed: {}", e.getMessage(), e);
        
        return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", "Analytics operation failed",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                ));
    }
}