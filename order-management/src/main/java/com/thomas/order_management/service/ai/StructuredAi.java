package com.thomas.order_management.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Ruft das Sprachmodell mit strukturierter Ausgabe auf (JSON-Schema aus dem Zieltyp statt Textparsing).
 * Gleiche Anfragen werden aus einem kleinen Cache beantwortet. Ist keine KI verfügbar oder schlägt der
 * Aufruf fehl, kommt ein leeres Ergebnis zurück und der Aufrufer nutzt seine regelbasierte Variante.
 */
@Slf4j
@Component
public class StructuredAi {

    private static final int CACHE_SIZE = 200;

    private final ChatClient chatClient;
    private final AiAvailability availability;
    private final Map<String, Object> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    public StructuredAi(ChatClient chatClient, AiAvailability availability) {
        this.chatClient = chatClient;
        this.availability = availability;
    }

    public boolean isAvailable() {
        return availability.isAvailable();
    }

    public <T> Optional<T> call(String prompt, Class<T> type) {
        return call(prompt, type, true);
    }

    public <T> Optional<T> call(String prompt, Class<T> type, boolean useCache) {
        if (!availability.isAvailable()) {
            return Optional.empty();
        }
        String key = type.getName() + ":" + sha256(prompt);
        if (useCache) {
            Object cached = cache.get(key);
            if (type.isInstance(cached)) {
                return Optional.of(type.cast(cached));
            }
        }
        try {
            T result = chatClient.prompt().user(prompt).call().entity(type);
            if (result != null && useCache) {
                cache.put(key, result);
            }
            return Optional.ofNullable(result);
        } catch (RuntimeException ex) {
            log.warn("KI-Aufruf für {} fehlgeschlagen, regelbasierte Variante wird genutzt: {}", type.getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
