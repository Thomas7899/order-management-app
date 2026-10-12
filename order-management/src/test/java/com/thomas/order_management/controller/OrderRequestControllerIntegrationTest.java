package com.thomas.order_management.controller;

import com.thomas.order_management.model.*;
import com.thomas.order_management.model.StockMovement.MovementType;
import com.thomas.order_management.model.StockMovement.ReferenceType;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.WarehouseRepository;
import com.thomas.order_management.service.StockPostingService;
import com.thomas.order_management.service.UserService;
import com.thomas.order_management.service.sales.intake.OrderRequestSamples;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Auftragseingang über die REST-API: Bestellung erfassen, ohne KI auslesen, zuordnen und als Auftrag übernehmen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("REST-API für den Auftragseingang")
class OrderRequestControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private StockPostingService stockPostingService;
    @Autowired private OrderRequestSamples samples;

    private Customer customer;
    private Product vacuum;
    private Product grill;
    private String suffix;
    /** Namen enthalten keine Ziffern */
    private String nameSuffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 6);
        nameSuffix = suffix.chars().mapToObj(ch -> String.valueOf((char) (Character.isDigit(ch) ? 'k' + (ch - '0') : ch)))
                .collect(java.util.stream.Collectors.joining());
        customer = new Customer("Greta", "Eingang" + nameSuffix, "greta.eingang." + suffix + "@example.com");
        customer.setCustomerNumber("1" + String.format("%05d", Math.abs(suffix.hashCode()) % 100000));
        customer.setCity("Augsburg");
        customer.setPaymentTerms(PaymentTerms.ZB30);
        customer.setDiscountPercent(BigDecimal.ZERO);
        customer = customerRepository.save(customer);

        vacuum = product("Quellsauger Akkusauger " + suffix);
        grill = product("Quellgrill Gasgrill " + suffix);
        Warehouse warehouse = warehouseRepository.save(new Warehouse("E" + suffix.substring(0, 3), "Lager " + suffix));
        for (Product p : new Product[]{vacuum, grill}) {
            stockPostingService.post(new StockPostingService.Posting(MovementType.INITIAL_ENTRY, p, null, warehouse,
                    20, LocalDateTime.now().minusDays(1), "Anfangsbestand", ReferenceType.MANUAL, null));
        }
    }

    private Product product(String name) {
        Product p = new Product(name, null, new BigDecimal("80.00"), 0);
        p.setMaterialNumber("2" + String.format("%05d", Math.abs((name).hashCode()) % 100000));
        p.setCostPrice(new BigDecimal("50.00"));
        return productRepository.save(p);
    }

    @Test
    @DisplayName("Strukturierte E-Mail wird eindeutig zugeordnet und als Auftrag übernommen")
    void shouldConvertStructuredEmail() throws Exception {
        String sales = login("MREUTER");
        String date = LocalDate.now().plusDays(10).toString();
        String text = """
                Guten Tag,
                bitte liefern Sie bis %s:
                %s  %s – 2 Stück
                %s  %s – 1 Stück
                Kundennummer: %s
                Bestellnummer: B-%s
                Viele Grüße
                Greta Eingang%s
                """.formatted(LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                vacuum.getMaterialNumber(), vacuum.getName(), grill.getMaterialNumber(), grill.getName(),
                customer.getCustomerNumber(), suffix, nameSuffix);

        JsonNode created = json(mockMvc.perform(multipart("/api/order-requests").param("text", text)
                        .param("sender", "Greta <" + customer.getEmail() + ">").param("subject", "Bestellung")
                        .header("Authorization", sales))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status.code").value("OPEN"))
                .andExpect(jsonPath("$.sourceType.code").value("EMAIL"))
                .andExpect(jsonPath("$.extractionSource").value("REGELN"))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.customer.match.customerId").value(customer.getId()))
                .andExpect(jsonPath("$.customerReference").value("B-" + suffix))
                .andExpect(jsonPath("$.requestedDeliveryDate").value(date))
                .andExpect(jsonPath("$.items[0].match.productId").value(vacuum.getId()))
                .andExpect(jsonPath("$.items[0].extracted.quantity").value(2))
                .andExpect(jsonPath("$.items[1].match.productId").value(grill.getId())));
        long id = created.get("id").asLong();

        String order = """
                {"customerId": %d, "requestedDeliveryDate": "%s", "customerReference": "B-%s",
                 "items": [{"productId": %d, "quantity": 2}, {"productId": %d, "quantity": 1}]}
                """.formatted(customer.getId(), date, suffix, vacuum.getId(), grill.getId());
        JsonNode orderDto = json(mockMvc.perform(post("/api/order-requests/" + id + "/convert").header("Authorization", sales)
                        .contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2)));
        long orderId = orderDto.get("id").asLong();

        mockMvc.perform(get("/api/order-requests/" + id).header("Authorization", sales))
                .andExpect(jsonPath("$.status.code").value("CONVERTED"))
                .andExpect(jsonPath("$.orderNumber").value(orderDto.get("orderNumber").asString()))
                .andExpect(jsonPath("$.processedBy").value("MREUTER"));
        mockMvc.perform(get("/api/orders/" + orderId + "/document-flow").header("Authorization", sales))
                .andExpect(jsonPath("$.nodes[0].documentType").value("SALES_ORDER_REQUEST"))
                .andExpect(jsonPath("$.nodes[1].documentType").value("SALES_ORDER"))
                .andExpect(jsonPath("$.nodes[1].level").value(1));

        // Zweite Übernahme ist nicht möglich, und dieselbe Bestellnummer wird als mögliche Dublette gemeldet
        mockMvc.perform(post("/api/order-requests/" + id + "/convert").header("Authorization", sales)
                        .contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(multipart("/api/order-requests").param("text", text).header("Authorization", sales))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages[*].text", hasItem(org.hamcrest.Matchers.containsString("mögliche Doppelbestellung"))));
    }

    @Test
    @DisplayName("Formlose E-Mail: Zuordnungen zum Prüfen, unbekannter Artikel als Fehler, Ablehnen möglich")
    void shouldFlagUncertainMatches() throws Exception {
        String sales = login("MREUTER");
        String text = """
                Hallo,
                wir bräuchten nächste Woche Mittwoch:
                - 3x Quellsauger Akkusauger %s
                - 2 Stück Gasgrill
                - 5 Kartons Kopierpapier
                Grüße
                Greta Eingang%s
                """.formatted(suffix, nameSuffix);
        JsonNode created = json(mockMvc.perform(multipart("/api/order-requests").param("text", text)
                        .header("Authorization", sales))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType.code").value("TEXT"))
                .andExpect(jsonPath("$.customer.match.customerId").value(customer.getId()))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].match.productId").value(vacuum.getId()))
                .andExpect(jsonPath("$.items[2].match").doesNotExist())
                .andExpect(jsonPath("$.confidence").value("NONE")));
        assertThat(created.get("requestedDeliveryDate").asString()).isNotBlank();
        assertThat(created.get("messages").toString()).contains("kein passendes Material", "Karton");

        mockMvc.perform(post("/api/order-requests/" + created.get("id").asLong() + "/reject").header("Authorization", sales)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Rückfrage beim Kunden\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("REJECTED"));
    }

    @Test
    @DisplayName("PDF-Bestellung: Text wird gelesen, Original bleibt abrufbar")
    void shouldReadPdf() throws Exception {
        String sales = login("MREUTER");
        byte[] pdf = samples.pdf(LocalDate.now());
        JsonNode created = json(mockMvc.perform(multipart("/api/order-requests")
                        .file(new MockMultipartFile("file", "bestellung.pdf", "application/pdf", pdf))
                        .header("Authorization", sales))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType.code").value("PDF"))
                .andExpect(jsonPath("$.fileName").value("bestellung.pdf"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].match.confidence").value("HIGH"))
                .andExpect(jsonPath("$.customer.match.confidence").value("HIGH"))
                .andExpect(jsonPath("$.customerReference").exists())
                .andExpect(jsonPath("$.requestedDeliveryDate").exists()));

        mockMvc.perform(get("/api/order-requests/" + created.get("id").asLong() + "/file").header("Authorization", sales))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mockMvc.perform(get("/api/order-requests").param("status", "OPEN").header("Authorization", sales))
                .andExpect(jsonPath("$[*].requestNumber", hasItem(created.get("requestNumber").asString())));
    }

    @Test
    @DisplayName("Nur Vertrieb und Administration dürfen Bestellungen erfassen; Beispiele kann jeder abrufen")
    void shouldRestrictCreation() throws Exception {
        String controlling = login("SWEBER");
        mockMvc.perform(multipart("/api/order-requests").param("text", "2x Gasgrill").header("Authorization", controlling))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/order-requests/samples").header("Authorization", controlling))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/order-requests/samples/" + OrderRequestSamples.STRUCTURED).header("Authorization", controlling))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("Kundennummer")));
        mockMvc.perform(multipart("/api/order-requests").param("text", " ").header("Authorization", login("MREUTER")))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + UserService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asString();
    }
}
