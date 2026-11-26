package com.ecommerce.project.repository;


import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    Double getTotalRevenue();

    /** Orders that contain at least one product belonging to this seller. */
    @Query("SELECT DISTINCT o FROM Order o JOIN o.orderItems oi WHERE oi.product.user = :user")
    Page<Order> findBySeller(@Param("user") User user, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT o.orderId) FROM Order o JOIN o.orderItems oi WHERE oi.product.user = :user")
    long countBySeller(@Param("user") User user);

    /** Revenue of this seller only: ordered price x quantity of their items. */
    @Query("SELECT COALESCE(SUM(oi.orderedProductPrice * oi.quantity), 0) " +
            "FROM OrderItem oi WHERE oi.product.user = :user")
    Double getRevenueBySeller(@Param("user") User user);
}
