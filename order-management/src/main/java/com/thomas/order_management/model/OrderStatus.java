package com.thomas.order_management.model;

public enum OrderStatus {
    PENDING("Ausstehend"),
    CONFIRMED("Bestätigt"),
    PROCESSING("In Bearbeitung"),
    SHIPPED("Versandt"),
    DELIVERED("Geliefert"),
    CANCELLED("Storniert");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}