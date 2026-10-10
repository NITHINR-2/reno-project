package com.renault.fleet.fleetrequest.repository;

import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FleetRequestRepository extends JpaRepository<FleetRequest, Long> {

    @EntityGraph(attributePaths = {"dealer", "variant"})
    Page<FleetRequest> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"dealer", "variant"})
    Page<FleetRequest> findByStatus(FleetRequestStatus status, Pageable pageable);
}
