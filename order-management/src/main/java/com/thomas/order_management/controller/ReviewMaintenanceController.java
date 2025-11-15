// order-management/src/main/java/com/thomas/order_management/controller/ReviewMaintenanceController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.service.ReviewReembedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reviews/maintenance")
@RequiredArgsConstructor
public class ReviewMaintenanceController {

    private final ReviewReembedService reembedService;

    @PostMapping("/reembed")
    public Map<String, Object> reembedAll() {
        int count = reembedService.reembedAll();
        return Map.of("status", "ok", "reembedded", count);
    }
}
