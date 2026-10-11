package com.thomas.order_management.controller;

import tools.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.sales.OrderDto;
import com.thomas.order_management.dto.sales.OrderRequests;
import com.thomas.order_management.model.*;
import com.thomas.order_management.model.StockMovement.MovementType;
import com.thomas.order_management.model.StockMovement.ReferenceType;
import com.thomas.order_management.repository.*;
import com.thomas.order_management.service.StockPostingService;
import com.thomas.order_management.service.UserService;
import com.thomas.order_management.service.sales.SalesOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bewertungen werden außerhalb einer Transaktion in DTOs umgewandelt (open-in-view ist aus).
 * Die Abfragen müssen Material, Debitor und Auftragsposition deshalb mitladen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("REST-API für Bewertungen")
class ReviewControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SalesOrderService orderService;
    @Autowired private StockPostingService stockPostingService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private ProductReviewRepository reviewRepository;

    private Product product;
    private Long orderId;
    private Long orderItemId;
    private Long reviewId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Customer customer = new Customer("Rita", "Rezension", "rita." + suffix + "@example.com");
        customer.setCustomerNumber("R" + suffix.substring(0, 6));
        customer.setPaymentTerms(PaymentTerms.ZB30);
        customer.setDiscountPercent(BigDecimal.ZERO);
        customer = customerRepository.save(customer);

        product = new Product("Bewertetes Material " + suffix, null, new BigDecimal("50.00"), 0);
        product.setMaterialNumber("B" + suffix);
        product = productRepository.save(product);

        Warehouse warehouse = warehouseRepository.save(new Warehouse("B" + suffix.substring(0, 3), "Lager " + suffix));
        stockPostingService.post(new StockPostingService.Posting(MovementType.INITIAL_ENTRY, product, null, warehouse,
                5, LocalDateTime.now().minusDays(1), "Anfangsbestand", ReferenceType.MANUAL, null));

        OrderDto order = orderService.create(new OrderRequests.Create(customer.getId(), LocalDate.now().plusDays(3),
                null, null, null, null, List.of(new OrderRequests.Item(product.getId(), 1, null)), null));
        orderId = order.id();
        orderItemId = order.items().get(0).id();

        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setCustomer(customer);
        review.setOrderItem(orderItemRepository.findById(orderItemId).orElseThrow());
        review.setRating(2);
        review.setComment("Ging nach einer Woche kaputt");
        review.setCreatedAt(LocalDateTime.now());
        reviewId = reviewRepository.save(review).getId();
    }

    private String login() throws Exception {
        return login("SWEBER");
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + UserService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asString();
    }

    @Test
    @DisplayName("Bewertungen je Material enthalten Auftrag und Auftragsposition")
    void shouldListReviewsByProduct() throws Exception {
        mockMvc.perform(get("/api/reviews/product/" + product.getId()).header("Authorization", login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId))
                .andExpect(jsonPath("$[0].orderItemId").value(orderItemId))
                .andExpect(jsonPath("$[0].customerName").value("Rita Rezension"));
    }

    @Test
    @DisplayName("Bewertungen je Auftrag und je Auftragsposition sind abrufbar")
    void shouldListReviewsByOrderAndItem() throws Exception {
        String token = login();
        mockMvc.perform(get("/api/reviews/order/" + orderId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value(product.getName()));
        mockMvc.perform(get("/api/reviews/order-item/" + orderItemId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId));
    }

    @Test
    @DisplayName("Vertrieb beantwortet eine kritische Bewertung mit Vorschlag; Controlling darf nicht antworten")
    void shouldReplyToCriticalReview() throws Exception {
        String sales = login("MREUTER");
        mockMvc.perform(get("/api/reviews/open-critical").header("Authorization", sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[?(@.id == " + reviewId + ")]").exists());

        mockMvc.perform(post("/api/reviews/" + reviewId + "/reply-suggestion").header("Authorization", sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("REGELN"))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString(product.getName())));

        String reply = "{\"text\":\"Guten Tag Rita, das tut uns leid. Bitte melden Sie sich bei unserem Kundenservice.\"}";
        mockMvc.perform(put("/api/reviews/" + reviewId + "/reply").header("Authorization", login())
                        .contentType(MediaType.APPLICATION_JSON).content(reply))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/reviews/" + reviewId + "/reply").header("Authorization", sales)
                        .contentType(MediaType.APPLICATION_JSON).content(reply))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repliedBy").value("MREUTER"))
                .andExpect(jsonPath("$.replyText").value(org.hamcrest.Matchers.startsWith("Guten Tag Rita")));

        mockMvc.perform(get("/api/reviews/open-critical").header("Authorization", sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[?(@.id == " + reviewId + ")]").doesNotExist());
    }

    @Test
    @DisplayName("Qualitätsauffälligkeiten sind für alle angemeldeten Benutzer abrufbar")
    void shouldListQualityAlerts() throws Exception {
        mockMvc.perform(get("/api/reviews/insights/quality-alerts").header("Authorization", login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
