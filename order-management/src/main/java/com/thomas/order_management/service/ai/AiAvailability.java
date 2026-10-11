package com.thomas.order_management.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ob KI-Funktionen genutzt werden dürfen. Ohne gültigen Schlüssel (oder mit app.ai.enabled=false)
 * laufen alle Auswertungen regelbasiert.
 */
@Component
public class AiAvailability {

    private final boolean available;

    public AiAvailability(@Value("${app.ai.enabled:true}") boolean enabled,
                          @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.available = enabled && apiKey != null && !apiKey.isBlank()
                && !"not-configured".equals(apiKey) && !apiKey.contains("${");
    }

    public boolean isAvailable() {
        return available;
    }
}
