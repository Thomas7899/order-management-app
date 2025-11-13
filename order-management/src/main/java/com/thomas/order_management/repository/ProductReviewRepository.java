// order-management/src/main/java/com/thomas/order_management/repository/ProductReviewRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    @Query("SELECT r FROM ProductReview r JOIN FETCH r.product WHERE r.id IN :ids")
    List<ProductReview> findAllByIdWithProduct(@Param("ids") List<Long> ids);


    List<ProductReview> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
