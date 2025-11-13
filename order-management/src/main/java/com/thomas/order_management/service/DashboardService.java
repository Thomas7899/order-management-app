package com.thomas.order_management.service;

import com.thomas.order_management.dto.DashboardDataDto;
import com.thomas.order_management.mapper.OrderMapper;
import com.thomas.order_management.mapper.ProductMapper;
import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.OrderRepository;
import com.thomas.order_management.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;

    public DashboardService(CustomerRepository customerRepository,
                            ProductRepository productRepository,
                            OrderRepository orderRepository,
                            OrderMapper orderMapper,
                            ProductMapper productMapper) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
    }

    public DashboardDataDto getCombinedDashboardData() {
        DashboardDataDto.Stats stats = getDashboardStats();
        DashboardDataDto.RecentActivity activity = getRecentActivity();
        return new DashboardDataDto(stats, activity);
    }

    public DashboardDataDto.Stats getDashboardStats() {
        long totalCustomers = customerRepository.count();
        long totalProducts = productRepository.countActiveProducts();
        long totalOrders = orderRepository.count();

        Map<String, Long> ordersByStatus = Arrays.stream(OrderStatus.values())
                .collect(Collectors.toMap(
                        OrderStatus::name,
                        orderRepository::countByStatus
                ));

        BigDecimal totalRevenue = orderRepository.getTotalAmountByStatus(OrderStatus.DELIVERED);
        BigDecimal pendingRevenue = orderRepository.getTotalAmountByStatus(OrderStatus.PENDING);

        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        BigDecimal todayRevenue = orderRepository.getTotalRevenueInPeriod(startOfDay, endOfDay);

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        BigDecimal monthRevenue = orderRepository.getTotalRevenueInPeriod(startOfMonth, LocalDateTime.now());

        long lowStockCount = productRepository.countLowStockProducts();

        return new DashboardDataDto.Stats(
                totalCustomers,
                totalProducts,
                totalOrders,
                ordersByStatus,
                totalRevenue != null ? totalRevenue : BigDecimal.ZERO,
                pendingRevenue != null ? pendingRevenue : BigDecimal.ZERO,
                todayRevenue != null ? todayRevenue : BigDecimal.ZERO,
                monthRevenue != null ? monthRevenue : BigDecimal.ZERO,
                lowStockCount
        );
    }

    public DashboardDataDto.RecentActivity getRecentActivity() {
        var recentOrders = orderRepository.findAllOrderByOrderDateDesc().stream()
                .limit(10)
                .toList();
        var lowStockProducts = productRepository.findLowStockProducts();

        return new DashboardDataDto.RecentActivity(
                orderMapper.toDtoList(recentOrders),
                productMapper.toDtoList(lowStockProducts)
        );
    }
}