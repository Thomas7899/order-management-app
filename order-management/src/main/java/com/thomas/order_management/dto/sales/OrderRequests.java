package com.thomas.order_management.dto.sales;

import com.thomas.order_management.model.RejectionReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Anfrageobjekte für Kundenaufträge.
 */
public final class OrderRequests {

    private OrderRequests() {
    }

    public record Create(
            @NotNull(message = "Auftraggeber ist erforderlich") Long customerId,
            LocalDate requestedDeliveryDate,
            @Size(max = 35, message = "Bestellnummer des Kunden: maximal 35 Zeichen") String customerReference,
            @Size(max = 500) String shippingAddress,
            @Size(max = 500) String billingAddress,
            @Size(max = 2000) String notes,
            @NotEmpty(message = "Mindestens eine Position ist erforderlich") List<@Valid Item> items,
            /** Belegdatum; leer = heute. */
            LocalDate documentDate
    ) {
    }

    public record Item(
            @NotNull(message = "Material ist erforderlich") Long productId,
            @NotNull(message = "Menge ist erforderlich")
            @Min(value = 1, message = "Menge muss mindestens 1 sein")
            @Max(value = 9999, message = "Menge darf höchstens 9999 sein") Integer quantity,
            @DecimalMin(value = "0.0", message = "Rabatt darf nicht negativ sein")
            @DecimalMax(value = "100.0", message = "Rabatt darf höchstens 100 % sein") BigDecimal discountPercent
    ) {
    }

    public record UpdateHeader(
            LocalDate requestedDeliveryDate,
            @Size(max = 35) String customerReference,
            @Size(max = 500) String shippingAddress,
            @Size(max = 500) String billingAddress,
            @Size(max = 2000) String notes
    ) {
    }

    public record ChangeItem(
            @NotNull @Min(1) @Max(9999) Integer quantity,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal discountPercent
    ) {
    }

    public record Reject(
            @NotNull(message = "Absagegrund ist erforderlich") RejectionReason reason,
            /** Positionsnummern; leer = alle offenen Positionen. */
            List<Integer> itemNumbers
    ) {
    }
}
