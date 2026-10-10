package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Konditionszeile einer Auftragsposition (SAP: Preiselemente), z. B. PR00, K007, RA01, MWST.
 */
@Entity
@Table(name = "order_item_conditions")
@Getter
@Setter
@NoArgsConstructor
public class PricingCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "condition_type", nullable = false, length = 4)
    private String conditionType;

    @Column(nullable = false, length = 60)
    private String description;

    /** Konditionsbetrag bzw. Prozentsatz. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal rate;

    /** "EUR" für Beträge je Einheit, "%" für Prozentkonditionen. */
    @Column(name = "rate_unit", nullable = false, length = 3)
    private String rateUnit;

    /** Konditionswert der Position (negativ bei Abschlägen). */
    @Column(name = "condition_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal conditionValue;

    /** Statistische Konditionen (z. B. VPRS) fließen nicht in den Nettowert ein. */
    @Column(nullable = false)
    private boolean statistical;

    public PricingCondition(int stepNumber, String conditionType, String description,
                            BigDecimal rate, String rateUnit, BigDecimal conditionValue, boolean statistical) {
        this.stepNumber = stepNumber;
        this.conditionType = conditionType;
        this.description = description;
        this.rate = rate;
        this.rateUnit = rateUnit;
        this.conditionValue = conditionValue;
        this.statistical = statistical;
    }
}
