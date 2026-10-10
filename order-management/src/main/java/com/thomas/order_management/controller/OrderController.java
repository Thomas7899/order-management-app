// src/main/java/com/thomas/order_management/controller/OrderController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.ChangeDocumentDto;
import com.thomas.order_management.dto.PageResponse;
import com.thomas.order_management.dto.sales.*;
import com.thomas.order_management.model.CreditStatus;
import com.thomas.order_management.model.OrderStatus;
import com.thomas.order_management.model.ProcessingStatus;
import com.thomas.order_management.service.sales.DeliveryService;
import com.thomas.order_management.service.sales.BillingService;
import com.thomas.order_management.service.sales.DocumentFlowService;
import com.thomas.order_management.service.sales.SalesOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Kundenaufträge", description = "Kundenauftragsbearbeitung (VA01/VA02/VA03) mit Preisfindung, Verfügbarkeits- und Kreditprüfung")
public class OrderController {

    private final SalesOrderService orderService;
    private final DocumentFlowService documentFlowService;
    private final DeliveryService deliveryService;
    private final BillingService billingService;

    public OrderController(SalesOrderService orderService, DocumentFlowService documentFlowService,
                           DeliveryService deliveryService, BillingService billingService) {
        this.orderService = orderService;
        this.documentFlowService = documentFlowService;
        this.deliveryService = deliveryService;
        this.billingService = billingService;
    }

    @Operation(summary = "Aufträge suchen", description = "Seitenweise Liste mit Filtern; Sortierung z. B. sort=orderDate,desc")
    @GetMapping
    public PageResponse<OrderListItemDto> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<OrderStatus> status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) CreditStatus creditStatus,
            @RequestParam(required = false) ProcessingStatus deliveryStatus,
            @RequestParam(required = false) ProcessingStatus billingStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.search(new SalesOrderService.OrderSearch(search, status, customerId, creditStatus,
                deliveryStatus, billingStatus, dateFrom, dateTo), pageable);
    }

    @Operation(summary = "Auftrag anzeigen")
    @GetMapping("/{id}")
    public OrderDto get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @Operation(summary = "Auftrag über Belegnummer anzeigen")
    @GetMapping("/number/{orderNumber}")
    public OrderDto getByNumber(@PathVariable String orderNumber) {
        return orderService.getByNumber(orderNumber);
    }

    @Operation(summary = "Aufträge eines Debitors")
    @GetMapping("/customer/{customerId}")
    public List<OrderListItemDto> getOrdersByCustomer(@PathVariable Long customerId) {
        return orderService.getByCustomer(customerId);
    }

    @Operation(summary = "Auftrag simulieren", description = "Preisfindung, Verfügbarkeit und Kreditprüfung ohne zu sichern")
    @PostMapping("/simulate")
    public OrderSimulationDto simulate(@Valid @RequestBody OrderRequests.Create request) {
        return orderService.simulate(request);
    }

    @Operation(summary = "Auftrag anlegen")
    @PostMapping
    public ResponseEntity<OrderDto> create(@Valid @RequestBody OrderRequests.Create request) {
        OrderDto created = orderService.create(request);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @Operation(summary = "Auftragskopf ändern")
    @PatchMapping("/{id}")
    public OrderDto updateHeader(@PathVariable Long id, @Valid @RequestBody OrderRequests.UpdateHeader request) {
        return orderService.updateHeader(id, request);
    }

    @Operation(summary = "Position hinzufügen")
    @PostMapping("/{id}/items")
    public OrderDto addItem(@PathVariable Long id, @Valid @RequestBody OrderRequests.Item request) {
        return orderService.addItem(id, request);
    }

    @Operation(summary = "Position ändern (Menge, Rabatt)")
    @PatchMapping("/{id}/items/{itemNumber}")
    public OrderDto changeItem(@PathVariable Long id, @PathVariable int itemNumber,
                               @Valid @RequestBody OrderRequests.ChangeItem request) {
        return orderService.changeItem(id, itemNumber, request);
    }

    @Operation(summary = "Positionen absagen", description = "Setzt einen Absagegrund; ohne Positionsnummern werden alle offenen Positionen abgesagt")
    @PostMapping("/{id}/reject")
    public OrderDto reject(@PathVariable Long id, @Valid @RequestBody OrderRequests.Reject request) {
        return orderService.reject(id, request);
    }

    @Operation(summary = "Kreditsperre freigeben")
    @PostMapping("/{id}/release-credit")
    public OrderDto releaseCreditBlock(@PathVariable Long id) {
        return orderService.releaseCreditBlock(id);
    }

    @Operation(summary = "Verfügbarkeit neu prüfen")
    @PostMapping("/{id}/reconfirm")
    public OrderDto reconfirm(@PathVariable Long id) {
        return orderService.reconfirm(id);
    }

    @Operation(summary = "Rückstandsbearbeitung", description = "Prüft alle nicht vollständig bestätigten Positionen erneut (V_RA)")
    @PostMapping("/backlog/process")
    public Map<String, Integer> processBacklog() {
        return Map.of("improvedItems", orderService.processBacklog());
    }

    @Operation(summary = "Belegfluss")
    @GetMapping("/{id}/document-flow")
    public DocumentFlowDto documentFlow(@PathVariable Long id) {
        return documentFlowService.forOrder(id);
    }

    @Operation(summary = "Lieferungen zum Auftrag")
    @GetMapping("/{id}/deliveries")
    public List<DeliveryDto> deliveries(@PathVariable Long id) {
        return deliveryService.getByOrder(id);
    }

    @Operation(summary = "Fakturen zum Auftrag")
    @GetMapping("/{id}/invoices")
    public List<InvoiceDto> invoices(@PathVariable Long id) {
        return billingService.getByOrder(id);
    }

    @Operation(summary = "Änderungsbelege")
    @GetMapping("/{id}/changes")
    public List<ChangeDocumentDto> changes(@PathVariable Long id) {
        return orderService.getChanges(id);
    }
}
