package com.thomas.order_management.dto.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Anfrageobjekte für Lieferung, Warenausgang, Faktura und Zahlungseingang.
 */
public final class LogisticsRequests {

    private LogisticsRequests() {
    }

    public record CreateDelivery(
            @NotNull(message = "Kundenauftrag ist erforderlich") Long orderId,
            /** Lagerort; leer = Lagerort mit ausreichendem Bestand, bevorzugt der Standardlagerort. */
            Long warehouseId,
            LocalDate plannedGoodsIssueDate,
            /** Positionen und Mengen; leer = alle bestätigten offenen Mengen. */
            List<@Valid DeliveryItem> items
    ) {
    }

    public record DeliveryItem(
            @NotNull Integer orderItemNumber,
            @NotNull @Min(1) Integer quantity
    ) {
    }

    public record PostGoodsIssue(LocalDate postingDate) {
    }

    public record CreateInvoice(
            @NotNull(message = "Lieferung ist erforderlich") Long deliveryId,
            LocalDate billingDate
    ) {
    }

    public record Payment(
            LocalDate paymentDate,
            @DecimalMin(value = "0.01", message = "Betrag muss positiv sein") BigDecimal amount
    ) {
    }

    public record Cancel(
            LocalDate date,
            @Size(max = 255) String reason
    ) {
    }
}
