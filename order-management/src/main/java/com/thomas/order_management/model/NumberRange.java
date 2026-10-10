package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "number_ranges")
@Getter
@Setter
@NoArgsConstructor
public class NumberRange {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "range_object", length = 30)
    private NumberRangeObject rangeObject;

    @Column(name = "from_number", nullable = false)
    private Long fromNumber;

    @Column(name = "to_number", nullable = false)
    private Long toNumber;

    /** Zuletzt vergebene Nummer; null, solange noch keine vergeben wurde. */
    @Column(name = "current_number")
    private Long currentNumber;

    public NumberRange(NumberRangeObject rangeObject) {
        this.rangeObject = rangeObject;
        this.fromNumber = rangeObject.getFromNumber();
        this.toNumber = rangeObject.getToNumber();
    }
}
