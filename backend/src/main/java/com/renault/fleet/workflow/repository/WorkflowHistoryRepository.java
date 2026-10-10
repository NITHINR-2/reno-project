package com.renault.fleet.workflow.repository;

import com.renault.fleet.workflow.entity.WorkflowHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowHistoryRepository extends JpaRepository<WorkflowHistory, Long> {
}
