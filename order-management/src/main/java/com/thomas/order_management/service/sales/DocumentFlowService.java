package com.thomas.order_management.service.sales;

import com.thomas.order_management.dto.sales.CodeText;
import com.thomas.order_management.dto.sales.DocumentFlowDto;
import com.thomas.order_management.dto.sales.DocumentFlowDto.DocumentType;
import com.thomas.order_management.dto.sales.DocumentFlowDto.Node;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.mapper.SalesDocumentMapper;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.DeliveryRepository;
import com.thomas.order_management.repository.InvoiceRepository;
import com.thomas.order_management.repository.OrderRepository;
import com.thomas.order_management.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class DocumentFlowService {

    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final InvoiceRepository invoiceRepository;
    private final StockMovementRepository movementRepository;

    public DocumentFlowService(OrderRepository orderRepository, DeliveryRepository deliveryRepository,
                               InvoiceRepository invoiceRepository, StockMovementRepository movementRepository) {
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.invoiceRepository = invoiceRepository;
        this.movementRepository = movementRepository;
    }

    public DocumentFlowDto forOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Kundenauftrag " + orderId + " nicht gefunden"));

        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(DocumentType.SALES_ORDER, "Kundenauftrag", order.getId(), order.getOrderNumber(), null, 0,
                order.getOrderDate(), SalesDocumentMapper.status(order.getStatus()), order.getNetAmount()));

        List<Invoice> invoices = invoiceRepository.findByOrderIdOrderByCreatedAtAsc(orderId);

        for (Delivery delivery : deliveryRepository.findByOrderIdOrderByCreatedAtAsc(orderId)) {
            nodes.add(new Node(DocumentType.DELIVERY, "Auslieferung", delivery.getId(), delivery.getDeliveryNumber(),
                    order.getOrderNumber(), 1, delivery.getCreatedAt(),
                    CodeText.of(delivery.getStatus().name(), delivery.getStatus().getDisplayName()), null));

            Map<String, List<StockMovement>> documents = new LinkedHashMap<>();
            movementRepository.findByReferenceTypeAndReferenceNumber(StockMovement.ReferenceType.DELIVERY,
                            delivery.getDeliveryNumber()).stream()
                    .sorted(Comparator.comparing(StockMovement::getPostingDate).thenComparing(StockMovement::getId))
                    .forEach(m -> documents.computeIfAbsent(m.getMovementNumber(), k -> new ArrayList<>()).add(m));
            documents.forEach((number, items) -> {
                StockMovement first = items.get(0);
                boolean reversal = first.getReversedMovement() != null;
                boolean reversed = items.stream().allMatch(StockMovement::isReversed);
                nodes.add(new Node(reversal ? DocumentType.GOODS_ISSUE_REVERSAL : DocumentType.GOODS_ISSUE,
                        (reversal ? "Storno WA " : "WL Warenausgang ") + first.getMovementCode(),
                        first.getId(), number, delivery.getDeliveryNumber(), 2, first.getPostingDate(),
                        CodeText.of(reversed ? "REVERSED" : "POSTED", reversed ? "Storniert" : "Gebucht"),
                        null));
            });

            invoices.stream()
                    .filter(i -> i.getDelivery() != null && i.getDelivery().getId().equals(delivery.getId()))
                    .forEach(i -> addInvoiceNodes(nodes, i, delivery.getDeliveryNumber()));
        }

        return new DocumentFlowDto(order.getOrderNumber(), nodes);
    }

    private void addInvoiceNodes(List<Node> nodes, Invoice invoice, String parentNumber) {
        boolean cancellation = invoice.getBillingType() == Invoice.BillingType.S1;
        String parent = cancellation && invoice.getReferenceInvoice() != null
                ? invoice.getReferenceInvoice().getInvoiceNumber() : parentNumber;
        nodes.add(new Node(cancellation ? DocumentType.CANCELLATION_INVOICE : DocumentType.INVOICE,
                invoice.getBillingType().getDisplayName() + " " + invoice.getBillingType().name(),
                invoice.getId(), invoice.getInvoiceNumber(), parent, cancellation ? 3 : 2,
                invoice.getCreatedAt(),
                CodeText.of(invoice.getStatus().name(), invoice.getStatus().getDisplayName()),
                invoice.getNetAmount()));

        if (!cancellation && invoice.getStatus() == Invoice.Status.PAID && invoice.getPaidAt() != null) {
            nodes.add(new Node(DocumentType.PAYMENT, "Zahlungseingang", invoice.getId(),
                    "ZE " + invoice.getInvoiceNumber(), invoice.getInvoiceNumber(), 3,
                    invoice.getPaidAt().atTime(12, 0),
                    CodeText.of("CLEARED", "Ausgeglichen"), invoice.getPaidAmount()));
        }
    }
}
