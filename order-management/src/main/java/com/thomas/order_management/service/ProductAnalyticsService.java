package com.thomas.order_management.service;

import com.thomas.order_management.dto.CategoryStatistics;
import com.thomas.order_management.dto.ProductRanking;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Advanced Analytics Service mit komplexen SQL-Abfragen
 * Demonstriert PostgreSQL Advanced Features und Performance-Optimierung
 */
@Service
@Transactional(readOnly = true)
public class ProductAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(ProductAnalyticsService.class);

    private final ProductRepository productRepository;

    public ProductAnalyticsService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ================ WINDOW FUNCTIONS ================

    /**
     * Verwendet Window Functions für Produkt-Rankings
     * Zeigt PostgreSQL OVER() Klauseln und Partitionierung
     */
    public List<ProductRanking> getProductRankings() {
        logger.info("Executing product rankings with window functions");
        
        List<Object[]> results = productRepository.getProductRankingsWithWindowFunctions();
        
        return results.stream()
                .map(row -> new ProductRanking(
                    ((Number) row[0]).longValue(),           // id
                    (String) row[1],                         // name
                    (String) row[2],                         // category
                    (BigDecimal) row[3],                     // price
                    ((Number) row[4]).intValue(),            // stock_quantity
                    ((java.sql.Timestamp) row[5]).toLocalDateTime(), // created_at
                    ((Number) row[6]).intValue(),            // category_rank
                    ((Number) row[7]).intValue(),            // overall_rank
                    (BigDecimal) row[8]                      // category_average_price
                ))
                .collect(Collectors.toList());
    }

    // ================ COMMON TABLE EXPRESSIONS (CTE) ================

    /**
     * Verwendet CTEs für komplexe Kategorie-Statistiken
     * Demonstriert WITH-Klauseln und Multi-Level-Aggregationen
     */
    public List<CategoryStatistics> getAdvancedCategoryStatistics() {
        logger.info("Executing advanced category statistics with CTE");
        
        List<Object[]> results = productRepository.getAdvancedCategoryStatistics();
        
        return results.stream()
                .map(row -> new CategoryStatistics(
                    (String) row[0],                         // category
                    ((Number) row[1]).longValue(),           // product_count
                    (BigDecimal) row[2],                     // avg_price
                    (BigDecimal) row[3],                     // total_value
                    (BigDecimal) row[4],                     // min_price
                    (BigDecimal) row[5],                     // max_price
                    ((Number) row[6]).longValue()            // total_stock
                ))
                .collect(Collectors.toList());
    }

    // ================ SUBQUERIES & CORRELATED QUERIES ================

    /**
     * Findet Produkte über dem Kategorie-Durchschnitt
     * Demonstriert korrelierte Subqueries
     */
    public List<Product> getProductsAboveCategoryAverage() {
        logger.info("Finding products above category average using correlated subquery");
        return productRepository.findProductsAboveCategoryAverage();
    }

    /**
     * Kategorie-Statistiken mit HAVING-Klausel
     * Zeigt Gruppierung mit Filterbedingungen
     */
    public List<CategoryStatistics> getCategoryStatistics(Long minProductCount) {
        logger.info("Getting category statistics with minimum product count: {}", minProductCount);
        
        List<Object[]> results = productRepository.getCategoryStatisticsNative(minProductCount);
        
        return results.stream()
                .map(row -> new CategoryStatistics(
                    (String) row[0],                         // category
                    ((Number) row[1]).longValue(),           // productCount
                    (BigDecimal) row[2]                      // averagePrice
                ))
                .collect(Collectors.toList());
    }

    // ================ TIME-BASED ANALYTICS ================

    /**
     * Zeitbasierte Produktanalyse
     * Demonstriert Datum-Bereiche und temporale Queries
     */
    public List<Product> getProductsByTimeRange(LocalDateTime startDate, LocalDateTime endDate) {
        logger.info("Analyzing products created between {} and {}", startDate, endDate);
        return productRepository.findProductsCreatedBetween(startDate, endDate);
    }

    /**
     * Monatliche Produkterstellung-Trends
     */
    public Map<String, Long> getMonthlyCreationTrends() {
        logger.info("Calculating monthly product creation trends");
        
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<Product> recentProducts = productRepository.findProductsCreatedBetween(
            oneYearAgo, LocalDateTime.now()
        );
        
        return recentProducts.stream()
                .collect(Collectors.groupingBy(
                    product -> product.getCreatedAt().getYear() + "-" + 
                              String.format("%02d", product.getCreatedAt().getMonthValue()),
                    Collectors.counting()
                ));
    }

    // ================ SEARCH & PERFORMANCE ================

    /**
     * Erweiterte Produktsuche mit Relevanz-Ranking
     * Demonstriert Full-Text-Search Patterns
     */
    public Page<Product> searchProductsAdvanced(String searchTerm, Pageable pageable) {
        logger.info("Performing advanced product search for term: '{}'", searchTerm);
        return productRepository.searchProducts(searchTerm, pageable);
    }

    /**
     * Performance-optimierte Kategoriesuche
     * Zeigt Index-bewusste Abfragen
     */
    public List<Product> findProductsOptimized(String category, BigDecimal minPrice, BigDecimal maxPrice) {
        logger.info("Executing optimized product search: category={}, priceRange=[{}, {}]", 
                   category, minPrice, maxPrice);
        return productRepository.findByCategoryAndPriceRange(category, minPrice, maxPrice);
    }

    // ================ BUSINESS ANALYTICS ================

    /**
     * Inventory Value Analysis
     * Berechnet Lagerwerte pro Kategorie
     */
    public List<Map<String, Object>> getInventoryAnalysis() {
        logger.info("Performing inventory value analysis");
        
        List<Object[]> results = productRepository.getInventoryAnalysis();
        
        return results.stream()
                .map(row -> Map.of(
                    "category", row[0],
                    "productCount", row[1],
                    "totalStock", row[2],
                    "inventoryValue", row[3],
                    "avgProductValue", row[4]
                ))
                .collect(Collectors.toList());
    }

    /**
     * Price Distribution Analysis
     * Analysiert Preisverteilung in Kategorien
     */
    public List<Map<String, Object>> getPriceDistributionAnalysis() {
        logger.info("Analyzing price distribution across product ranges");
        
        List<Object[]> results = productRepository.getPriceDistributionAnalysis();
        
        return results.stream()
                .map(row -> Map.of(
                    "priceCategory", row[0],
                    "productCount", row[1],
                    "averagePrice", row[2]
                ))
                .collect(Collectors.toList());
    }

    // ================ PERFORMANCE METRICS ================

    /**
     * Database Performance Monitoring
     * Sammelt Query-Performance Metriken
     */
    public Map<String, Object> getPerformanceMetrics() {
        logger.info("Collecting database performance metrics");
        
        long startTime = System.currentTimeMillis();
        long totalProducts = productRepository.count();
        long activeProducts = productRepository.countActiveProducts();
        List<String> categories = productRepository.findAllCategories();
        long queryTime = System.currentTimeMillis() - startTime;
        
        return Map.of(
            "totalProducts", totalProducts,
            "activeProducts", activeProducts,
            "categoryCount", categories.size(),
            "queryExecutionTimeMs", queryTime,
            "timestamp", LocalDateTime.now()
        );
    }

    // ================ COMPLEX BUSINESS LOGIC ================

    /**
     * Comprehensive Product Analytics Dashboard Data
     * Kombiniert mehrere komplexe Abfragen
     */
    public Map<String, Object> getDashboardAnalytics() {
        logger.info("Generating comprehensive dashboard analytics");
        
        return Map.of(
            "categoryStatistics", getCategoryStatistics(1L),
            "inventoryAnalysis", getInventoryAnalysis(),
            "priceDistribution", getPriceDistributionAnalysis(),
            "monthlyTrends", getMonthlyCreationTrends(),
            "performanceMetrics", getPerformanceMetrics(),
            "generatedAt", LocalDateTime.now()
        );
    }
}