package com.thomas.order_management.model;

/**
 * Absagegründe für Auftragspositionen (SAP: Absagegrund).
 */
public enum RejectionReason {
    R01("Lieferung zu spät"),
    R02("Qualität mangelhaft"),
    R03("Zu teuer"),
    R04("Wettbewerber besser"),
    R05("Material nicht mehr lieferbar"),
    R10("Kundenwunsch");

    private final String description;

    RejectionReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
