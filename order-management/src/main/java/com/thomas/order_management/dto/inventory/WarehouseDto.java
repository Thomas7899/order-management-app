// order-management/src/main/java/com/thomas/order_management/dto/inventory/WarehouseDto.java
package com.thomas.order_management.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String address;
    private String city;
    private String zipCode;
    private String country;
    private Boolean active;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    
    // Aggregierte Werte
    private Long productCount;
    private Long totalStock;
    private BigDecimal inventoryValue;
}
