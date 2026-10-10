package com.renault.fleet.dispatch.repository;

import com.renault.fleet.dispatch.entity.Dispatch;
import com.renault.fleet.dispatch.entity.DispatchStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DispatchRepository extends JpaRepository<Dispatch, Long> {

    List<Dispatch> findByFleetRequestIdAndStatusIn(Long fleetRequestId, Collection<DispatchStatus> statuses);

    Optional<Dispatch> findFirstByFleetRequestIdOrderByIdDesc(Long fleetRequestId);

    @EntityGraph(attributePaths = {"fromLocation", "scheduledBy"})
    Page<Dispatch> findByFleetRequestId(Long fleetRequestId, Pageable pageable);

    @Query(value = "select nextval('dispatch_number_seq')", nativeQuery = true)
    long nextDispatchNumber();
}
