package com.thomas.order_management.model;

/**
 * Teilstatus eines Auftrags für Lieferung bzw. Faktura (SAP: A = nicht bearbeitet,
 * B = teilweise bearbeitet, C = vollständig bearbeitet).
 */
public enum ProcessingStatus {
    NOT_RELEVANT("Nicht relevant"),
    NOT_PROCESSED("Nicht bearbeitet"),
    PARTIALLY_PROCESSED("Teilweise bearbeitet"),
    FULLY_PROCESSED("Vollständig bearbeitet");

    private final String displayName;

    ProcessingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ProcessingStatus of(long done, long relevant) {
        if (relevant <= 0) {
            return NOT_RELEVANT;
        }
        if (done <= 0) {
            return NOT_PROCESSED;
        }
        return done >= relevant ? FULLY_PROCESSED : PARTIALLY_PROCESSED;
    }
}
