package com.thomas.order_management.model;

/**
 * Nummernkreisobjekte mit ihren Intervallen, angelehnt an die SAP-Standardvergabe.
 */
public enum NumberRangeObject {
    SALES_ORDER("Kundenauftrag", 10000001L, 19999999L),
    DELIVERY("Auslieferung", 80000001L, 89999999L),
    INVOICE("Faktura", 90000001L, 99999999L),
    MATERIAL_DOCUMENT("Materialbeleg", 4900000001L, 4999999999L),
    CUSTOMER("Debitor", 100001L, 199999L),
    MATERIAL("Material", 200001L, 299999L),
    SALES_ORDER_REQUEST("Auftragseingang", 70000001L, 79999999L);

    private final String description;
    private final long fromNumber;
    private final long toNumber;

    NumberRangeObject(String description, long fromNumber, long toNumber) {
        this.description = description;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
    }

    public String getDescription() {
        return description;
    }

    public long getFromNumber() {
        return fromNumber;
    }

    public long getToNumber() {
        return toNumber;
    }
}
