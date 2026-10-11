// order-management/src/main/java/com/thomas/order_management/dto/reporting/AbcAnalysisDto.java
package com.thomas.order_management.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbcAnalysisDto {
    
    private List<AbcItem> items;
    private AbcSummary summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AbcItem {
        private Long id;
        private String number;
        private String name;
        private String category;
        private BigDecimal revenue;
        private BigDecimal revenuePercentage;
        private BigDecimal cumulativePercentage;
        private String abcClass; // A, B, or C
        private Integer orderCount;
        private Integer quantitySold;
    }

    /** Als Record, damit die JSON-Namen aClassCount usw. lauten (Lombok-Getter ergäben "aclassCount"). */
    public record AbcSummary(
            int aClassCount,
            int bClassCount,
            int cClassCount,
            BigDecimal aClassRevenue,
            BigDecimal bClassRevenue,
            BigDecimal cClassRevenue,
            BigDecimal aClassPercentage,
            BigDecimal bClassPercentage,
            BigDecimal cClassPercentage
    ) {
    }
}
