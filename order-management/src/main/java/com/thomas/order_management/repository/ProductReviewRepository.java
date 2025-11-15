// order-management/src/main/java/com/thomas/order_management/repository/ProductReviewRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.ProductReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    @Override
    @EntityGraph(attributePaths = {
            "product",
            "customer",
            "orderItem",
            "orderItem.order"
    })
    List<ProductReview> findAllById(Iterable<Long> ids);

    List<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<ProductReview> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Query("""
        SELECT r FROM ProductReview r
        WHERE r.orderItem.order.id = :orderId
        ORDER BY r.createdAt DESC
    """)
    List<ProductReview> findByOrderId(@Param("orderId") Long orderId);

    List<ProductReview> findByOrderItemId(Long orderItemId);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    @Query("""
        SELECT r FROM ProductReview r
        JOIN FETCH r.product
        JOIN FETCH r.customer
        WHERE r.id IN :ids
    """)
    List<ProductReview> findAllByIdWithProductAndCustomer(@Param("ids") List<Long> ids);

    List<ProductReview> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<ProductReview> findByProductIdAndCreatedAtBetween(
            Long productId,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("""
        SELECT r FROM ProductReview r
        WHERE r.product.category = :category
        AND r.createdAt BETWEEN :start AND :end
        ORDER BY r.createdAt DESC
    """)
    List<ProductReview> findByProductCategoryAndCreatedAtBetween(
            @Param("category") String category,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        SELECT AVG(r.rating)
        FROM ProductReview r
        WHERE r.product.id = :productId
    """)
    Double getAverageRatingForProduct(@Param("productId") Long productId);

    Long countByProductId(Long productId);

    Long countByCustomerId(Long customerId);

    List<ProductReview> findByCustomerIdAndProductId(Long customerId, Long productId);
}
