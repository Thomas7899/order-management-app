package com.thomas.order_management.controller;

import com.thomas.order_management.dto.DashboardDataDto;
import com.thomas.order_management.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard") 
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardDataDto.Stats> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getDashboardStats());
    }

    @GetMapping("/recent-activity")
    public ResponseEntity<DashboardDataDto.RecentActivity> getRecentActivity() {
        return ResponseEntity.ok(dashboardService.getRecentActivity());
    }
}