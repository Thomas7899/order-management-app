package com.thomas.order_management.service.sales;

import com.thomas.order_management.dto.sales.*;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.model.*;
import com.thomas.order_management.model.StockMovement.MovementType;
import com.thomas.order_management.model.StockMovement.ReferenceType;
import com.thomas.order_management.repository.*;
import com.thomas.order_management.service.StockPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Durchläuft den Order-to-Cash-Prozess über die echten Services gegen eine H2-Datenbank:
 * Auftrag → Lieferung → Warenausgang → Faktura → Zahlungseingang, inklusive Stornos.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Order-to-Cash Integrationstest")
class OrderToCashIntegrationTest {

    @Autowired private SalesOrderService orderService;
    @Autowired private DeliveryService deliveryService;
    @Autowired private BillingService billingService;
    @Autowired private DocumentFlowService documentFlowService;
    @Autowired private AvailabilityService availabilityService;
    @Autowired private StockPostingService stockPostingService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private StockMovementRepository movementRepository;

    private Customer customer;
    private Product product;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        customer = new Customer("Erika", "Muster", "erika." + suffix + "@example.com");
        customer.setCustomerNumber("T" + suffix.substring(0, 6));
        customer.setPaymentTerms(PaymentTerms.ZB01);
        customer.setDiscountPercent(BigDecimal.ZERO);
        customer.setCreditLimit(new BigDecimal("5000.00"));
        customer = customerRepository.save(customer);

        product = new Product("Testmaterial " + suffix, null, new BigDecimal("100.00"), 0);
        product.setMaterialNumber("M" + suffix);
        product.setCostPrice(new BigDecimal("60.00"));
        product = productRepository.save(product);

        warehouse = new Warehouse("L" + suffix.substring(0, 3), "Testlager " + suffix);
        warehouse = warehouseRepository.save(warehouse);

        stockPostingService.post(new StockPostingService.Posting(MovementType.INITIAL_ENTRY, product, null, warehouse,
                10, LocalDateTime.now().minusDays(1), "Anfangsbestand", ReferenceType.MANUAL, null));
    }

    private OrderRequests.Create orderRequest(int quantity) {
        return new OrderRequests.Create(customer.getId(), LocalDate.now().plusDays(3), "PO-4711",
                null, null, null, List.of(new OrderRequests.Item(product.getId(), quantity, null)), null);
    }

    @Test
    @DisplayName("Kompletter Belegfluss mit Skontozahlung")
    void shouldRunCompleteOrderToCash() {
        OrderDto order = orderService.create(orderRequest(4));

        assertThat(order.orderNumber()).matches("1\\d{7}");
        assertThat(order.status().code()).isEqualTo("OPEN");
        assertThat(order.creditStatus().code()).isEqualTo("APPROVED");
        assertThat(order.netAmount()).isEqualByComparingTo("400.00");
        assertThat(order.totalAmount()).isEqualByComparingTo("476.00");
        assertThat(order.items().get(0).confirmedQuantity()).isEqualTo(4);
        assertThat(availabilityService.availableQuantity(product.getId(), null)).isEqualTo(6);

        DeliveryDto delivery = deliveryService.create(new LogisticsRequests.CreateDelivery(order.id(), warehouse.getId(), null, null));
        assertThat(delivery.deliveryNumber()).matches("8\\d{7}");
        assertThat(orderService.get(order.id()).deliveryStatus().code()).isEqualTo("FULLY_PROCESSED");
        assertThat(availabilityService.availableQuantity(product.getId(), null)).isEqualTo(6);

        DeliveryDto issued = deliveryService.postGoodsIssue(delivery.id(), new LogisticsRequests.PostGoodsIssue(LocalDate.now()));
        assertThat(issued.status().code()).isEqualTo("GOODS_ISSUED");
        assertThat(issued.goodsIssueDocument()).matches("49\\d{8}");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(6);

        InvoiceDto invoice = billingService.create(new LogisticsRequests.CreateInvoice(delivery.id(), LocalDate.now()));
        assertThat(invoice.invoiceNumber()).matches("9\\d{7}");
        assertThat(invoice.netAmount()).isEqualByComparingTo("400.00");
        assertThat(invoice.costAmount()).isEqualByComparingTo("240.00");
        assertThat(invoice.dueDate()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(orderService.get(order.id()).status().code()).isEqualTo("COMPLETED");

        InvoiceDto paid = billingService.postPayment(invoice.id(), new LogisticsRequests.Payment(LocalDate.now(), null));
        assertThat(paid.status().code()).isEqualTo("PAID");
        assertThat(paid.cashDiscountAmount()).isEqualByComparingTo("9.52");
        assertThat(paid.paidAmount()).isEqualByComparingTo("466.48");

        DocumentFlowDto flow = documentFlowService.forOrder(order.id());
        assertThat(flow.nodes()).extracting(DocumentFlowDto.Node::documentType).containsExactly(
                DocumentFlowDto.DocumentType.SALES_ORDER,
                DocumentFlowDto.DocumentType.DELIVERY,
                DocumentFlowDto.DocumentType.GOODS_ISSUE,
                DocumentFlowDto.DocumentType.INVOICE,
                DocumentFlowDto.DocumentType.PAYMENT);
    }

    @Test
    @DisplayName("Verfügbarkeitsprüfung bestätigt nur den vorhandenen Bestand")
    void shouldConfirmOnlyAvailableQuantity() {
        OrderSimulationDto simulation = orderService.simulate(orderRequest(15));
        assertThat(simulation.items().get(0).confirmedQuantity()).isEqualTo(10);
        assertThat(simulation.messages()).anyMatch(m -> m.messageId().equals("V1 382"));

        OrderDto order = orderService.create(orderRequest(15));
        assertThat(order.items().get(0).confirmedQuantity()).isEqualTo(10);

        DeliveryDto delivery = deliveryService.create(new LogisticsRequests.CreateDelivery(order.id(), warehouse.getId(), null, null));
        assertThat(delivery.totalQuantity()).isEqualTo(10);
        assertThat(orderService.get(order.id()).deliveryStatus().code()).isEqualTo("PARTIALLY_PROCESSED");

        // Nach einem Wareneingang bestätigt die Rückstandsbearbeitung die Restmenge
        stockPostingService.post(new StockPostingService.Posting(MovementType.GOODS_RECEIPT, product, null, warehouse,
                5, LocalDateTime.now(), "Nachlieferung", ReferenceType.PURCHASE_ORDER, "4500000001"));
        orderService.processBacklog();
        assertThat(orderService.get(order.id()).items().get(0).confirmedQuantity()).isEqualTo(15);
    }

    @Test
    @DisplayName("Kreditlimit überschritten führt zu Kreditsperre und Liefersperre")
    void shouldBlockOrderOnCreditLimit() {
        customer.setCreditLimit(new BigDecimal("100.00"));
        customerRepository.save(customer);

        OrderDto order = orderService.create(orderRequest(2));
        assertThat(order.creditStatus().code()).isEqualTo("BLOCKED");

        assertThatThrownBy(() -> deliveryService.create(new LogisticsRequests.CreateDelivery(order.id(), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Kreditsperre");

        orderService.releaseCreditBlock(order.id());
        DeliveryDto delivery = deliveryService.create(new LogisticsRequests.CreateDelivery(order.id(), null, null, null));
        assertThat(delivery.totalQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Rechnungsstorno (S1) und Warenausgangsstorno (602) machen den Fluss rückgängig")
    void shouldReverseInvoiceAndGoodsIssue() {
        OrderDto order = orderService.create(orderRequest(3));
        DeliveryDto delivery = deliveryService.create(new LogisticsRequests.CreateDelivery(order.id(), warehouse.getId(), null, null));
        deliveryService.postGoodsIssue(delivery.id(), null);
        InvoiceDto invoice = billingService.create(new LogisticsRequests.CreateInvoice(delivery.id(), null));

        assertThatThrownBy(() -> deliveryService.reverseGoodsIssue(delivery.id(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bereits fakturiert");

        InvoiceDto cancellation = billingService.cancel(invoice.id(), null);
        assertThat(cancellation.billingType().code()).isEqualTo("S1");
        assertThat(cancellation.netAmount()).isEqualByComparingTo("-300.00");
        assertThat(billingService.get(invoice.id()).status().code()).isEqualTo("CANCELLED");

        deliveryService.reverseGoodsIssue(delivery.id(), null);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(movementRepository.findByReferenceTypeAndReferenceNumber(ReferenceType.DELIVERY, delivery.deliveryNumber()))
                .extracting(StockMovement::getMovementCode)
                .containsExactlyInAnyOrder("601", "602");

        assertThat(orderService.get(order.id()).status().code()).isEqualTo("IN_PROCESS");
    }

    @Test
    @DisplayName("Absage aller Positionen storniert den Auftrag und gibt die Reservierung frei")
    void shouldCancelOrderByRejection() {
        OrderDto order = orderService.create(orderRequest(5));
        assertThat(availabilityService.availableQuantity(product.getId(), null)).isEqualTo(5);

        OrderDto rejected = orderService.reject(order.id(), new OrderRequests.Reject(RejectionReason.R03, null));

        assertThat(rejected.status().code()).isEqualTo("CANCELLED");
        assertThat(rejected.items().get(0).rejectionReason().code()).isEqualTo("R03");
        assertThat(availabilityService.availableQuantity(product.getId(), null)).isEqualTo(10);
        assertThatThrownBy(() -> orderService.addItem(order.id(), new OrderRequests.Item(product.getId(), 1, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Bestand unterschritten: Warenausgang über den Lagerortbestand hinaus wird abgelehnt")
    void shouldRejectGoodsIssueWithoutStock() {
        assertThatThrownBy(() -> stockPostingService.post(new StockPostingService.Posting(MovementType.SCRAP, product,
                warehouse, null, 11, LocalDateTime.now(), "Test", ReferenceType.MANUAL, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bestand unterschritten");
    }
}
