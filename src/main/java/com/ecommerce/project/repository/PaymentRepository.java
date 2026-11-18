package com.ecommerce.project.repository;


import com.ecommerce.project.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>{

    /** Used to match a Safepay tracker back to its payment/order. */
    Optional<Payment> findFirstByPgPaymentId(String pgPaymentId);
}
