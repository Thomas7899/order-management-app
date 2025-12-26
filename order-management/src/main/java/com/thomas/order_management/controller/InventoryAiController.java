// order-management/src/main/java/com/thomas/order_management/controller/InventoryAiController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.inventory.InventoryAiDto.*;
import com.thomas.order_management.service.InventoryAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller für KI-gestützte Inventory-Analysen.
 */
@RestController
@RequestMapping("/api/inventory/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InventoryAiController {

    private final InventoryAiService inventoryAiService;

    /**
     * Generiert einen vollständigen KI-Report für das Inventar.
     * Enthält Prognosen, Nachbestellempfehlungen, Anomalien und Health Score.
     */
    @GetMapping("/report")
    public ResponseEntity<InventoryAiReport> getFullReport() {
        return ResponseEntity.ok(inventoryAiService.generateFullReport());
    }

    /**
     * Generiert Bestandsprognosen für alle Produkte.
     */
    @GetMapping("/forecasts")
    public ResponseEntity<List<DemandForecast>> getDemandForecasts(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "30") int forecastDays) {
        return ResponseEntity.ok(inventoryAiService.generateDemandForecasts(productId, forecastDays));
    }

    /**
     * Generiert Bestandsprognose für ein spezifisches Produkt.
     */
    @GetMapping("/forecasts/{productId}")
    public ResponseEntity<List<DemandForecast>> getProductForecast(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "30") int forecastDays) {
        return ResponseEntity.ok(inventoryAiService.generateDemandForecasts(productId, forecastDays));
    }

    /**
     * Generiert intelligente Nachbestellungs-Empfehlungen.
     */
    @GetMapping("/reorder-recommendations")
    public ResponseEntity<List<ReorderRecommendation>> getReorderRecommendations() {
        return ResponseEntity.ok(inventoryAiService.generateReorderRecommendations());
    }

    /**
     * Erkennt Anomalien im Lagerbestand.
     */
    @GetMapping("/anomalies")
    public ResponseEntity<List<InventoryAnomaly>> detectAnomalies() {
        return ResponseEntity.ok(inventoryAiService.detectInventoryAnomalies());
    }

    /**
     * Berechnet den Inventory Health Score.
     */
    @GetMapping("/health-score")
    public ResponseEntity<InventoryHealthScore> getHealthScore() {
        return ResponseEntity.ok(inventoryAiService.calculateHealthScore());
    }
}
