package com.renault.fleet.fleetrequest.repository;

import com.renault.fleet.fleetrequest.entity.FleetRequest;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FleetRequestRepository extends JpaRepository<FleetRequest, Long>, JpaSpecificationExecutor<FleetRequest> {

    @Override
    @EntityGraph(attributePaths = {"dealer", "dealer.region", "variant", "variant.model", "plant", "dealerOrder"})
    Page<FleetRequest> findAll(Specification<FleetRequest> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"dealer", "dealer.region", "variant", "variant.model", "plant", "createdBy", "assignedTo"})
    Optional<FleetRequest> findDetailedById(Long id);

    /** {@code SELECT ... FOR UPDATE} on the request row: serialises transitions and stock mapping per request. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FleetRequest r where r.id = :id")
    Optional<FleetRequest> findByIdForUpdate(@Param("id") Long id);

    Optional<FleetRequest> findByRequestNumber(String requestNumber);

    /** Dealer Portal lookup: another dealer's request is indistinguishable from a missing one. */
    @EntityGraph(attributePaths = {"variant", "variant.model", "dealerOrder", "plant"})
    Optional<FleetRequest> findByIdAndDealerId(Long id, Long dealerId);

    /** The lines of the given dealer orders, in line order. */
    @EntityGraph(attributePaths = {"variant", "variant.model", "plant"})
    List<FleetRequest> findByDealerOrderIdInOrderByDealerOrderLineNo(Collection<Long> dealerOrderIds);

    @Query("select r.status as status, count(r) as requests, coalesce(sum(r.productionRequired), 0) as productionUnits"
            + " from FleetRequest r group by r.status")
    List<StatusTotals> totalsByStatus();

    @Query("select r.status as status,"
            + " sum(case when r.slaWarningAt <= :now and r.slaDueAt > :now then 1 else 0 end) as atRisk,"
            + " sum(case when r.slaDueAt <= :now then 1 else 0 end) as breached"
            + " from FleetRequest r where r.slaDueAt is not null group by r.status")
    List<SlaTotals> slaTotalsByStatus(@Param("now") java.time.Instant now);

    /** Open stage clocks that are due and whose breach is not recorded yet (the 5-minute scan). */
    @Query("select r.id from FleetRequest r where r.slaDueAt <= :now and r.slaBreachedAt is null")
    List<Long> findUnrecordedBreaches(@Param("now") java.time.Instant now);

    /**
     * Claims the breach for one stage entry. The row lock and re-checked WHERE make concurrent scanners
     * serialise: exactly one gets 1. Bulk JPQL leaves {@code version} alone, so users' edits don't turn stale.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FleetRequest r set r.slaBreachedAt = :now where r.id = :id and r.slaBreachedAt is null"
            + " and r.slaDueAt <= :now and r.stageEnteredAt = :enteredAt")
    int claimBreach(@Param("id") Long id, @Param("enteredAt") java.time.Instant enteredAt, @Param("now") java.time.Instant now);

    @Query(value = "select nextval('fleet_request_number_seq')", nativeQuery = true)
    long nextRequestNumber();
}
