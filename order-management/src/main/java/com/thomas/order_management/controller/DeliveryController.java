package com.thomas.order_management.controller;

import com.thomas.order_management.dto.ChangeDocumentDto;
import com.thomas.order_management.dto.PageResponse;
import com.thomas.order_management.dto.sales.DeliveryDto;
import com.thomas.order_management.dto.sales.LogisticsRequests;
import com.thomas.order_management.model.Delivery;
import com.thomas.order_management.model.ProcessingStatus;
import com.thomas.order_management.service.sales.DeliveryService;
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

@RestController
@RequestMapping("/api/deliveries")
@Tag(name = "Auslieferungen", description = "Lieferungen und Warenausgang (VL01N/VL02N/VL09)")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Operation(summary = "Lieferungen suchen")
    @GetMapping
    public PageResponse<DeliveryDto> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Delivery.Status status,
            @RequestParam(required = false) ProcessingStatus billingStatus,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return deliveryService.search(new DeliveryService.DeliverySearch(search, status, billingStatus, warehouseId,
                dateFrom, dateTo), pageable);
    }

    @Operation(summary = "Lieferung anzeigen")
    @GetMapping("/{id}")
    public DeliveryDto get(@PathVariable Long id) {
        return deliveryService.get(id);
    }

    @Operation(summary = "Lieferung zum Auftrag anlegen")
    @PostMapping
    public ResponseEntity<DeliveryDto> create(@Valid @RequestBody LogisticsRequests.CreateDelivery request) {
        DeliveryDto created = deliveryService.create(request);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @Operation(summary = "Warenausgang buchen (Bewegungsart 601)")
    @PostMapping("/{id}/goods-issue")
    public DeliveryDto postGoodsIssue(@PathVariable Long id,
                                      @RequestBody(required = false) LogisticsRequests.PostGoodsIssue request) {
        return deliveryService.postGoodsIssue(id, request);
    }

    @Operation(summary = "Warenausgang stornieren (Bewegungsart 602)")
    @PostMapping("/{id}/goods-issue/reverse")
    public DeliveryDto reverseGoodsIssue(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) LogisticsRequests.Cancel request) {
        return deliveryService.reverseGoodsIssue(id, request);
    }

    @Operation(summary = "Lieferung stornieren (nur ohne Warenausgang)")
    @PostMapping("/{id}/cancel")
    public DeliveryDto cancel(@PathVariable Long id) {
        return deliveryService.cancel(id);
    }

    @Operation(summary = "Änderungsbelege")
    @GetMapping("/{id}/changes")
    public List<ChangeDocumentDto> changes(@PathVariable Long id) {
        return deliveryService.getChanges(id);
    }
}
