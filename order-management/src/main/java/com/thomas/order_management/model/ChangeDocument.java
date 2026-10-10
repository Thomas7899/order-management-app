package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Änderungsbeleg nach dem Vorbild von SAP CDHDR/CDPOS: eine Zeile je geändertem Feld
 * oder je Ereignis (Anlage, Storno, Buchung).
 */
@Entity
@Table(name = "change_documents", indexes = {
        @Index(name = "idx_change_documents_object", columnList = "object_class, object_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ChangeDocument {

    public enum ObjectClass { SALES_ORDER, DELIVERY, INVOICE, CUSTOMER, MATERIAL, MATERIAL_DOCUMENT }

    public enum ChangeType { INSERT, UPDATE, DELETE, EVENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_class", nullable = false, length = 30)
    private ObjectClass objectClass;

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(name = "object_number", length = 30)
    private String objectNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 10)
    private ChangeType changeType;

    @Column(name = "field_name", length = 60)
    private String fieldName;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
