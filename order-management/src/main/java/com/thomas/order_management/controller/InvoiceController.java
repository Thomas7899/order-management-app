package com.thomas.order_management.controller;

import com.thomas.order_management.dto.ChangeDocumentDto;
import com.thomas.order_management.dto.PageResponse;
import com.thomas.order_management.dto.sales.DeliveryDto;
import com.thomas.order_management.dto.sales.InvoiceDto;
import com.thomas.order_management.dto.sales.LogisticsRequests;
import com.thomas.order_management.model.Invoice;
import com.thomas.order_management.service.sales.BillingService;
import com.thomas.order_management.service.sales.InvoicePdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Fakturen", description = "Fakturierung (VF01/VF04/VF11), offene Posten und Zahlungseingang")
public class InvoiceController {

    private final BillingService billingService;
    private final InvoicePdfService pdfService;

    public InvoiceController(BillingService billingService, InvoicePdfService pdfService) {
        this.billingService = billingService;
        this.pdfService = pdfService;
    }

    @Operation(summary = "Fakturen suchen")
    @GetMapping
    public PageResponse<InvoiceDto> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Invoice.Status status,
            @RequestParam(required = false) Invoice.BillingType billingType,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20, sort = "billingDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return billingService.search(new BillingService.InvoiceSearch(search, status, billingType, overdue, customerId,
                dateFrom, dateTo), pageable);
    }

    @Operation(summary = "Fakturavorrat", description = "Lieferungen mit Warenausgang, die noch nicht fakturiert sind (VF04)")
    @GetMapping("/due-list")
    public List<DeliveryDto> billingDueList() {
        return billingService.getBillingDueList();
    }

    @Operation(summary = "Faktura anzeigen")
    @GetMapping("/{id}")
    public InvoiceDto get(@PathVariable Long id) {
        return billingService.get(id);
    }

    @Operation(summary = "Rechnung als PDF")
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        InvoicePdfService.RenderedPdf pdf = pdfService.render(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }

    @Operation(summary = "Faktura zur Lieferung anlegen")
    @PostMapping
    public ResponseEntity<InvoiceDto> create(@Valid @RequestBody LogisticsRequests.CreateInvoice request) {
        InvoiceDto created = billingService.create(request);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @Operation(summary = "Faktura stornieren (Stornorechnung S1)")
    @PostMapping("/{id}/cancel")
    public InvoiceDto cancel(@PathVariable Long id, @Valid @RequestBody(required = false) LogisticsRequests.Cancel request) {
        return billingService.cancel(id, request);
    }

    @Operation(summary = "Zahlungseingang buchen", description = "Gleicht den offenen Posten aus; Skonto wird automatisch berücksichtigt")
    @PostMapping("/{id}/payment")
    public InvoiceDto postPayment(@PathVariable Long id, @Valid @RequestBody(required = false) LogisticsRequests.Payment request) {
        return billingService.postPayment(id, request);
    }

    @Operation(summary = "Änderungsbelege")
    @GetMapping("/{id}/changes")
    public List<ChangeDocumentDto> changes(@PathVariable Long id) {
        return billingService.getChanges(id);
    }
}
