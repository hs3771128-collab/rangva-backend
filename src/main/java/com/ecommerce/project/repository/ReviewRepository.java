package com.ecommerce.project.repository;

import com.ecommerce.project.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProduct_ProductIdOrderByCreatedAtDesc(Long productId);

    boolean existsByProduct_ProductIdAndUser_UserId(Long productId, Long userId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.productId = :productId")
    Double averageRating(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.productId = :productId")
    Long countByProduct(@Param("productId") Long productId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.productId = :productId GROUP BY r.rating")
    List<Object[]> ratingBreakdown(@Param("productId") Long productId);

    void deleteByProduct_ProductId(Long productId);
}
