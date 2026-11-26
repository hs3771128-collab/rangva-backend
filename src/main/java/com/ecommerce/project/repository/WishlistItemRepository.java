package com.ecommerce.project.repository;

import com.ecommerce.project.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUser_UserIdAndProduct_ProductId(Long userId, Long productId);

    Optional<WishlistItem> findByUser_UserIdAndProduct_ProductId(Long userId, Long productId);

    void deleteByUser_UserIdAndProduct_ProductId(Long userId, Long productId);

    void deleteByProduct_ProductId(Long productId);
}
