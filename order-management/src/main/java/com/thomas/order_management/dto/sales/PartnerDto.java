package com.thomas.order_management.dto.sales;

/**
 * Kurzdarstellung eines Geschäftspartners in Belegen.
 */
public record PartnerDto(
        Long id,
        String customerNumber,
        String name,
        String email,
        String city,
        String country
) {
}
