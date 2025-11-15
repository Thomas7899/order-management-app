// order-management/src/main/java/com/thomas/order_management/dto/ProductTrendReportDTO.java
package com.thomas.order_management.dto;

import java.time.LocalDate;
import java.util.List;

public class ProductTrendReportDTO {
    public Long productId;
    public String productName;
    public String summary;
    public List<String> positiveTrends;
    public List<String> negativeTrends;
    public List<String> neutralObservations;
    public double avgRating;
    public long reviewCount;
    public LocalDate windowStart;
    public LocalDate windowEnd;
}
