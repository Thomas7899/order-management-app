package com.thomas.order_management.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * DTO für Produkt-Rankings mit Window Functions
 */
public class ProductRanking {
    private Long id;
    private String name;
    private String category;
    private BigDecimal price;
    private Integer stockQuantity;
    private LocalDateTime createdAt;
    private Integer categoryRank;
    private Integer overallRank;
    private BigDecimal categoryAveragePrice;
    private BigDecimal priceRatio;

    public ProductRanking() {}

    public ProductRanking(Long id, String name, String category, BigDecimal price,
                          Integer stockQuantity, LocalDateTime createdAt,
                          Integer categoryRank, Integer overallRank,
                          BigDecimal categoryAveragePrice) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.createdAt = createdAt;
        this.categoryRank = categoryRank;
        this.overallRank = overallRank;
        this.categoryAveragePrice = categoryAveragePrice;

        // Berechne Preis-Verhältnis
        if (price != null && categoryAveragePrice != null
                && categoryAveragePrice.compareTo(BigDecimal.ZERO) > 0) {
            this.priceRatio = price.divide(categoryAveragePrice, 2, RoundingMode.HALF_UP);
        } else {
            this.priceRatio = null;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getCategoryRank() { return categoryRank; }
    public void setCategoryRank(Integer categoryRank) { this.categoryRank = categoryRank; }

    public Integer getOverallRank() { return overallRank; }
    public void setOverallRank(Integer overallRank) { this.overallRank = overallRank; }

    public BigDecimal getCategoryAveragePrice() { return categoryAveragePrice; }
    public void setCategoryAveragePrice(BigDecimal categoryAveragePrice) {
        this.categoryAveragePrice = categoryAveragePrice;
    }

    public BigDecimal getPriceRatio() { return priceRatio; }
    public void setPriceRatio(BigDecimal priceRatio) { this.priceRatio = priceRatio; }

    @Override
    public String toString() {
        return "ProductRanking{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", price=" + price +
                ", categoryRank=" + categoryRank +
                ", overallRank=" + overallRank +
                ", priceRatio=" + priceRatio +
                '}';
    }
}
