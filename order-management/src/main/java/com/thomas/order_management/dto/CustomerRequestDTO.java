package com.thomas.order_management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data 
public class CustomerRequestDTO {

    @NotBlank(message = "Vorname darf nicht leer sein")
    @Size(min = 2, message = "Vorname muss mindestens 2 Zeichen lang sein")
    private String firstName;

    @NotBlank(message = "Nachname darf nicht leer sein")
    private String lastName;

    @NotBlank(message = "E-Mail darf nicht leer sein")
    @Email(message = "Gültige E-Mail-Adresse erforderlich")
    private String email;

    private String phone;
    private String address;
    private String city;
    private String zipCode;
    private String country;
}