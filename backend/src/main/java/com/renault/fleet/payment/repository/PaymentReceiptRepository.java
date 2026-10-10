package com.renault.fleet.payment.repository;

import com.renault.fleet.payment.entity.PaymentReceipt;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.entity.ReceiptType;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    @Query("""
            select coalesce(sum(p.amount), 0) from PaymentReceipt p
            where p.fleetRequest.id = :requestId and p.receiptType = :type and p.status = :status
            """)
    BigDecimal sumAmount(@Param("requestId") Long requestId, @Param("type") ReceiptType type,
            @Param("status") ReceiptStatus status);

    boolean existsByReceiptNumber(String receiptNumber);
}
