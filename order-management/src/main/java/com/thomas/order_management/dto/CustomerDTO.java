// order-management/src/main/java/com/thomas/order_management/dto/CustomerDTO.java
package com.thomas.order_management.dto;

import com.thomas.order_management.model.PaymentTerms;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CustomerDTO {
    private Long id;
    private String customerNumber;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String zipCode;
    private String country;
    private PaymentTerms paymentTerms;
    private BigDecimal creditLimit;
    private BigDecimal discountPercent;
    private Boolean active;
    private LocalDateTime createdAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getPaymentTermsText() {
        return paymentTerms != null ? paymentTerms.getDescription() : null;
    }
}
