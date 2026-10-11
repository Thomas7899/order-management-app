package com.thomas.order_management.model;

/**
 * Feste Themen, nach denen Bewertungen ausgewertet werden. Eine feste Liste macht Auswertungen
 * über Zeiträume vergleichbar (frei formulierte KI-Themen wären es nicht).
 */
public enum ReviewTheme {
    QUALITY("Qualität & Verarbeitung"),
    FUNCTION("Funktion & Leistung"),
    DURABILITY("Haltbarkeit & Defekte"),
    USABILITY("Bedienung & Montage"),
    DESIGN("Design & Optik"),
    PRICE("Preis-Leistung"),
    DELIVERY("Lieferung & Verpackung"),
    SERVICE("Kundenservice");

    private final String displayName;

    ReviewTheme(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
