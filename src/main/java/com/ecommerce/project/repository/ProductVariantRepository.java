package com.ecommerce.project.repository;

import com.ecommerce.project.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProduct_ProductIdOrderBySortOrderAsc(Long productId);

    @Query("SELECT v FROM ProductVariant v WHERE v.product.productId = :productId AND v.label = :label")
    ProductVariant findByProductAndLabel(@Param("productId") Long productId, @Param("label") String label);

    @Modifying
    @Query("DELETE FROM ProductVariant v WHERE v.product.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
