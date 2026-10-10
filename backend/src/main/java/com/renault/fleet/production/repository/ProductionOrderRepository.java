package com.renault.fleet.production.repository;

import com.renault.fleet.production.entity.ProductionOrder;
import com.renault.fleet.production.entity.ProductionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

    List<ProductionOrder> findByFleetRequestIdAndStatusIn(Long fleetRequestId, Collection<ProductionStatus> statuses);

    Optional<ProductionOrder> findFirstByFleetRequestIdOrderByIdDesc(Long fleetRequestId);

    @EntityGraph(attributePaths = {"plant", "scheduledBy"})
    Page<ProductionOrder> findByFleetRequestId(Long fleetRequestId, Pageable pageable);

    @Query(value = "select nextval('production_number_seq')", nativeQuery = true)
    long nextProductionNumber();
}
