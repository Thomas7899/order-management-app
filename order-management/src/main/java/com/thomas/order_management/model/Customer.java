// order-management/src/main/java/com/thomas/order_management/model/Customer.java
package com.thomas.order_management.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    /** Debitorennummer aus dem Nummernkreis CUSTOMER. */
    @Column(name = "customer_number", unique = true, length = 10)
    private String customerNumber;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_terms", length = 4)
    private PaymentTerms paymentTerms = PaymentTerms.ZB30;

    /** Kreditlimit in EUR; null bedeutet keine Kreditprüfung. */
    @Column(name = "credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    /** Kundenrabatt in Prozent (Konditionsart K007). */
    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean active = true;

    @Column
    private String phone;

    @Column
    private String address;

    @Column
    private String city;

    @Column
    private String zipCode;

    @Column
    private String country;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "customer", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH}, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Order> orders = new ArrayList<>();

    public Customer(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder(getFullName());
        if (address != null && !address.isBlank()) sb.append(", ").append(address);
        String cityLine = ((zipCode != null ? zipCode + " " : "") + (city != null ? city : "")).trim();
        if (!cityLine.isEmpty()) sb.append(", ").append(cityLine);
        if (country != null && !country.isBlank()) sb.append(", ").append(country);
        return sb.toString();
    }

    @PrePersist
    public void onPersist() {
        if (this.createdAt == null) {
            this.createdAt = this.updatedAt = LocalDateTime.now();
        } else if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
