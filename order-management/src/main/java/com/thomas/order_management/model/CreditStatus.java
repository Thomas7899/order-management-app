package com.thomas.order_management.model;

/**
 * Ergebnis der Kreditprüfung beim Sichern des Auftrags.
 */
public enum CreditStatus {
    NOT_CHECKED("Nicht geprüft"),
    APPROVED("Freigegeben"),
    BLOCKED("Kreditsperre"),
    RELEASED("Manuell freigegeben");

    private final String displayName;

    CreditStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean allowsDelivery() {
        return this != BLOCKED;
    }
}
