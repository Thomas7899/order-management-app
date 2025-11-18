// order-management/src/main/java/com/thomas/order_management/dto/ProductAnomalyDTO.java
package com.thomas.order_management.dto;

import java.util.List;

public record ProductAnomalyDTO(
    Long productId,
    String productName,
    String reason, 
    double avgRating,
    long reviewCount,
    List<String> negativeKeywords
) {}