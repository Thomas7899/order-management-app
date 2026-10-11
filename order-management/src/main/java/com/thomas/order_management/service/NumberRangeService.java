package com.thomas.order_management.service;

import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.model.NumberRange;
import com.thomas.order_management.model.NumberRangeObject;
import com.thomas.order_management.repository.NumberRangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vergibt fortlaufende Belegnummern je Nummernkreisobjekt. Die Zeile des Nummernkreises
 * wird gesperrt, sodass parallele Buchungen keine Nummer doppelt erhalten.
 */
@Service
public class NumberRangeService {

    private final NumberRangeRepository repository;

    public NumberRangeService(NumberRangeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String next(NumberRangeObject rangeObject) {
        NumberRange range = repository.findForUpdate(rangeObject)
                .orElseGet(() -> repository.saveAndFlush(new NumberRange(rangeObject)));

        long next = range.getCurrentNumber() == null ? range.getFromNumber() : range.getCurrentNumber() + 1;
        if (next > range.getToNumber()) {
            throw new BusinessException("NR", "751",
                    "Nummernkreis für " + rangeObject.getDescription() + " ist erschöpft");
        }
        range.setCurrentNumber(next);
        return Long.toString(next);
    }

    @Transactional
    public void reset() {
        repository.deleteAllInBatch();
    }
}
