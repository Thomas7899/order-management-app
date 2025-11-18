// order-management/src/main/java/com/thomas/order_management/dto/AnomalyReportDTO.java
package com.thomas.order_management.dto;

import java.time.LocalDate;
import java.util.List;

public class AnomalyReportDTO {
    // ALT: public List<String> anomalies;
    public List<ProductAnomalyDTO> anomalies; // <-- NEU
    public LocalDate windowStart;
    public LocalDate windowEnd;
}
