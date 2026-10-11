package com.renault.fleet.sla.service;

import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.mapper.FleetRequestAuditMapper;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.workflow.service.WorkflowHistoryService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an {@code SLA_BREACHED} workflow event the first time a stage clock runs out. A system event, so it
 * has no actor and no audit_logs row (it changes nothing a user did); it never changes the status.
 */
@Service
public class SlaBreachService {

    public static final String SLA_BREACHED = "SLA_BREACHED";

    private final FleetRequestRepository requests;
    private final WorkflowHistoryService history;
    private final FleetRequestAuditMapper json;
    private final Clock clock;

    public SlaBreachService(FleetRequestRepository requests, WorkflowHistoryService history, FleetRequestAuditMapper json,
            Clock clock) {
        this.requests = requests;
        this.history = history;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Long> dueIds() {
        return requests.findUnrecordedBreaches(clock.instant());
    }

    /** Returns whether this call recorded the breach; false when nothing is due or another scan got there first. */
    @Transactional
    public boolean recordIfBreached(Long requestId) {
        Instant now = clock.instant();
        FleetRequest request = requests.findById(requestId).orElse(null);
        if (request == null || request.getSlaDueAt() == null || request.getSlaBreachedAt() != null
                || now.isBefore(request.getSlaDueAt())) {
            return false;
        }
        Instant enteredAt = request.getStageEnteredAt();
        Instant dueAt = request.getSlaDueAt();
        var stage = request.getStatus();
        if (requests.claimBreach(requestId, enteredAt, now) != 1) {
            return false;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        // Instant.toString() is stable, which the ux_workflow_history_sla_breach index relies on.
        metadata.put("stageEnteredAt", enteredAt.toString());
        metadata.put("dueAt", dueAt.toString());
        metadata.put("detectedAt", now.toString());
        history.record(requests.getReferenceById(requestId), stage, stage, SLA_BREACHED, null, null, json.toJson(metadata));
        return true;
    }
}
