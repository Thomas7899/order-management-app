package com.thomas.order_management.controller;

import com.thomas.order_management.dto.sales.OrderDto;
import com.thomas.order_management.dto.sales.OrderRequestDtos.*;
import com.thomas.order_management.dto.sales.OrderRequests;
import com.thomas.order_management.model.SalesOrderRequest;
import com.thomas.order_management.model.SalesOrderRequestFile;
import com.thomas.order_management.service.sales.intake.OrderRequestSamples;
import com.thomas.order_management.service.sales.intake.OrderRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order-requests")
@Tag(name = "Auftragseingang", description = "Bestellungen per E-Mail oder PDF auslesen, zuordnen und als Kundenauftrag übernehmen")
public class OrderRequestController {

    private final OrderRequestService service;
    private final OrderRequestSamples samples;

    public OrderRequestController(OrderRequestService service, OrderRequestSamples samples) {
        this.service = service;
        this.samples = samples;
    }

    @Operation(summary = "Eingänge auflisten", description = "Optional nach Status filtern (OPEN, CONVERTED, REJECTED)")
    @GetMapping
    public List<ListItem> list(@RequestParam(required = false) List<SalesOrderRequest.Status> status) {
        return service.list(status);
    }

    @Operation(summary = "Anzahl offener Eingänge")
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("open", service.countOpen());
    }

    @Operation(summary = "Eingang mit ausgelesenen Angaben und Zuordnungsvorschlägen")
    @GetMapping("/{id}")
    public Detail get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Originaldatei des Eingangs")
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        SalesOrderRequestFile file = service.file(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.getContent());
    }

    @Operation(summary = "Bestellung erfassen und auslesen", description = "Text und/oder Datei (PDF, .txt, .eml)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Detail> create(@RequestParam(required = false) String text,
                                         @RequestParam(required = false) String sender,
                                         @RequestParam(required = false) String subject,
                                         @RequestPart(required = false) MultipartFile file) throws IOException {
        Detail created = service.create(new OrderRequestService.Incoming(text, sender, subject,
                file == null ? null : file.getOriginalFilename(), file == null ? null : file.getContentType(),
                file == null || file.isEmpty() ? null : file.getBytes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Erneut auslesen (z. B. mit KI, wenn sie beim Eingang nicht verfügbar war)")
    @PostMapping("/{id}/extract")
    public Detail reextract(@PathVariable Long id) {
        return service.reextract(id);
    }

    @Operation(summary = "Als Kundenauftrag übernehmen", description = "Mit den vom Vertrieb geprüften und ggf. korrigierten Angaben")
    @PostMapping("/{id}/convert")
    public ResponseEntity<OrderDto> convert(@PathVariable Long id, @Valid @RequestBody OrderRequests.Create order) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.convert(id, order));
    }

    @Operation(summary = "Eingang ablehnen (z. B. keine Bestellung oder Dublette)")
    @PostMapping("/{id}/reject")
    public Detail reject(@PathVariable Long id, @Valid @RequestBody(required = false) Reject reject) {
        return service.reject(id, reject == null ? null : reject.note());
    }

    @Operation(summary = "Beispielbestellungen für die Demo")
    @GetMapping("/samples")
    public List<Sample> samples() {
        return samples.list();
    }

    @Operation(summary = "Text einer Beispielbestellung")
    @GetMapping("/samples/{key}")
    public SampleText sample(@PathVariable String key) {
        return samples.text(key, LocalDate.now());
    }

    @Operation(summary = "Beispielbestellung als PDF")
    @GetMapping("/samples/" + OrderRequestSamples.PDF + "/file")
    public ResponseEntity<byte[]> samplePdf() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("bestellung.pdf").build().toString())
                .body(samples.pdf(LocalDate.now()));
    }
}
