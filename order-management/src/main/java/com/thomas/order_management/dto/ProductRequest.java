package com.thomas.order_management.dto;

import com.thomas.order_management.model.TaxCode;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Anlage/Änderung eines Materials. Der Bestand ist bewusst nicht enthalten: er wird nur über
 * Materialbelege fortgeschrieben.
 */
public record ProductRequest(
        @NotBlank(message = "Bezeichnung ist erforderlich")
        @Size(max = 255, message = "Bezeichnung darf höchstens 255 Zeichen lang sein") String name,
        @Size(max = 1000) String description,
        @NotNull(message = "Verkaufspreis ist erforderlich")
        @DecimalMin(value = "0.01", message = "Verkaufspreis muss größer 0 sein")
        @Digits(integer = 8, fraction = 2) BigDecimal price,
        @DecimalMin(value = "0.00", message = "Einstandspreis darf nicht negativ sein")
        @Digits(integer = 8, fraction = 2) BigDecimal costPrice,
        @Size(max = 100) String category,
        @Pattern(regexp = "^[A-Z]{1,3}$", message = "Mengeneinheit: 1–3 Großbuchstaben, z. B. ST") String unitOfMeasure,
        TaxCode taxCode,
        @Size(max = 500) String imageUrl,
        Boolean active
) {
}
