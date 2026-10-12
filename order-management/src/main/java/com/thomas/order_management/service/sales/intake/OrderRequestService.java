package com.thomas.order_management.service.sales.intake;

import com.thomas.order_management.dto.sales.CodeText;
import com.thomas.order_management.dto.sales.OrderDto;
import com.thomas.order_management.dto.sales.OrderRequestDtos.*;
import com.thomas.order_management.dto.sales.OrderRequests;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.model.*;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.OrderRepository;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.SalesOrderRequestRepository;
import com.thomas.order_management.service.CurrentUserProvider;
import com.thomas.order_management.service.NumberRangeService;
import com.thomas.order_management.service.reviews.ReviewAnalyticsService;
import com.thomas.order_management.service.sales.SalesOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auftragseingang: Bestellungen annehmen, auslesen, den Stammdaten zuordnen und nach Prüfung
 * durch den Vertrieb als Kundenauftrag anlegen.
 */
@Slf4j
@Service
public class OrderRequestService {

    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 20_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern HEADER = Pattern.compile("(?im)^(from|von|subject|betreff)\\s*:\\s*(.+)$");

    /** Eingang einer Bestellung, z. B. aus dem Postfach oder vom Hochladen. */
    public record Incoming(String text, String sender, String subject, String fileName, String contentType, byte[] content) {
    }

    private final SalesOrderRequestRepository repository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final SalesOrderService orderService;
    private final OrderRequestExtractor extractor;
    private final NumberRangeService numberRangeService;
    private final CurrentUserProvider currentUser;

    public OrderRequestService(SalesOrderRequestRepository repository, CustomerRepository customerRepository,
                               ProductRepository productRepository, OrderRepository orderRepository,
                               SalesOrderService orderService, OrderRequestExtractor extractor,
                               NumberRangeService numberRangeService, CurrentUserProvider currentUser) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.extractor = extractor;
        this.numberRangeService = numberRangeService;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------------

    public List<ListItem> list(List<SalesOrderRequest.Status> status) {
        List<SalesOrderRequest> requests = repository.findByStatusInOrderByReceivedAtDesc(
                status == null || status.isEmpty() ? EnumSet.allOf(SalesOrderRequest.Status.class) : status);
        MasterData master = masterData();
        return requests.stream().map(r -> {
            Extraction e = read(r);
            CustomerProposal customer = e == null ? null : OrderRequestMatcher.matchCustomer(e.customer(), master.customers);
            List<ItemProposal> items = e == null ? List.of() : OrderRequestMatcher.matchItems(e.items(), master.products);
            CustomerCandidate match = customer == null ? null : customer.match();
            String customerName = match != null ? match.name()
                    : e != null && e.customer() != null ? firstNonBlank(e.customer().company(), e.customer().name()) : null;
            return new ListItem(r.getId(), r.getRequestNumber(), status(r), sourceType(r), r.getSender(), r.getSubject(),
                    r.getReceivedAt(), customerName, match == null ? null : match.customerNumber(), items.size(),
                    e == null ? null : overall(customer, items),
                    r.getOrder() == null ? null : r.getOrder().getId(), r.getOrder() == null ? null : r.getOrder().getOrderNumber());
        }).toList();
    }

    public long countOpen() {
        return repository.countByStatus(SalesOrderRequest.Status.OPEN);
    }

    /** Detailansicht; noch nicht ausgelesene Eingänge werden dabei ausgelesen. */
    public Detail get(Long id) {
        SalesOrderRequest request = load(id);
        if (request.getExtraction() == null && request.getStatus() == SalesOrderRequest.Status.OPEN) {
            request = extractAndStore(request);
        }
        return toDetail(request, masterData());
    }

    public SalesOrderRequestFile file(Long id) {
        SalesOrderRequest request = load(id);
        if (request.getFile() == null) {
            throw new ResourceNotFoundException("Auftragseingang " + request.getRequestNumber() + " hat keine Datei");
        }
        return request.getFile();
    }

    public Optional<String> requestNumberForOrder(Long orderId) {
        return repository.findByOrderId(orderId).map(SalesOrderRequest::getRequestNumber);
    }

    // ------------------------------------------------------------------
    // Anlegen und Auslesen
    // ------------------------------------------------------------------

    public Detail create(Incoming incoming) {
        SalesOrderRequest request = receive(incoming, LocalDateTime.now());
        return toDetail(extractAndStore(request), masterData());
    }

    /** Nimmt einen Eingang an, ohne ihn auszulesen (z. B. für Demodaten; ausgelesen wird später). */
    public SalesOrderRequest receive(Incoming incoming, LocalDateTime receivedAt) {
        SalesOrderRequest request = new SalesOrderRequest();
        String text = incoming.text() == null ? "" : incoming.text().strip();
        String sender = blankToNull(incoming.sender());
        String subject = blankToNull(incoming.subject());
        request.setSourceType(sender != null || subject != null ? SalesOrderRequest.SourceType.EMAIL : SalesOrderRequest.SourceType.TEXT);

        if (incoming.content() != null && incoming.content().length > 0) {
            if (incoming.content().length > MAX_FILE_BYTES) {
                throw new BusinessException("ZI", "003", "Die Datei ist größer als 2 MB", HttpStatus.CONTENT_TOO_LARGE);
            }
            String fileName = incoming.fileName() == null ? "bestellung" : incoming.fileName();
            if (isPdf(incoming.content())) {
                String pdfText = PdfText.extract(incoming.content());
                text = text.isEmpty() ? pdfText : text + "\n\n--- Anhang " + fileName + " ---\n" + pdfText;
                request.setFile(new SalesOrderRequestFile(fileName, "application/pdf", incoming.content()));
                request.setSourceType(SalesOrderRequest.SourceType.PDF);
            } else {
                String fileText = new String(incoming.content(), StandardCharsets.UTF_8);
                if (fileText.indexOf('\0') >= 0) {
                    throw new BusinessException("ZI", "004", "Nur PDF- und Textdateien (z. B. .txt, .eml) werden unterstützt",
                            HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                }
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".eml")) fileText = decodeQuotedPrintable(fileText);
                Matcher header = HEADER.matcher(fileText.length() > 4000 ? fileText.substring(0, 4000) : fileText);
                while (header.find()) {
                    String key = header.group(1).toLowerCase(Locale.ROOT);
                    if (sender == null && (key.equals("from") || key.equals("von"))) sender = header.group(2).strip();
                    if (subject == null && (key.equals("subject") || key.equals("betreff"))) subject = header.group(2).strip();
                }
                text = text.isEmpty() ? fileText.strip() : text + "\n\n" + fileText.strip();
                request.setSourceType(sender != null ? SalesOrderRequest.SourceType.EMAIL : SalesOrderRequest.SourceType.TEXT);
            }
        }
        if (text.isBlank()) {
            throw new BusinessException("ZI", "005", "Bitte einen Bestelltext eingeben oder eine Datei hochladen", HttpStatus.BAD_REQUEST);
        }
        if (text.length() > MAX_TEXT_CHARS) text = text.substring(0, MAX_TEXT_CHARS);

        request.setRequestNumber(numberRangeService.next(NumberRangeObject.SALES_ORDER_REQUEST));
        request.setSourceText(text);
        request.setSender(truncate(sender, 200));
        request.setSubject(truncate(subject, 300));
        request.setReceivedAt(receivedAt);
        request.setCreatedBy(currentUser.currentUsername());
        return repository.save(request);
    }

    /** Erneut auslesen, z. B. wenn beim ersten Mal keine KI verfügbar war. */
    public Detail reextract(Long id) {
        SalesOrderRequest request = load(id);
        requireOpen(request);
        return toDetail(extractAndStore(request), masterData());
    }

    /** Liest alle offenen, noch nicht ausgelesenen Eingänge aus (nach dem Start im Hintergrund). */
    public int extractMissing() {
        List<SalesOrderRequest> missing = repository.findByStatusAndExtractionIsNull(SalesOrderRequest.Status.OPEN);
        missing.forEach(this::extractAndStore);
        return missing.size();
    }

    /** Ausgelesen wird außerhalb einer Transaktion, damit der KI-Aufruf keine Datenbankverbindung blockiert. */
    private SalesOrderRequest extractAndStore(SalesOrderRequest request) {
        OrderRequestExtractor.Result result = extractor.extract(request.getSourceText(), request.getSender(),
                activeProducts(), LocalDate.now());
        request.setExtraction(JsonMapper.shared().writeValueAsString(result.extraction()));
        request.setExtractionSource(result.source());
        request.setExtractedAt(LocalDateTime.now());
        return repository.save(request);
    }

    // ------------------------------------------------------------------
    // Übernehmen und Ablehnen
    // ------------------------------------------------------------------

    /** Legt den vom Vertrieb geprüften Auftrag an und verknüpft ihn mit dem Eingang. */
    @Transactional
    public OrderDto convert(Long id, OrderRequests.Create order) {
        SalesOrderRequest request = load(id);
        requireOpen(request);
        Order created = orderService.createOrder(order, null);
        request.setStatus(SalesOrderRequest.Status.CONVERTED);
        request.setOrder(created);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(currentUser.currentUsername());
        repository.save(request);
        return orderService.get(created.getId());
    }

    @Transactional
    public Detail reject(Long id, String note) {
        SalesOrderRequest request = load(id);
        requireOpen(request);
        request.setStatus(SalesOrderRequest.Status.REJECTED);
        request.setRejectionNote(blankToNull(note));
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(currentUser.currentUsername());
        return toDetail(repository.save(request), masterData());
    }

    // ------------------------------------------------------------------
    // Ansicht aufbauen
    // ------------------------------------------------------------------

    private Detail toDetail(SalesOrderRequest r, MasterData master) {
        Extraction e = read(r);
        CustomerProposal customer = e == null ? new CustomerProposal(null, null, List.of())
                : OrderRequestMatcher.matchCustomer(e.customer(), master.customers);
        List<ItemProposal> items = e == null ? List.of() : OrderRequestMatcher.matchItems(e.items(), master.products);
        LocalDate deliveryDate = e == null ? null : parseDate(e.requestedDeliveryDate());
        Confidence confidence = e == null ? null : overall(customer, items);
        List<Message> messages = e == null ? List.of() : messages(r, e, customer, items, deliveryDate, confidence);

        return new Detail(r.getId(), r.getRequestNumber(), status(r), sourceType(r), r.getSender(), r.getSubject(),
                r.getReceivedAt(), r.getSourceText(), r.getFile() == null ? null : r.getFile().getFileName(),
                r.getExtractionSource(), r.getExtractedAt(), customer,
                e == null ? null : blankToNull(e.customerReference()), deliveryDate,
                e == null ? null : blankToNull(e.deliveryAddress()), e == null ? null : blankToNull(e.notes()),
                items, confidence, messages,
                r.getOrder() == null ? null : r.getOrder().getId(), r.getOrder() == null ? null : r.getOrder().getOrderNumber(),
                r.getRejectionNote(), r.getProcessedAt(), r.getProcessedBy(), r.getCreatedBy());
    }

    private List<Message> messages(SalesOrderRequest r, Extraction e, CustomerProposal customer, List<ItemProposal> items,
                                   LocalDate deliveryDate, Confidence confidence) {
        List<Message> messages = new ArrayList<>();
        if (ReviewAnalyticsService.SOURCE_RULES.equals(r.getExtractionSource())) {
            messages.add(new Message("I", "Ohne KI mit festen Mustern ausgelesen – formlose Angaben bitte besonders prüfen"));
        }
        CustomerCandidate match = customer.match();
        if (match == null) {
            messages.add(new Message("E", "Auftraggeber nicht gefunden – bitte Debitor auswählen"));
        } else if (match.confidence() != Confidence.HIGH) {
            messages.add(new Message("W", "Auftraggeber prüfen: " + match.name() + " (" + match.reason() + ")"));
        }
        if (items.isEmpty()) {
            messages.add(new Message("E", "Keine Positionen erkannt"));
        }
        for (ItemProposal item : items) {
            String label = "Position " + item.lineNumber() + " „" + Objects.toString(item.extracted().description(), "") + "“";
            if (item.match() == null) {
                messages.add(new Message("E", label + ": kein passendes Material gefunden"));
            } else if (item.match().confidence() != Confidence.HIGH) {
                messages.add(new Message("W", label + ": Zuordnung zu " + item.match().name() + " prüfen (" + item.match().reason() + ")"));
            }
            if (item.extracted().quantity() == null || item.extracted().quantity() < 1) {
                messages.add(new Message("E", label + ": Menge fehlt"));
            }
            String unit = item.extracted().unit();
            if (unit != null && !unit.isBlank() && !unit.matches("(?i)st(ü|ue)ck|stk\\.?|st\\.?|pcs|x")) {
                messages.add(new Message("W", label + ": Einheit „" + unit + "“ – das Material wird in "
                        + (item.match() == null ? "ST" : item.match().unitOfMeasure()) + " geführt, Menge prüfen"));
            }
        }
        if (deliveryDate == null) {
            messages.add(new Message("I", "Kein Wunschtermin erkannt – vorgeschlagen wird heute + 3 Tage"));
        } else if (deliveryDate.isBefore(LocalDate.now())) {
            messages.add(new Message("W", "Wunschtermin " + DATE.format(deliveryDate) + " liegt in der Vergangenheit"));
        }
        if (match != null && e.customerReference() != null && !e.customerReference().isBlank()) {
            orderRepository.findFirstByCustomerIdAndCustomerReferenceIgnoreCase(match.customerId(), e.customerReference().strip())
                    .filter(o -> r.getOrder() == null || !o.getId().equals(r.getOrder().getId()))
                    .ifPresent(o -> messages.add(new Message("W", "Bestellnummer " + e.customerReference()
                            + " wurde bereits im Auftrag " + o.getOrderNumber() + " verwendet – mögliche Doppelbestellung")));
        }
        if (e.deliveryAddress() != null && !e.deliveryAddress().isBlank()) {
            messages.add(new Message("I", "Abweichende Lieferanschrift: " + e.deliveryAddress()));
        }
        if (confidence == Confidence.HIGH && messages.stream().noneMatch(m -> m.type().equals("E") || m.type().equals("W"))) {
            messages.add(new Message("S", "Auftraggeber und alle Positionen eindeutig zugeordnet"));
        }
        return messages;
    }

    private static Confidence overall(CustomerProposal customer, List<ItemProposal> items) {
        Confidence worst = customer == null || customer.match() == null ? Confidence.NONE : customer.match().confidence();
        if (items.isEmpty()) return Confidence.NONE;
        for (ItemProposal item : items) {
            Confidence c = item.match() == null ? Confidence.NONE : item.match().confidence();
            if (c.ordinal() > worst.ordinal()) worst = c;
        }
        return worst;
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    private record MasterData(List<Customer> customers, List<Product> products) {
    }

    private MasterData masterData() {
        return new MasterData(customerRepository.findAll().stream().filter(c -> Boolean.TRUE.equals(c.getActive())).toList(),
                activeProducts());
    }

    private List<Product> activeProducts() {
        return productRepository.findByActiveTrue().stream().filter(p -> p.getMaterialNumber() != null).toList();
    }

    private SalesOrderRequest load(Long id) {
        return repository.findWithOrderById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Auftragseingang " + id + " nicht gefunden"));
    }

    private static void requireOpen(SalesOrderRequest request) {
        if (request.getStatus() != SalesOrderRequest.Status.OPEN) {
            throw new BusinessException("ZI", "010", "Auftragseingang " + request.getRequestNumber() + " ist bereits "
                    + request.getStatus().getDescription().toLowerCase(Locale.GERMANY));
        }
    }

    private static Extraction read(SalesOrderRequest r) {
        if (r.getExtraction() == null) return null;
        try {
            return JsonMapper.shared().readValue(r.getExtraction(), Extraction.class);
        } catch (RuntimeException ex) {
            log.warn("Auslesung zu Auftragseingang {} nicht lesbar: {}", r.getRequestNumber(), ex.getMessage());
            return null;
        }
    }

    private static CodeText status(SalesOrderRequest r) {
        return CodeText.of(r.getStatus().name(), r.getStatus().getDescription());
    }

    private static CodeText sourceType(SalesOrderRequest r) {
        return r.getSourceType() == null ? null : CodeText.of(r.getSourceType().name(), r.getSourceType().getDescription());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.strip());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean isPdf(byte[] content) {
        return content.length > 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
    }

    /** Einfache Dekodierung von quoted-printable (häufig in .eml-Dateien), Zeichensatz UTF-8. */
    static String decodeQuotedPrintable(String text) {
        if (!text.matches("(?is).*content-transfer-encoding:\\s*quoted-printable.*")) return text;
        String joined = text.replaceAll("=\\r?\\n", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < joined.length(); i++) {
            char c = joined.charAt(i);
            if (c == '=' && i + 2 < joined.length() && joined.substring(i + 1, i + 3).matches("[0-9A-Fa-f]{2}")) {
                out.write(Integer.parseInt(joined.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                out.write(bytes, 0, bytes.length);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
