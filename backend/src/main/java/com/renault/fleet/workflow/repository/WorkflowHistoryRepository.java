package com.renault.fleet.workflow.repository;

import com.renault.fleet.workflow.entity.WorkflowHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowHistoryRepository extends JpaRepository<WorkflowHistory, Long> {

    @EntityGraph(attributePaths = "actor")
    Page<WorkflowHistory> findByFleetRequestId(Long fleetRequestId, Pageable pageable);

    long countByFleetRequestId(Long fleetRequestId);
}
