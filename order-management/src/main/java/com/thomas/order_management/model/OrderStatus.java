// order-management/src/main/java/com/thomas/order_management/model/OrderStatus.java
package com.thomas.order_management.model;

/**
 * Gesamtstatus des Kundenauftrags. Wird aus Liefer- und Fakturastatus der Positionen
 * abgeleitet und nicht frei gesetzt.
 */
public enum OrderStatus {
    OPEN("Offen"),
    IN_PROCESS("In Bearbeitung"),
    COMPLETED("Erledigt"),
    CANCELLED("Storniert");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
