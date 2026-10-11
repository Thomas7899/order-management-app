package com.thomas.order_management.controller;

import com.thomas.order_management.dto.inventory.InventoryAnalysisDto.Report;
import com.thomas.order_management.service.InventoryAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/ai")
@RequiredArgsConstructor
@Tag(name = "Bestandsanalyse", description = "Bedarfsprognose, Nachschubvorschläge und Auffälligkeiten")
public class InventoryAiController {

    private final InventoryAnalysisService analysisService;

    @Operation(summary = "Bestandsanalyse (berechnet; Zusammenfassung bei verfügbarer KI vom Sprachmodell)")
    @GetMapping("/report")
    public Report report() {
        return analysisService.analyze();
    }
}
