// order-management/src/main/java/com/thomas/order_management/controller/AnomalyController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.AnomalyReportDTO;
import com.thomas.order_management.service.ReviewAnomalyService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reviews/trends/anomalies")
public class AnomalyController {

    private final ReviewAnomalyService service;

    public AnomalyController(ReviewAnomalyService service) {
        this.service = service;
    }

    @GetMapping
    public AnomalyReportDTO detect(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end
    ) {
        return service.detectAnomalies(start, end);
    }
}
