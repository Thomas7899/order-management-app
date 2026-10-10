package com.thomas.order_management.model;

/**
 * Fachliche Rollen, angelehnt an typische SAP-Sammelrollen.
 */
public enum Role {
    SALES("Vertrieb", "Kundenaufträge und Debitoren pflegen"),
    WAREHOUSE("Lager & Versand", "Lieferungen, Warenausgang und Bestandsbuchungen"),
    BILLING("Fakturierung & Debitorenbuchhaltung", "Fakturen, Zahlungseingänge und Kreditfreigaben"),
    CONTROLLING("Controlling", "Auswertungen und KI-Analysen"),
    ADMIN("Administrator", "Alle Funktionen und Benutzerverwaltung");

    private final String displayName;
    private final String description;

    Role(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
