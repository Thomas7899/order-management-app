package com.thomas.order_management.repository;

import com.thomas.order_management.model.ReviewTrendReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewTrendReportRepository extends JpaRepository<ReviewTrendReport, Long> {
}
