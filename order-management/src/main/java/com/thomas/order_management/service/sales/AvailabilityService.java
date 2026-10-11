package com.thomas.order_management.service.sales;

import com.thomas.order_management.dto.sales.AvailabilityDto;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.WarehouseStock;
import com.thomas.order_management.repository.DeliveryRepository;
import com.thomas.order_management.repository.OrderItemRepository;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.WarehouseStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Verfügbarkeitsprüfung auf Werksebene (alle Lagerorte summiert, wie die SAP-Standard-ATP):
 * <pre>
 * verfügbar = Bestand − für Aufträge bestätigte Menge − Lieferungen ohne Warenausgang
 * </pre>
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final WarehouseStockRepository stockRepository;
    private final OrderItemRepository orderItemRepository;
    private final DeliveryRepository deliveryRepository;
    private final ProductRepository productRepository;

    public AvailabilityService(WarehouseStockRepository stockRepository,
                               OrderItemRepository orderItemRepository,
                               DeliveryRepository deliveryRepository,
                               ProductRepository productRepository) {
        this.stockRepository = stockRepository;
        this.orderItemRepository = orderItemRepository;
        this.deliveryRepository = deliveryRepository;
        this.productRepository = productRepository;
    }

    /** Verfügbare Menge; die Reservierung der Position {@code excludeItemId} wird nicht mitgezählt. */
    public int availableQuantity(Long productId, Long excludeItemId) {
        long stock = nullSafe(stockRepository.getTotalStockByProduct(productId));
        long reservedOrders = orderItemRepository.sumOpenConfirmedQuantity(productId, excludeItemId);
        long reservedDeliveries = deliveryRepository.sumOpenDeliveryQuantity(productId);
        return (int) Math.max(0, stock - reservedOrders - reservedDeliveries);
    }

    public AvailabilityDto getAvailability(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Material " + productId + " nicht gefunden"));

        List<WarehouseStock> stocks = stockRepository.findByProductId(productId);
        long stock = stocks.stream().mapToLong(WarehouseStock::getQuantity).sum();
        long reservedOrders = orderItemRepository.sumOpenConfirmedQuantity(productId, null);
        long reservedDeliveries = deliveryRepository.sumOpenDeliveryQuantity(productId);

        List<AvailabilityDto.StorageLocationStock> locations = stocks.stream()
                .sorted(Comparator.comparing(s -> s.getWarehouse().getCode()))
                .map(s -> new AvailabilityDto.StorageLocationStock(
                        s.getWarehouse().getId(),
                        s.getWarehouse().getCode(),
                        s.getWarehouse().getName(),
                        s.getQuantity(),
                        (int) deliveryRepository.sumOpenDeliveryQuantity(productId, s.getWarehouse().getId()),
                        s.getBinLocation()))
                .toList();

        return new AvailabilityDto(
                product.getId(),
                product.getMaterialNumber(),
                product.getName(),
                product.getUnitOfMeasure(),
                (int) stock,
                (int) reservedOrders,
                (int) reservedDeliveries,
                (int) Math.max(0, stock - reservedOrders - reservedDeliveries),
                locations);
    }

    private static long nullSafe(Integer value) {
        return value != null ? value : 0L;
    }
}
