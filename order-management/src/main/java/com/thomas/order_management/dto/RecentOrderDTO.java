package com.thomas.order_management.dto;

import com.thomas.order_management.model.Order;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecentOrderDTO {
    
    private Long id;
    private String orderNumber;
    private String customerName; // Kombiniert Vor- und Nachname
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime orderDate;

    public RecentOrderDTO(Order order) {
        this.id = order.getId();
        this.orderNumber = order.getOrderNumber();
        this.totalAmount = order.getTotalAmount();
        this.status = order.getStatus().name();
        this.orderDate = order.getOrderDate();
        
        // Sicherer Zugriff auf den Kunden (verhindert NullPointerException)
        if (order.getCustomer() != null) {
            this.customerName = order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName();
        } else {
            this.customerName = "N/A";
        }
    }
}
