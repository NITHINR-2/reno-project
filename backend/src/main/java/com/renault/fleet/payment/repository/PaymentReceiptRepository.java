package com.renault.fleet.payment.repository;

import com.renault.fleet.payment.entity.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {
}
