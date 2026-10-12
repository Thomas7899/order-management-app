package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Auftragseingang (angelehnt an SAP Sales Order Automation): eine Bestellung als E-Mail-Text oder PDF,
 * deren Inhalt ausgelesen und nach Prüfung durch den Vertrieb in einen Kundenauftrag übernommen wird.
 */
@Entity
@Table(name = "sales_order_requests")
@Getter
@Setter
@NoArgsConstructor
public class SalesOrderRequest {

    public enum Status {
        OPEN("Offen"), CONVERTED("Übernommen"), REJECTED("Abgelehnt");

        private final String description;

        Status(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum SourceType {
        EMAIL("E-Mail"), PDF("PDF"), TEXT("Text");

        private final String description;

        SourceType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false, unique = true, length = 10)
    private String requestNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private SourceType sourceType;

    @Column(length = 200)
    private String sender;

    @Column(length = 300)
    private String subject;

    /** Text der E-Mail bzw. der aus dem PDF gelesene Text. */
    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    /** Originaldatei (PDF), getrennt gespeichert, damit Listen sie nicht mitladen. */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "file_id")
    private SalesOrderRequestFile file;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** Ausgelesene Angaben als JSON (so, wie sie im Dokument stehen, noch ohne Zuordnung). */
    @Column(columnDefinition = "TEXT")
    private String extraction;

    @Column(name = "extraction_source", length = 10)
    private String extractionSource;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "rejection_note", length = 500)
    private String rejectionNote;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by", length = 50)
    private String processedBy;

    @Column(name = "created_by", length = 50)
    private String createdBy;
}
