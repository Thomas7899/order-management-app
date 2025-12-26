package com.thomas.order_management.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger Konfiguration für die API-Dokumentation.
 * 
 * Die Dokumentation ist verfügbar unter:
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:Order Management API}")
    private String applicationName;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Management System API")
                        .version("1.0.0")
                        .description("""
                                ## 📦 AI-Powered Order Management System
                                
                                Eine moderne RESTful API für das Management von Bestellungen, Kunden und Produkten 
                                mit integrierter **KI-gestützter Analyse** von Kundenbewertungen.
                                
                                ### 🚀 Features
                                - **CRUD-Operationen** für Orders, Customers, Products
                                - **Semantische Suche** in Bewertungen via Vector Embeddings
                                - **KI-Trend-Analyse** durch OpenAI Integration
                                - **Dashboard-Analytics** mit Echtzeit-KPIs
                                - **Inventory Management** mit Stock-Tracking
                                
                                ### 🔐 Authentifizierung
                                Aktuell ist die API für Entwicklungszwecke ohne Authentifizierung zugänglich.
                                
                                ### 📚 Weitere Ressourcen
                                - [GitHub Repository](https://github.com/Thomas7899/order-management-app)
                                """)
                        .contact(new Contact()
                                .name("Thomas Osterlehner")
                                .email("thomas.osterlehner@example.com")
                                .url("https://github.com/Thomas7899"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://order-management-api.herokuapp.com")
                                .description("Production Server")))
                .tags(List.of(
                        new Tag().name("Orders").description("Bestellungsverwaltung - CRUD-Operationen für Bestellungen"),
                        new Tag().name("Customers").description("Kundenverwaltung - CRUD-Operationen für Kunden"),
                        new Tag().name("Products").description("Produktverwaltung - CRUD-Operationen für Produkte"),
                        new Tag().name("Reviews").description("Bewertungen - Kundenfeedback und Produktbewertungen"),
                        new Tag().name("AI Analytics").description("KI-gestützte Analysen - Trends, Sentiment, Anomalien"),
                        new Tag().name("Dashboard").description("Dashboard - KPIs und Statistiken"),
                        new Tag().name("Inventory").description("Lagerverwaltung - Bestandsüberwachung")));
    }
}
