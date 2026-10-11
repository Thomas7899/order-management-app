package com.thomas.order_management.service.sales;

import com.thomas.order_management.dto.sales.CreditExposureDto;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.InvoiceRepository;
import com.thomas.order_management.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;

/**
 * Einfache Kreditprüfung (angelehnt an SAP FSCM Credit Management):
 * Obligo = offener Auftragswert (noch nicht fakturiert, brutto) + offene Posten.
 */
@Service
@Transactional(readOnly = true)
public class CreditCheckService {

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;

    public CreditCheckService(OrderRepository orderRepository, InvoiceRepository invoiceRepository) {
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
    }

    public CreditExposureDto exposure(Customer customer) {
        return exposure(customer, null);
    }

    /** Obligo ohne den Auftrag {@code excludeOrderId} (für die Prüfung des gerade bearbeiteten Auftrags). */
    public CreditExposureDto exposure(Customer customer, Long excludeOrderId) {
        BigDecimal openOrders = orderRepository
                .findByCustomerIdAndStatusIn(customer.getId(), EnumSet.of(OrderStatus.OPEN, OrderStatus.IN_PROCESS))
                .stream()
                .filter(o -> excludeOrderId == null || !o.getId().equals(excludeOrderId))
                .map(CreditCheckService::unbilledGrossValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openItems = invoiceRepository.findOpenItemsByCustomer(customer.getId()).stream()
                .map(Invoice::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = openOrders.add(openItems);
        BigDecimal limit = customer.getCreditLimit();
        BigDecimal available = limit != null ? limit.subtract(total) : null;
        BigDecimal utilization = limit != null && limit.signum() > 0
                ? total.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP)
                : null;

        return new CreditExposureDto(customer.getId(), customer.getCustomerNumber(), limit,
                openOrders, openItems, total, available, utilization);
    }

    /** Ergebnis der Kreditprüfung für einen neuen bzw. geänderten Auftrag. */
    public CreditStatus check(Customer customer, Long orderId, BigDecimal orderGrossValue) {
        if (customer.getCreditLimit() == null) {
            return CreditStatus.APPROVED;
        }
        BigDecimal exposure = exposure(customer, orderId).totalExposure().add(orderGrossValue);
        return exposure.compareTo(customer.getCreditLimit()) > 0 ? CreditStatus.BLOCKED : CreditStatus.APPROVED;
    }

    /** Bruttowert der noch nicht fakturierten Mengen eines Auftrags. */
    static BigDecimal unbilledGrossValue(Order order) {
        BigDecimal value = BigDecimal.ZERO;
        for (OrderItem item : order.getOrderItems()) {
            if (item.isRejected() || item.getQuantity() == 0) continue;
            int unbilled = Math.max(0, item.getQuantity() - item.getBilledQuantity());
            if (unbilled == 0) continue;
            value = value.add(item.getGrossAmount()
                    .multiply(BigDecimal.valueOf(unbilled))
                    .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP));
        }
        return value;
    }
}
