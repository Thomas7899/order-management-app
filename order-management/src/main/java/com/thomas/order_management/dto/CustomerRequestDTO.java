package com.thomas.order_management.dto;

import com.thomas.order_management.model.PaymentTerms;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerRequestDTO {

    @NotBlank(message = "Vorname darf nicht leer sein")
    @Size(min = 2, max = 100, message = "Vorname muss 2 bis 100 Zeichen lang sein")
    private String firstName;

    @NotBlank(message = "Nachname darf nicht leer sein")
    @Size(max = 100, message = "Nachname darf höchstens 100 Zeichen lang sein")
    private String lastName;

    @NotBlank(message = "E-Mail darf nicht leer sein")
    @Email(message = "Gültige E-Mail-Adresse erforderlich")
    private String email;

    @Size(max = 50)
    private String phone;

    @Size(max = 500)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 20)
    private String zipCode;

    @Size(max = 100)
    private String country;

    private PaymentTerms paymentTerms;

    @DecimalMin(value = "0.00", message = "Kreditlimit darf nicht negativ sein")
    private BigDecimal creditLimit;

    @DecimalMin(value = "0.00", message = "Rabatt darf nicht negativ sein")
    @DecimalMax(value = "50.00", message = "Kundenrabatt darf höchstens 50 % betragen")
    private BigDecimal discountPercent;

    private Boolean active;
}
