package com.thomas.order_management.model;

public enum ReviewSentiment {
    POSITIVE("Positiv"),
    NEUTRAL("Neutral"),
    NEGATIVE("Negativ");

    private final String displayName;

    ReviewSentiment(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
