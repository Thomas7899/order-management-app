package com.thomas.order_management.dto.sales;

/**
 * Schlüssel mit Kurztext, wie in SAP-Oberflächen üblich (z. B. "ZB01" / "14 Tage 2 % Skonto").
 */
public record CodeText(String code, String text) {

    public static CodeText of(String code, String text) {
        return code == null ? null : new CodeText(code, text);
    }
}
