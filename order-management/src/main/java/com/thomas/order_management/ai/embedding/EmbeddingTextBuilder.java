// order-management/src/main/java/com/thomas/order_management/ai/embedding/EmbeddingTextBuilder.java
package com.thomas.order_management.ai.embedding;

import com.thomas.order_management.model.ProductReview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Baut optimierte Texte für Review-Embeddings.
 * 
 * <h2>Design-Prinzipien:</h2>
 * <ul>
 *   <li>Strukturierter Text mit Metadaten für bessere semantische Erfassung</li>
 *   <li>Sentiment-Signal aus Rating explizit eingebunden</li>
 *   <li>Chunking-Unterstützung für lange Reviews</li>
 *   <li>Normalisierung für konsistente Embeddings</li>
 * </ul>
 * 
 * <h2>Embedding-Strategie:</h2>
 * <p>Der Text wird mit Kontext angereichert, um die semantische Suche zu verbessern:</p>
 * <pre>
 * [SENTIMENT: POSITIV] Produktbewertung für: {Produktname}
 * Kategorie: {Kategorie}
 * Bewertung: {Rating}/5 Sterne
 * 
 * Kundenfeedback:
 * {Kommentar}
 * </pre>
 * 
 * @author Thomas Osterlehner
 * @since 2.0
 */
@Component
@RequiredArgsConstructor
public class EmbeddingTextBuilder {

    private static final int DEFAULT_MAX_LENGTH = 2000;

    /**
     * Baut einen optimierten Embedding-Text aus einer ProductReview.
     * 
     * <p>Der Text enthält:</p>
     * <ul>
     *   <li>Explizites Sentiment-Signal basierend auf Rating</li>
     *   <li>Produktname und Kategorie für Kontext</li>
     *   <li>Numerisches Rating für Gewichtung</li>
     *   <li>Bereinigten Kommentar-Text</li>
     * </ul>
     *
     * @param review Die zu verarbeitende Review
     * @return Optimierter Text für Embedding-Generierung
     */
    public String buildEmbeddingText(ProductReview review) {
        return buildEmbeddingText(review, DEFAULT_MAX_LENGTH);
    }

    /**
     * Baut einen Embedding-Text mit maximaler Länge.
     *
     * @param review Die zu verarbeitende Review
     * @param maxLength Maximale Textlänge in Zeichen
     * @return Optimierter Text für Embedding-Generierung
     */
    public String buildEmbeddingText(ProductReview review, int maxLength) {
        StringBuilder sb = new StringBuilder();
        
        // Sentiment-Signal als erstes Element (wichtig für Embedding-Qualität)
        String sentimentSignal = deriveSentimentSignal(review.getRating());
        sb.append("[SENTIMENT: ").append(sentimentSignal).append("] ");
        
        // Produktkontext
        sb.append("Produktbewertung für: ");
        if (review.getProduct() != null) {
            sb.append(normalizeText(review.getProduct().getName()));
            
            if (review.getProduct().getCategory() != null && !review.getProduct().getCategory().isBlank()) {
                sb.append("\nKategorie: ").append(normalizeText(review.getProduct().getCategory()));
            }
        } else {
            sb.append("Unbekanntes Produkt");
        }
        
        // Numerisches Rating
        sb.append("\nBewertung: ").append(review.getRating()).append("/5 Sterne");
        
        // Rating-Beschreibung für bessere semantische Erfassung
        sb.append(" (").append(getRatingDescription(review.getRating())).append(")");
        
        // Kommentar mit Header
        sb.append("\n\nKundenfeedback:\n");
        String comment = review.getComment();
        if (comment != null && !comment.isBlank()) {
            sb.append(normalizeText(comment));
        } else {
            sb.append("(Keine Textbewertung vorhanden)");
        }
        
        // Truncate falls nötig
        String result = sb.toString();
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength - 3) + "...";
        }
        
        return result;
    }

    /**
     * Leitet ein Sentiment-Signal aus dem Rating ab.
     * 
     * @param rating Rating (1-5)
     * @return Sentiment-Signal (NEGATIV, NEUTRAL, POSITIV, SEHR_POSITIV)
     */
    private String deriveSentimentSignal(int rating) {
        return switch (rating) {
            case 1 -> "SEHR_NEGATIV";
            case 2 -> "NEGATIV";
            case 3 -> "NEUTRAL";
            case 4 -> "POSITIV";
            case 5 -> "SEHR_POSITIV";
            default -> "UNBEKANNT";
        };
    }

    /**
     * Gibt eine textuelle Beschreibung des Ratings zurück.
     */
    private String getRatingDescription(int rating) {
        return switch (rating) {
            case 1 -> "sehr unzufrieden";
            case 2 -> "unzufrieden";
            case 3 -> "neutral";
            case 4 -> "zufrieden";
            case 5 -> "sehr zufrieden";
            default -> "keine Bewertung";
        };
    }

    /**
     * Normalisiert Text für konsistente Embeddings.
     * 
     * <ul>
     *   <li>Entfernt übermäßige Whitespaces</li>
     *   <li>Entfernt Steuerzeichen</li>
     *   <li>Trimmt den Text</li>
     * </ul>
     */
    private String normalizeText(String text) {
        if (text == null) return "";
        
        return text
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "") // Entferne Steuerzeichen außer Whitespace
                .replaceAll("\\s+", " ")                   // Normalisiere Whitespace
                .trim();
    }

    /**
     * Erstellt Chunks aus langem Text für separate Embeddings.
     * 
     * <p>Verwendet Overlap für Kontext-Erhaltung zwischen Chunks.</p>
     *
     * @param text Der zu chunkende Text
     * @param chunkSize Größe jedes Chunks
     * @param overlap Überlappung zwischen Chunks
     * @return Liste von Text-Chunks
     */
    public java.util.List<String> chunkText(String text, int chunkSize, int overlap) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        
        if (text == null || text.length() <= chunkSize) {
            chunks.add(text != null ? text : "");
            return chunks;
        }
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // Versuche an Wortgrenze zu schneiden
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start + chunkSize / 2) {
                    end = lastSpace;
                }
            }
            
            chunks.add(text.substring(start, end).trim());
            start = end - overlap;
            
            // Verhindere Endlosschleife
            if (start >= text.length() - overlap) break;
        }
        
        return chunks;
    }
}
