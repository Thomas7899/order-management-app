package com.thomas.order_management.service.reviews;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Nach dem Start (und nach dem Erzeugen der Demo-Daten): fehlende Einordnungen regelbasiert ergänzen
 * und, falls KI verfügbar ist, die Einordnung per KI im Hintergrund nachziehen.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewInsightBootstrap {

    private final ReviewInsightService insightService;

    @Value("${app.ai.classify-on-start:true}")
    private boolean classifyOnStart;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            insightService.classifyMissingWithRules();
            if (classifyOnStart && insightService.startLlmClassification()) {
                log.info("KI-Einordnung der Bewertungen läuft im Hintergrund");
            }
        } catch (RuntimeException ex) {
            log.warn("Einordnung der Bewertungen beim Start fehlgeschlagen", ex);
        }
    }
}
