package com.thomas.order_management.repository;

import com.thomas.order_management.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    List<Product> findByActiveTrue();
    
    List<Product> findByCategory(String category);
    
    List<Product> findByNameContainingIgnoreCase(String name);
    
    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);
    
    @Query("SELECT p FROM Product p WHERE p.stockQuantity > 0 AND p.active = true")
    List<Product> findAvailableProducts();
    
    @Query("SELECT p FROM Product p WHERE p.stockQuantity <= 5 AND p.active = true")
    List<Product> findLowStockProducts();
    
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL")
    List<String> findAllCategories();
    
    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true")
    long countActiveProducts();

    // ================ ADVANCED SQL FEATURES ================

    /**
     * Window Function: Produkt-Rankings mit Kategorie-Analyse
     */
    @Query(value = """
        SELECT 
            p.id,
            p.name,
            p.category,
            p.price,
            p.stock_quantity,
            p.created_at,
            ROW_NUMBER() OVER (PARTITION BY p.category ORDER BY p.price DESC) as category_rank,
            ROW_NUMBER() OVER (ORDER BY p.price DESC) as overall_rank,
            AVG(p.price) OVER (PARTITION BY p.category) as category_average_price
        FROM products p 
        WHERE p.active = true
        ORDER BY p.category, category_rank
        """, nativeQuery = true)
    List<Object[]> getProductRankingsWithWindowFunctions();

    /**
     * CTE (Common Table Expression): Erweiterte Kategorie-Statistiken
     */
    @Query(value = """
        WITH category_stats AS (
            SELECT 
                category,
                COUNT(*) as product_count,
                AVG(price) as avg_price,
                SUM(price * stock_quantity) as total_value,
                MIN(price) as min_price,
                MAX(price) as max_price,
                SUM(stock_quantity) as total_stock
            FROM products 
            WHERE active = true 
            GROUP BY category
        ),
        category_rankings AS (
            SELECT 
                *,
                ROW_NUMBER() OVER (ORDER BY total_value DESC) as value_rank,
                ROW_NUMBER() OVER (ORDER BY product_count DESC) as count_rank
            FROM category_stats
        )
        SELECT category, product_count, avg_price, total_value, min_price, max_price, total_stock, value_rank, count_rank
        FROM category_rankings 
        ORDER BY value_rank
        """, nativeQuery = true)
    List<Object[]> getAdvancedCategoryStatistics();

    /**
     * Subquery: Produkte über Kategorie-Durchschnitt
     */
    @Query("""
        SELECT p FROM Product p 
        WHERE p.price > (
            SELECT AVG(pr.price) 
            FROM Product pr 
            WHERE pr.category = p.category AND pr.active = true
        ) AND p.active = true
        ORDER BY p.category, p.price DESC
        """)
    List<Product> findProductsAboveCategoryAverage();

    /**
     * Aggregation mit HAVING: Kategorien mit mindestens X Produkten
     */
    @Query(value = """
        SELECT 
            p.category as category,
            COUNT(p.id) as productCount,
            AVG(p.price) as averagePrice
        FROM products p 
        WHERE p.active = true
        GROUP BY p.category 
        HAVING COUNT(p.id) >= :minCount
        ORDER BY COUNT(p.id) DESC
        """, nativeQuery = true)
    List<Object[]> getCategoryStatisticsNative(@Param("minCount") Long minCount);

    /**
     * Zeitbasierte Analyse: Produkte nach Erstellungsdatum
     */
    @Query("""
        SELECT p FROM Product p 
        WHERE p.createdAt BETWEEN :startDate AND :endDate 
        AND p.active = true
        ORDER BY p.createdAt DESC
        """)
    List<Product> findProductsCreatedBetween(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Full-Text Search mit Relevanz-Ranking
     */
    @Query("""
        SELECT p FROM Product p 
        WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) 
        OR LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
        AND p.active = true
        ORDER BY 
            CASE WHEN LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) THEN 1 ELSE 2 END,
            p.name
        """)
    Page<Product> searchProducts(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Index-optimierte Abfrage für Performance
     */
    @Query("""
        SELECT p FROM Product p 
        WHERE p.category = :category 
        AND p.price BETWEEN :minPrice AND :maxPrice 
        AND p.stockQuantity > 0 
        AND p.active = true
        ORDER BY p.price
        """)
    List<Product> findByCategoryAndPriceRange(
        @Param("category") String category,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice
    );

    /**
     * Batch Update für Stock-Anpassungen
     */
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity WHERE p.id = :productId")
    int reduceStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * Inventory Value Analysis
     */
    @Query(value = """
        SELECT 
            category,
            COUNT(*) as product_count,
            SUM(stock_quantity) as total_stock,
            SUM(price * stock_quantity) as inventory_value,
            AVG(price * stock_quantity) as avg_product_value
        FROM products 
        WHERE active = true
        GROUP BY category
        ORDER BY inventory_value DESC
        """, nativeQuery = true)
    List<Object[]> getInventoryAnalysis();

    /**
     * Price Distribution Analysis
     */
    @Query(value = """
        SELECT 
            CASE 
                WHEN price < 50 THEN 'Budget (< 50€)'
                WHEN price < 200 THEN 'Mid-Range (50-200€)'
                WHEN price < 500 THEN 'Premium (200-500€)'
                ELSE 'Luxury (> 500€)'
            END as price_category,
            COUNT(*) as product_count,
            AVG(price) as avg_price
        FROM products 
        WHERE active = true
        GROUP BY 
            CASE 
                WHEN price < 50 THEN 'Budget (< 50€)'
                WHEN price < 200 THEN 'Mid-Range (50-200€)'
                WHEN price < 500 THEN 'Premium (200-500€)'
                ELSE 'Luxury (> 500€)'
            END
        ORDER BY avg_price
        """, nativeQuery = true)
    List<Object[]> getPriceDistributionAnalysis();
}