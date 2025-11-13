// order-management/src/main/java/com/thomas/order_management/scheduler/ReviewTrendScheduler.java
package com.thomas.order_management.scheduler;

import com.thomas.order_management.service.ReviewTrendAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewTrendScheduler {

    private final ReviewTrendAnalysisService analysisService;

    /** Tägliche Analyse des Vortags (lokale Zeit) */
    @Scheduled(cron = "0 0 3 * * *")
    public void analyzeYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Running daily review trend analysis for {}", yesterday);
        analysisService.analyze(yesterday, yesterday);
    }
}
