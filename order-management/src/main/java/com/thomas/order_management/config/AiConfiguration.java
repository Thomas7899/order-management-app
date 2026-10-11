// order-management/src/main/java/com/thomas/order_management/config/AiConfiguration.java
package com.thomas.order_management.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
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
        Du unterstützt Fachanwender in einem ERP-System für die Auftragsabwicklung
        (Vertrieb, Logistik, Fakturierung) eines Handelsunternehmens.

        Regeln:
        1. Antworte auf Deutsch, sachlich und knapp.
        2. Verwende ausschließlich die Fakten, Kennzahlen und Zitate aus der Anfrage.
           Erfinde keine Zahlen, Produkte, Ursachen oder Ereignisse.
        3. Wenn die Daten eine Aussage nicht stützen, schreibe das, statt zu spekulieren.
        4. Halte dich exakt an das verlangte Ausgabeformat.
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
     *   <li>Strukturierte Ausgaben über das Ausgabeformat des Aufrufers</li>
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
     * Sobald ein eigenes ObjectMapper-Bean existiert, legt Spring Boot keinen eigenen mehr an.
     * Ohne dieses Bean würde die REST-API die spring.jackson.*-Einstellungen verlieren
     * (z. B. Datumswerte als Arrays statt als ISO-Strings).
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
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
