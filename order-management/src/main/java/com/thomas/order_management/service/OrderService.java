package com.thomas.order_management.service;

import com.thomas.order_management.dto.OrderDto;
import com.thomas.order_management.mapper.OrderMapper;
import com.thomas.order_management.model.Order;
import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    public List<OrderDto> getAllOrders() {
        return orderMapper.toDtoList(orderRepository.findAllOrderByOrderDateDesc());
    }

    public Optional<OrderDto> getOrderById(Long id) {
        return orderRepository.findById(id).map(orderMapper::toDto);
    }

    public Optional<OrderDto> getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber).map(orderMapper::toDto);
    }

    @Transactional
    public OrderDto createOrder(Order order) {
        if (order.getOrderNumber() == null || order.getOrderNumber().isEmpty()) {
            order.setOrderNumber(generateOrderNumber());
        }
        if (order.getTotalAmount() == null) {
            order.setTotalAmount(order.calculateTotalAmount());
        }
        Order savedOrder = orderRepository.save(order);
        return orderMapper.toDto(savedOrder);
    }

    @Transactional
    public Optional<OrderDto> updateOrder(Long id, Order orderDetails) {
        return orderRepository.findById(id).map(order -> {
            order.setStatus(orderDetails.getStatus());
            order.setNotes(orderDetails.getNotes());
            order.setShippingAddress(orderDetails.getShippingAddress());
            order.setBillingAddress(orderDetails.getBillingAddress());
            order.setTotalAmount(order.calculateTotalAmount());
            Order updatedOrder = orderRepository.save(order);
            return orderMapper.toDto(updatedOrder);
        });
    }

    @Transactional
    public Optional<OrderDto> updateOrderStatus(Long id, OrderStatus status) {
        return orderRepository.findById(id).map(order -> {
            order.setStatus(status);
            Order updatedOrder = orderRepository.save(order);
            return orderMapper.toDto(updatedOrder);
        });
    }

    @Transactional
    public boolean deleteOrder(Long id) {
        if (orderRepository.existsById(id)) {
            orderRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public List<OrderDto> getOrdersByCustomer(Long customerId) {
        return orderMapper.toDtoList(orderRepository.findByCustomerIdOrderByOrderDateDesc(customerId));
    }

    public List<OrderDto> getOrdersByStatus(OrderStatus status) {
        return orderMapper.toDtoList(orderRepository.findByStatus(status));
    }

    public List<OrderDto> getOrdersInPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        return orderMapper.toDtoList(orderRepository.findByOrderDateBetween(startDate, endDate));
    }

    public BigDecimal getRevenueByStatus(OrderStatus status) {
        BigDecimal revenue = orderRepository.getTotalAmountByStatus(status);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    public BigDecimal getRevenueInPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        BigDecimal revenue = orderRepository.getTotalRevenueInPeriod(startDate, endDate);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    public long getOrderCountByStatus(OrderStatus status) {
        return orderRepository.countByStatus(status);
    }

    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis();
    }
}