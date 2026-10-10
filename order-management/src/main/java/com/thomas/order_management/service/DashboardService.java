package com.thomas.order_management.service;

import com.thomas.order_management.dto.DashboardDataDto;
import com.thomas.order_management.mapper.SalesDocumentMapper;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DeliveryRepository deliveryRepository;
    private final InvoiceRepository invoiceRepository;
    private final WarehouseStockRepository stockRepository;
    private final InventoryService inventoryService;
    private final SalesDocumentMapper salesMapper;

    public DashboardService(CustomerRepository customerRepository,
                            ProductRepository productRepository,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            DeliveryRepository deliveryRepository,
                            InvoiceRepository invoiceRepository,
                            WarehouseStockRepository stockRepository,
                            InventoryService inventoryService,
                            SalesDocumentMapper salesMapper) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.deliveryRepository = deliveryRepository;
        this.invoiceRepository = invoiceRepository;
        this.stockRepository = stockRepository;
        this.inventoryService = inventoryService;
        this.salesMapper = salesMapper;
    }

    public DashboardDataDto.Stats getDashboardStats() {
        LocalDate today = LocalDate.now();

        Map<String, Long> ordersByStatus = Arrays.stream(OrderStatus.values())
                .collect(Collectors.toMap(OrderStatus::name, orderRepository::countByStatus));

        List<Invoice> openItems = invoiceRepository.findOpenItems();
        List<Invoice> overdue = openItems.stream().filter(i -> i.isOverdue(today)).toList();

        long creditBlocked = orderRepository.findByStatusIn(List.of(OrderStatus.OPEN, OrderStatus.IN_PROCESS)).stream()
                .filter(o -> o.getCreditStatus() == CreditStatus.BLOCKED)
                .count();

        return new DashboardDataDto.Stats(
                customerRepository.count(),
                productRepository.countActiveProducts(),
                orderRepository.count(),
                ordersByStatus,
                invoiceRepository.sumNetRevenueTotal(),
                openOrderValue(),
                invoiceRepository.sumNetRevenue(today, today),
                invoiceRepository.sumNetRevenue(today.withDayOfMonth(1), today),
                stockRepository.findLowStockItems().size(),
                deliveryRepository.countByStatus(Delivery.Status.CREATED),
                deliveryRepository.findBillingDueList(ProcessingStatus.NOT_PROCESSED).size(),
                openItems.size(),
                sum(openItems),
                overdue.size(),
                sum(overdue),
                creditBlocked,
                orderItemRepository.findBacklogItems().size(),
                orderRepository.countByOrderDateBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
        );
    }

    public DashboardDataDto.RecentActivity getRecentActivity() {
        return new DashboardDataDto.RecentActivity(
                orderRepository.findTop10ByOrderByOrderDateDesc().stream().map(salesMapper::toListItem).toList(),
                inventoryService.getLowStockItems().stream().limit(10).toList());
    }

    /** Auftragsbestand: Nettowert der noch nicht fakturierten Mengen offener Aufträge. */
    private BigDecimal openOrderValue() {
        BigDecimal value = BigDecimal.ZERO;
        for (OrderItem item : orderItemRepository.findItemsOfOpenOrders()) {
            int unbilled = Math.max(0, item.getQuantity() - item.getBilledQuantity());
            if (unbilled == 0 || item.getQuantity() == 0) continue;
            value = value.add(item.getNetAmount().multiply(BigDecimal.valueOf(unbilled))
                    .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP));
        }
        return value;
    }

    private static BigDecimal sum(List<Invoice> invoices) {
        return invoices.stream().map(Invoice::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
