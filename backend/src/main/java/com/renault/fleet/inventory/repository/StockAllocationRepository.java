package com.renault.fleet.inventory.repository;

import com.renault.fleet.inventory.entity.AllocationStatus;
import com.renault.fleet.inventory.entity.StockAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockAllocationRepository extends JpaRepository<StockAllocation, Long> {

    @EntityGraph(attributePaths = {"stock", "stock.location", "allocatedBy"})
    List<StockAllocation> findByFleetRequestIdAndStatusOrderById(Long fleetRequestId, AllocationStatus status);

    /** Stock ids only, so stock rows can be locked before they enter the persistence context. */
    @Query("select a.stock.id from StockAllocation a where a.fleetRequest.id = :requestId and a.status = 'ACTIVE'")
    List<Long> findActiveStockIds(@Param("requestId") Long requestId);

    long countByFleetRequestIdAndStatus(Long fleetRequestId, AllocationStatus status);
}
