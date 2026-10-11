package com.thomas.order_management.model;

public enum ReviewEmotion {
    JOY("Begeisterung"),
    SATISFACTION("Zufriedenheit"),
    SURPRISE("Überraschung"),
    NEUTRAL("Sachlich"),
    DISAPPOINTMENT("Enttäuschung"),
    FRUSTRATION("Frust"),
    ANGER("Ärger");

    private final String displayName;

    ReviewEmotion(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
