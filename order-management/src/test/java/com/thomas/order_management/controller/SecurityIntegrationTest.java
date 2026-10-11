package com.thomas.order_management.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.thomas.order_management.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Absicherung der REST-API")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + UserService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("user").get("username").asString()).isEqualTo(username);
        return "Bearer " + json.get("token").asString();
    }

    @Test
    @DisplayName("Ohne Anmeldung sind fachliche Endpunkte gesperrt")
    void shouldRejectAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Health-Check ist ohne Anmeldung erreichbar")
    void shouldExposeHealthCheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Demo-Zugänge sind ohne Anmeldung abrufbar")
    void shouldListDemoUsers() throws Exception {
        mockMvc.perform(get("/api/auth/demo-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'MREUTER')]").exists());
    }

    @Test
    @DisplayName("Falsches Kennwort liefert 401 mit Meldungsnummer")
    void shouldRejectWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"MREUTER\",\"password\":\"falsch\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.messageId").value("00 152"));
    }

    @Test
    @DisplayName("Angemeldete Benutzer dürfen lesen")
    void shouldAllowReadAccess() throws Exception {
        String token = login("SWEBER");
        mockMvc.perform(get("/api/orders").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].code").value("CONTROLLING"));
    }

    @Test
    @DisplayName("Schreibzugriffe sind auf die zuständige Rolle beschränkt")
    void shouldEnforceRoles() throws Exception {
        String sales = login("MREUTER");
        mockMvc.perform(post("/api/invoices/1/payment").header("Authorization", sales)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").header("Authorization", sales))
                .andExpect(status().isForbidden());

        String admin = login("ADMIN");
        mockMvc.perform(get("/api/users").header("Authorization", admin))
                .andExpect(status().isOk());
    }
}
