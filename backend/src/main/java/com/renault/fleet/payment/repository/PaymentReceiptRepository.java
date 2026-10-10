package com.renault.fleet.payment.repository;

import com.renault.fleet.payment.entity.PaymentReceipt;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.entity.ReceiptType;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    @Query("""
            select coalesce(sum(p.amount), 0) from PaymentReceipt p
            where p.fleetRequest.id = :requestId and p.receiptType = :type and p.status = :status
            """)
    BigDecimal sumAmount(@Param("requestId") Long requestId, @Param("type") ReceiptType type,
            @Param("status") ReceiptStatus status);

    @Query("""
            select coalesce(sum(p.amount), 0) from PaymentReceipt p
            where p.fleetRequest.id = :requestId and p.status = :status
            """)
    BigDecimal sumAmount(@Param("requestId") Long requestId, @Param("status") ReceiptStatus status);

    boolean existsByReceiptNumber(String receiptNumber);

    /** Same normalisation as the unique index {@code ux_payment_receipts_txn_ref}. */
    @Query(value = "select exists(select 1 from payment_receipts where upper(trim(transaction_reference)) = upper(trim(:ref)))",
            nativeQuery = true)
    boolean existsByNormalisedReference(@Param("ref") String transactionReference);

    @EntityGraph(attributePaths = {"fleetRequest", "quotation", "recordedBy", "reviewedBy"})
    Page<PaymentReceipt> findByFleetRequestId(Long fleetRequestId, Pageable pageable);

    @EntityGraph(attributePaths = {"fleetRequest", "quotation", "recordedBy", "reviewedBy"})
    Optional<PaymentReceipt> findDetailedById(Long id);

    @Query("select p.fleetRequest.id from PaymentReceipt p where p.id = :id")
    Optional<Long> findRequestIdById(@Param("id") Long id);

    /** {@code SELECT ... FOR UPDATE}: approval is serialised per receipt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentReceipt p where p.id = :id")
    Optional<PaymentReceipt> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "select nextval('receipt_number_seq')", nativeQuery = true)
    long nextReceiptNumber();
}
