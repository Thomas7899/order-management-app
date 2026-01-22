// order-management/src/main/java/com/thomas/order_management/ai/config/AiProperties.java
package com.thomas.order_management.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Zentrale Konfigurationsparameter für KI-Services.
 * 
 * <h2>Konfigurierbare Parameter:</h2>
 * <ul>
 *   <li>Embedding: Dimensionen, Batch-Größe, Top-K</li>
 *   <li>Analyse: Batch-Größe, Temperatur</li>
 *   <li>Retry: Max-Versuche, Backoff</li>
 *   <li>Rate-Limiting: Requests pro Minute</li>
 * </ul>
 * 
 * @author Thomas Osterlehner
 * @since 2.0
 */
@Configuration
@ConfigurationProperties(prefix = "app.ai")
@Validated
@Getter
@Setter
public class AiProperties {

    /**
     * Temperatur für LLM-Aufrufe (0.0 = deterministisch, 1.0 = kreativ).
     * Empfohlen: 0.3 für Analyse-Tasks
     */
    @Min(0)
    @Max(1)
    private double temperature = 0.3;

    /**
     * Maximale Tokens pro LLM-Antwort.
     */
    @Min(100)
    @Max(4000)
    private int maxTokens = 2000;

    /**
     * Embedding-spezifische Konfiguration
     */
    @NotNull
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /**
     * Retry-Konfiguration für API-Aufrufe
     */
    @NotNull
    private RetryConfig retry = new RetryConfig();

    /**
     * Rate-Limiting Konfiguration
     */
    @NotNull
    private RateLimitConfig rateLimit = new RateLimitConfig();

    @Getter
    @Setter
    public static class EmbeddingConfig {
        /**
         * Embedding-Dimensionen (1536 für text-embedding-3-small)
         */
        private int dimensions = 1536;

        /**
         * Batch-Größe für Bulk-Embedding-Operationen
         */
        @Min(1)
        @Max(100)
        private int batchSize = 20;

        /**
         * Standard Top-K für Similarity-Suche
         */
        @Min(1)
        @Max(1000)
        private int defaultTopK = 50;

        /**
         * Maximale Text-Länge für Embeddings (Zeichen)
         */
        @Min(100)
        @Max(8000)
        private int maxTextLength = 2000;

        /**
         * Chunk-Größe für lange Texte (Zeichen)
         */
        @Min(100)
        @Max(2000)
        private int chunkSize = 500;

        /**
         * Chunk-Overlap für Kontext-Erhaltung (Zeichen)
         */
        @Min(0)
        @Max(200)
        private int chunkOverlap = 50;
    }

    @Getter
    @Setter
    public static class RetryConfig {
        /**
         * Maximale Anzahl von Wiederholungsversuchen
         */
        @Min(1)
        @Max(5)
        private int maxAttempts = 3;

        /**
         * Initiale Backoff-Zeit in Millisekunden
         */
        @Min(100)
        @Max(10000)
        private long backoffMs = 1000;

        /**
         * Multiplikator für exponentiellen Backoff
         */
        @Min(1)
        @Max(5)
        private double backoffMultiplier = 2.0;
    }

    @Getter
    @Setter
    public static class RateLimitConfig {
        /**
         * Aktiviert Rate-Limiting
         */
        private boolean enabled = true;

        /**
         * Maximale Requests pro Minute für Chat-Completions
         */
        @Min(1)
        @Max(1000)
        private int chatRequestsPerMinute = 60;

        /**
         * Maximale Requests pro Minute für Embeddings
         */
        @Min(1)
        @Max(3000)
        private int embeddingRequestsPerMinute = 200;
    }
}
