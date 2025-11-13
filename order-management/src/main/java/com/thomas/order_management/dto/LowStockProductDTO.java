//src/main/java/com/thomas/order_management/dto/LowStockProductDTO.java
package com.thomas.order_management.dto;

import com.thomas.order_management.model.Product;
import lombok.Data;

/**
 * DTO für ein "Low Stock Product" im Dashboard.
 */
@Data
public class LowStockProductDTO {

    private Long id;
    private String name;
    private String imageUrl;
    private int stockQuantity;

    // Konstruktor, der die Entity in ein DTO umwandelt
    public LowStockProductDTO(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.imageUrl = product.getImageUrl();
        this.stockQuantity = product.getStockQuantity();
    }
}