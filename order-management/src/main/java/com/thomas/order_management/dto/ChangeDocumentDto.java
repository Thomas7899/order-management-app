package com.thomas.order_management.dto;

import com.thomas.order_management.model.ChangeDocument;

import java.time.LocalDateTime;

public record ChangeDocumentDto(
        Long id,
        ChangeDocument.ObjectClass objectClass,
        String objectNumber,
        ChangeDocument.ChangeType changeType,
        String fieldName,
        String oldValue,
        String newValue,
        String description,
        String changedBy,
        LocalDateTime changedAt
) {
}
