package com.thomas.order_management.model;

import java.math.BigDecimal;

/**
 * Steuerklassifikation des Materials für die MWST-Kondition.
 */
public enum TaxCode {
    FULL("Voller Steuersatz", new BigDecimal("19.00")),
    REDUCED("Ermäßigter Steuersatz", new BigDecimal("7.00")),
    EXEMPT("Steuerfrei", BigDecimal.ZERO);

    private final String description;
    private final BigDecimal rate;

    TaxCode(String description, BigDecimal rate) {
        this.description = description;
        this.rate = rate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getRate() {
        return rate;
    }
}
