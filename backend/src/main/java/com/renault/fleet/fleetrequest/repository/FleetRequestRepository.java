package com.renault.fleet.fleetrequest.repository;

import com.renault.fleet.fleetrequest.entity.FleetRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FleetRequestRepository extends JpaRepository<FleetRequest, Long>, JpaSpecificationExecutor<FleetRequest> {

    @Override
    @EntityGraph(attributePaths = {"dealer", "dealer.region", "variant", "variant.model", "plant"})
    Page<FleetRequest> findAll(Specification<FleetRequest> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"dealer", "dealer.region", "variant", "variant.model", "plant", "createdBy", "assignedTo"})
    Optional<FleetRequest> findDetailedById(Long id);

    /** {@code SELECT ... FOR UPDATE} on the request row: serialises transitions and stock mapping per request. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FleetRequest r where r.id = :id")
    Optional<FleetRequest> findByIdForUpdate(@Param("id") Long id);

    Optional<FleetRequest> findByRequestNumber(String requestNumber);

    @Query("select r.status as status, count(r) as requests, coalesce(sum(r.productionRequired), 0) as productionUnits"
            + " from FleetRequest r group by r.status")
    List<StatusTotals> totalsByStatus();

    @Query(value = "select nextval('fleet_request_number_seq')", nativeQuery = true)
    long nextRequestNumber();
}
