package com.thomas.order_management.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CustomerDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String zipCode;
    private String country;
    private LocalDateTime createdAt;
    
    public String getFullName() {
        return firstName + " " + lastName;
    }
}