// order-management/src/main/java/com/thomas/order_management/dto/CategoryTrendReportDTO.java
package com.thomas.order_management.dto;

import java.time.LocalDate;
import java.util.List;

public class CategoryTrendReportDTO {
    public String category;
    public String summary;
    public List<String> positiveTrends;
    public List<String> negativeTrends;
    public List<String> neutralObservations;
    public double avgRating;
    public long reviewCount;
    public LocalDate windowStart;
    public LocalDate windowEnd;
}
