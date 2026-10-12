package com.thomas.order_management.repository;

import com.thomas.order_management.model.SalesOrderRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesOrderRequestRepository extends JpaRepository<SalesOrderRequest, Long> {

    @EntityGraph(attributePaths = "order")
    List<SalesOrderRequest> findByStatusInOrderByReceivedAtDesc(Collection<SalesOrderRequest.Status> status);

    /** Mit Auftrag und Datei (die Datei ist höchstens wenige MB groß und wird nur in der Detailansicht gebraucht). */
    @EntityGraph(attributePaths = {"order", "file"})
    Optional<SalesOrderRequest> findWithOrderById(Long id);

    Optional<SalesOrderRequest> findByOrderId(Long orderId);

    List<SalesOrderRequest> findByStatusAndExtractionIsNull(SalesOrderRequest.Status status);

    long countByStatus(SalesOrderRequest.Status status);
}
