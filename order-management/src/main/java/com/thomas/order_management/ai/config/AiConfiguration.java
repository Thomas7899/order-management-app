// order-management/src/main/java/com/thomas/order_management/ai/config/AiConfiguration.java
package com.thomas.order_management.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Zentrale KI-Konfiguration für alle AI-Services.
 * 
 * <h2>Design-Entscheidungen:</h2>
 * <ul>
 *   <li>Zentrale ChatClient-Konfiguration mit System-Prompt</li>
 *   <li>Retry-Support für transiente API-Fehler</li>
 *   <li>Deterministische Prompts durch niedrige Temperature</li>
 *   <li>Shared ObjectMapper für konsistentes JSON-Handling</li>
 * </ul>
 * 
 * @author Thomas Osterlehner
 * @since 2.0
 */
@Configuration
@EnableRetry
public class AiConfiguration {

    /**
     * System-Prompt für konsistente KI-Antworten.
     * Definiert Rolle, Sprache und Ausgabeformat.
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
        Du bist ein präziser Datenanalyse-Assistent für ein E-Commerce-System.
        
        STRIKTE REGELN:
        1. Antworte IMMER auf Deutsch
        2. Antworte AUSSCHLIESSLICH mit validem JSON (keine Markdown-Codeblöcke)
        3. Nutze die exakten Feldnamen aus den Anfragen
        4. Sei präzise und faktenbasiert - keine Spekulationen
        5. Bei Unsicherheit: liefere "UNBEKANNT" statt falscher Daten
        6. Halte Zusammenfassungen kurz und actionable
        
        KONTEXT: E-Commerce Plattform mit Produktbewertungen, Bestandsmanagement und Kundenservice.
        """;

    @Value("${app.ai.temperature:0.3}")
    private double temperature;

    @Value("${app.ai.max-tokens:2000}")
    private int maxTokens;

    /**
     * Erstellt einen vorkonfigurierten ChatClient mit System-Prompt.
     * 
     * <p>Der ChatClient wird mit folgenden Eigenschaften konfiguriert:</p>
     * <ul>
     *   <li>Niedriger Temperature-Wert (0.3) für deterministische Ausgaben</li>
     *   <li>System-Prompt für konsistente Antworten</li>
     *   <li>JSON-Modus für strukturierte Ausgaben</li>
     * </ul>
     *
     * @param model Das OpenAI Chat Model (wird von Spring AI auto-konfiguriert)
     * @return Konfigurierter ChatClient
     */
    @Bean
    @Primary
    public ChatClient aiChatClient(OpenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build())
                .build();
    }

    /**
     * Shared ObjectMapper für alle AI-Services.
     * Verhindert mehrfache Instanziierung und gewährleistet konsistentes JSON-Handling.
     */
    @Bean("aiObjectMapper")
    public ObjectMapper aiObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules(); // Registriert Java 8 Date/Time Support
        return mapper;
    }
}
