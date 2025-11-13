package com.thomas.order_management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivityDTO {
    private List<OrderDto> recentOrders;
    private List<CustomerDTO> newCustomers;
}