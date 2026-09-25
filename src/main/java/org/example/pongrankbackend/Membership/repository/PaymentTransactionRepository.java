package org.example.pongrankbackend.Membership.repository;

import org.example.pongrankbackend.Membership.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByMercadoPagoPaymentId(String paymentId);
}
