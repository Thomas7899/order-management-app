package com.thomas.order_management.model;

import java.math.BigDecimal;

/**
 * Zahlungsbedingungen (SAP: Zahlungsbedingungsschlüssel).
 */
public enum PaymentTerms {
    ZB01("14 Tage 2 % Skonto, 30 Tage netto", 30, 14, new BigDecimal("2.00")),
    ZB30("30 Tage netto", 30, 0, BigDecimal.ZERO),
    ZB14("14 Tage netto", 14, 0, BigDecimal.ZERO),
    ZB00("Sofort fällig ohne Abzug", 0, 0, BigDecimal.ZERO);

    private final String description;
    private final int netDays;
    private final int cashDiscountDays;
    private final BigDecimal cashDiscountPercent;

    PaymentTerms(String description, int netDays, int cashDiscountDays, BigDecimal cashDiscountPercent) {
        this.description = description;
        this.netDays = netDays;
        this.cashDiscountDays = cashDiscountDays;
        this.cashDiscountPercent = cashDiscountPercent;
    }

    public String getDescription() {
        return description;
    }

    public int getNetDays() {
        return netDays;
    }

    public int getCashDiscountDays() {
        return cashDiscountDays;
    }

    public BigDecimal getCashDiscountPercent() {
        return cashDiscountPercent;
    }
}
