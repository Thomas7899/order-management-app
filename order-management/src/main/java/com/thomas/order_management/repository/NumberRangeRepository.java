package com.thomas.order_management.repository;

import com.thomas.order_management.model.NumberRange;
import com.thomas.order_management.model.NumberRangeObject;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NumberRangeRepository extends JpaRepository<NumberRange, NumberRangeObject> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM NumberRange n WHERE n.rangeObject = :rangeObject")
    Optional<NumberRange> findForUpdate(@Param("rangeObject") NumberRangeObject rangeObject);
}
