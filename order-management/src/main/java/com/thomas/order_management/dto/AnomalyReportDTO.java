// order-management/src/main/java/com/thomas/order_management/dto/AnomalyReportDTO.java
package com.thomas.order_management.dto;

import java.time.LocalDate;
import java.util.List;

public class AnomalyReportDTO {
    public List<String> anomalies;
    public LocalDate windowStart;
    public LocalDate windowEnd;
}
