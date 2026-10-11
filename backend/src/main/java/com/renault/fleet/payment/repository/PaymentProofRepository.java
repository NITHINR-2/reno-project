package com.renault.fleet.payment.repository;

import com.renault.fleet.payment.entity.PaymentProof;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentProofRepository extends JpaRepository<PaymentProof, Long> {

    Optional<PaymentProof> findByReceiptId(Long receiptId);

    /** Which of these receipts have a proof attached (no file bytes loaded). */
    @Query("select p.receipt.id from PaymentProof p where p.receipt.id in :receiptIds")
    List<Long> findReceiptIdsWithProof(@Param("receiptIds") Collection<Long> receiptIds);
}
