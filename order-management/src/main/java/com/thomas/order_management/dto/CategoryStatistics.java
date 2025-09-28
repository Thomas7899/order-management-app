package com.thomas.order_management.dto;

import java.math.BigDecimal;

/**
 * DTO für Kategorie-Statistiken mit komplexen SQL-Aggregationen
 */
public class CategoryStatistics {
    private String category;
    private Long productCount;
    private BigDecimal averagePrice;
    private BigDecimal totalValue;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Long totalStock;

    // Constructor für JPQL
    public CategoryStatistics(String category, Long productCount, BigDecimal averagePrice) {
        this.category = category;
        this.productCount = productCount;
        this.averagePrice = averagePrice;
    }

    // Constructor für komplexe Abfragen
    public CategoryStatistics(String category, Long productCount, BigDecimal averagePrice, 
                            BigDecimal totalValue, BigDecimal minPrice, BigDecimal maxPrice, Long totalStock) {
        this.category = category;
        this.productCount = productCount;
        this.averagePrice = averagePrice;
        this.totalValue = totalValue;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.totalStock = totalStock;
    }

    // Getters and Setters
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Long getProductCount() { return productCount; }
    public void setProductCount(Long productCount) { this.productCount = productCount; }

    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }

    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }

    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

    public Long getTotalStock() { return totalStock; }
    public void setTotalStock(Long totalStock) { this.totalStock = totalStock; }

    @Override
    public String toString() {
        return "CategoryStatistics{" +
                "category='" + category + '\'' +
                ", productCount=" + productCount +
                ", averagePrice=" + averagePrice +
                ", totalValue=" + totalValue +
                ", minPrice=" + minPrice +
                ", maxPrice=" + maxPrice +
                ", totalStock=" + totalStock +
                '}';
    }
}