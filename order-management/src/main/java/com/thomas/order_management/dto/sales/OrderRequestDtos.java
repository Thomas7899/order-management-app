package com.thomas.order_management.dto.sales;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Auftragseingang: ausgelesene Angaben, Zuordnungsvorschläge und Listenansicht.
 */
public final class OrderRequestDtos {

    private OrderRequestDtos() {
    }

    /** Konfidenz einer Zuordnung: HIGH = eindeutig, MEDIUM = prüfen, LOW = unsicher, NONE = nichts gefunden. */
    public enum Confidence { HIGH, MEDIUM, LOW, NONE }

    // ------------------------------------------------------------------
    // Ausgelesen (so, wie es im Dokument steht)
    // ------------------------------------------------------------------

    public record Extraction(
            Party customer,
            String customerReference,
            /** ISO-Datum (yyyy-MM-dd) */
            String requestedDeliveryDate,
            String deliveryAddress,
            String notes,
            List<Line> items
    ) {
    }

    public record Party(String name, String company, String email, String customerNumber, String city) {
    }

    public record Line(
            /** Artikelnummer, wie sie im Dokument steht */
            String articleNumber,
            String description,
            Integer quantity,
            String unit,
            /** Vorschlag der KI aus dem Materialkatalog (nur bei eindeutiger Bedeutung) */
            String catalogMaterialNumber
    ) {
    }

    // ------------------------------------------------------------------
    // Zuordnung zu Stammdaten
    // ------------------------------------------------------------------

    public record CustomerCandidate(Long customerId, String customerNumber, String name, String email, String city,
                                    double score, Confidence confidence, String reason) {
    }

    public record MaterialCandidate(Long productId, String materialNumber, String name, String unitOfMeasure,
                                    BigDecimal price, double score, Confidence confidence, String reason) {
    }

    public record CustomerProposal(Party extracted, CustomerCandidate match, List<CustomerCandidate> alternatives) {
    }

    public record ItemProposal(int lineNumber, Line extracted, MaterialCandidate match, List<MaterialCandidate> alternatives) {
    }

    /** Meldung mit Typ S (Erfolg), I (Info), W (Warnung) oder E (Fehler). */
    public record Message(String type, String text) {
    }

    // ------------------------------------------------------------------
    // Ansicht
    // ------------------------------------------------------------------

    public record Detail(
            Long id,
            String requestNumber,
            CodeText status,
            CodeText sourceType,
            String sender,
            String subject,
            LocalDateTime receivedAt,
            String sourceText,
            String fileName,
            String extractionSource,
            LocalDateTime extractedAt,
            CustomerProposal customer,
            String customerReference,
            LocalDate requestedDeliveryDate,
            String deliveryAddress,
            String notes,
            List<ItemProposal> items,
            Confidence confidence,
            List<Message> messages,
            Long orderId,
            String orderNumber,
            String rejectionNote,
            LocalDateTime processedAt,
            String processedBy,
            String createdBy
    ) {
    }

    public record ListItem(
            Long id,
            String requestNumber,
            CodeText status,
            CodeText sourceType,
            String sender,
            String subject,
            LocalDateTime receivedAt,
            String customerName,
            String customerNumber,
            int itemCount,
            Confidence confidence,
            Long orderId,
            String orderNumber
    ) {
    }

    public record Sample(String key, String title, String description, String format) {
    }

    public record SampleText(String sender, String subject, String text) {
    }

    public record Reject(@Size(max = 500, message = "Begründung: maximal 500 Zeichen") String note) {
    }
}
