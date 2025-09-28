package com.thomas.order_management.controller;

import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.OrderRepository;
import com.thomas.order_management.repository.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public DashboardController(CustomerRepository customerRepository, 
                             ProductRepository productRepository, 
                             OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/stats")
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Grundlegende Statistiken
        stats.put("totalCustomers", customerRepository.count());
        stats.put("totalProducts", productRepository.countActiveProducts());
        stats.put("totalOrders", orderRepository.count());
        
        // Bestellungsstatistiken nach Status
        Map<String, Long> ordersByStatus = new HashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            ordersByStatus.put(status.name(), orderRepository.countByStatus(status));
        }
        stats.put("ordersByStatus", ordersByStatus);
        
        // Umsatzstatistiken
        BigDecimal totalRevenue = orderRepository.getTotalAmountByStatus(OrderStatus.DELIVERED);
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
        
        BigDecimal pendingRevenue = orderRepository.getTotalAmountByStatus(OrderStatus.PENDING);
        stats.put("pendingRevenue", pendingRevenue != null ? pendingRevenue : BigDecimal.ZERO);
        
        // Heutiger Umsatz
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        BigDecimal todayRevenue = orderRepository.getTotalRevenueInPeriod(startOfDay, endOfDay);
        stats.put("todayRevenue", todayRevenue != null ? todayRevenue : BigDecimal.ZERO);
        
        // Monatsstatistiken
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime now = LocalDateTime.now();
        BigDecimal monthRevenue = orderRepository.getTotalRevenueInPeriod(startOfMonth, now);
        stats.put("monthRevenue", monthRevenue != null ? monthRevenue : BigDecimal.ZERO);
        
        // Produkte mit niedrigem Lagerbestand
        stats.put("lowStockProductsCount", productRepository.findLowStockProducts().size());
        
        return stats;
    }

    @GetMapping("/recent-activity")
    public Map<String, Object> getRecentActivity() {
        Map<String, Object> activity = new HashMap<>();
        
        // Letzte 10 Bestellungen
        activity.put("recentOrders", orderRepository.findAllOrderByOrderDateDesc().stream().limit(10).toList());
        
        // Produkte mit niedrigem Lagerbestand
        activity.put("lowStockProducts", productRepository.findLowStockProducts());
        
        return activity;
    }
}