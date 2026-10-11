package com.thomas.order_management.service;

import com.thomas.order_management.dto.ChangeDocumentDto;
import com.thomas.order_management.model.ChangeDocument;
import com.thomas.order_management.model.ChangeDocument.ChangeType;
import com.thomas.order_management.model.ChangeDocument.ObjectClass;
import com.thomas.order_management.repository.ChangeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ChangeDocumentService {

    private final ChangeDocumentRepository repository;
    private final CurrentUserProvider currentUser;

    public ChangeDocumentService(ChangeDocumentRepository repository, CurrentUserProvider currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional
    public void logEvent(ObjectClass objectClass, Long objectId, String objectNumber,
                         ChangeType changeType, String description, LocalDateTime changedAt) {
        ChangeDocument doc = base(objectClass, objectId, objectNumber, changeType, changedAt);
        doc.setDescription(description);
        repository.save(doc);
    }

    @Transactional
    public void logEvent(ObjectClass objectClass, Long objectId, String objectNumber,
                         ChangeType changeType, String description) {
        logEvent(objectClass, objectId, objectNumber, changeType, description, LocalDateTime.now());
    }

    /** Schreibt nur dann einen Beleg, wenn sich der Wert tatsächlich geändert hat. */
    @Transactional
    public void logFieldChange(ObjectClass objectClass, Long objectId, String objectNumber,
                               String fieldName, Object oldValue, Object newValue) {
        String oldText = stringify(oldValue);
        String newText = stringify(newValue);
        if (Objects.equals(oldText, newText)) {
            return;
        }
        ChangeDocument doc = base(objectClass, objectId, objectNumber, ChangeType.UPDATE, LocalDateTime.now());
        doc.setFieldName(fieldName);
        doc.setOldValue(oldText);
        doc.setNewValue(newText);
        repository.save(doc);
    }

    @Transactional(readOnly = true)
    public List<ChangeDocumentDto> getChanges(ObjectClass objectClass, Long objectId) {
        return repository.findByObjectClassAndObjectIdOrderByChangedAtDescIdDesc(objectClass, objectId).stream()
                .map(d -> new ChangeDocumentDto(d.getId(), d.getObjectClass(), d.getObjectNumber(),
                        d.getChangeType(), d.getFieldName(), d.getOldValue(), d.getNewValue(),
                        d.getDescription(), d.getChangedBy(), d.getChangedAt()))
                .toList();
    }

    private ChangeDocument base(ObjectClass objectClass, Long objectId, String objectNumber,
                                ChangeType changeType, LocalDateTime changedAt) {
        ChangeDocument doc = new ChangeDocument();
        doc.setObjectClass(objectClass);
        doc.setObjectId(objectId);
        doc.setObjectNumber(objectNumber);
        doc.setChangeType(changeType);
        doc.setChangedBy(currentUser.currentUsername());
        doc.setChangedAt(changedAt);
        return doc;
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        String text = value instanceof java.math.BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : value.toString();
        return text.length() > 500 ? text.substring(0, 497) + "..." : text;
    }
}
