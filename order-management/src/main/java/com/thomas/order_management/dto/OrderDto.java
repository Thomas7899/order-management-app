package com.thomas.order_management.dto;

import com.thomas.order_management.model.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDto {
    private Long id;
    private String orderNumber;
    private CustomerDTO customer;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String notes;
    private String shippingAddress;
    private String billingAddress;
    private List<OrderItemDto> orderItems;

    @Data
    public static class OrderItemDto {
        private ProductDto product;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}