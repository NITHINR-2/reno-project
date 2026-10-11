package com.renault.fleet.quotation.repository;

import com.renault.fleet.quotation.entity.Quotation;
import com.renault.fleet.quotation.entity.QuotationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    boolean existsByFleetRequestIdAndStatus(Long fleetRequestId, QuotationStatus status);

    Optional<Quotation> findFirstByFleetRequestIdAndStatus(Long fleetRequestId, QuotationStatus status);

    List<Quotation> findByFleetRequestIdAndStatus(Long fleetRequestId, QuotationStatus status);

    @EntityGraph(attributePaths = {"fleetRequest", "issuedBy", "createdBy"})
    Page<Quotation> findByFleetRequestId(Long fleetRequestId, Pageable pageable);

    @EntityGraph(attributePaths = {"fleetRequest", "issuedBy", "createdBy"})
    Optional<Quotation> findDetailedById(Long id);

    /** Scalar lookup, so the request row can be locked before the quotation enters the persistence context. */
    @Query("select q.fleetRequest.id from Quotation q where q.id = :id")
    Optional<Long> findRequestIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quotation q where q.id = :id")
    Optional<Quotation> findByIdForUpdate(@Param("id") Long id);

    Optional<Quotation> findByVerificationToken(String verificationToken);

    @Query(value = "select nextval('quotation_number_seq')", nativeQuery = true)
    long nextQuotationNumber();
}
