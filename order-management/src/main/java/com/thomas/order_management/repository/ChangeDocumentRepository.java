package com.thomas.order_management.repository;

import com.thomas.order_management.model.ChangeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChangeDocumentRepository extends JpaRepository<ChangeDocument, Long> {

    List<ChangeDocument> findByObjectClassAndObjectIdOrderByChangedAtDescIdDesc(
            ChangeDocument.ObjectClass objectClass, Long objectId);
}
