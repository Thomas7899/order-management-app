package com.thomas.order_management.dto.sales;

import java.util.List;

public record AvailabilityDto(
        Long productId,
        String materialNumber,
        String productName,
        String unitOfMeasure,
        int stock,
        int reservedForOrders,
        int reservedForDeliveries,
        int available,
        List<StorageLocationStock> storageLocations
) {
    public record StorageLocationStock(
            Long warehouseId,
            String code,
            String name,
            int stock,
            int openDeliveryQuantity,
            String binLocation
    ) {
    }
}
