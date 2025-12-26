// order-management/src/main/java/com/thomas/order_management/controller/ReportingController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.reporting.AbcAnalysisDto;
import com.thomas.order_management.dto.reporting.KpiDashboardDto;
import com.thomas.order_management.dto.reporting.SalesForecastDto;
import com.thomas.order_management.service.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reporting")
@CrossOrigin(origins = "*")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    /**
     * ABC-Analyse für Produkte nach Umsatz
     * GET /api/reporting/abc/products
     */
    @GetMapping("/abc/products")
    public ResponseEntity<AbcAnalysisDto> getProductAbcAnalysis() {
        return ResponseEntity.ok(reportingService.getProductAbcAnalysis());
    }

    /**
     * ABC-Analyse für Kunden nach Umsatz
     * GET /api/reporting/abc/customers
     */
    @GetMapping("/abc/customers")
    public ResponseEntity<AbcAnalysisDto> getCustomerAbcAnalysis() {
        return ResponseEntity.ok(reportingService.getCustomerAbcAnalysis());
    }

    /**
     * Umsatzprognose
     * GET /api/reporting/forecast?historyMonths=12&forecastMonths=3
     */
    @GetMapping("/forecast")
    public ResponseEntity<SalesForecastDto> getSalesForecast(
            @RequestParam(defaultValue = "12") int historyMonths,
            @RequestParam(defaultValue = "3") int forecastMonths) {
        return ResponseEntity.ok(reportingService.getSalesForecast(historyMonths, forecastMonths));
    }

    /**
     * Erweitertes KPI-Dashboard
     * GET /api/reporting/kpi
     */
    @GetMapping("/kpi")
    public ResponseEntity<KpiDashboardDto> getKpiDashboard() {
        return ResponseEntity.ok(reportingService.getKpiDashboard());
    }
}
