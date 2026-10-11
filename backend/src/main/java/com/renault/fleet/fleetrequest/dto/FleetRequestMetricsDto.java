package com.renault.fleet.fleetrequest.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.util.Map;

/**
 * Dashboard summary, computed in SQL. The stage buckets partition the non-terminal statuses:
 * marketing (pending review, returned, qualified), executive (pending approval, approved),
 * finance (pending clearance, quotation issued, payment pending, funds verified) and
 * operations (cleared, production scheduled, dispatch scheduled).
 */
public record FleetRequestMetricsDto(
        long activeRequests,
        long inMarketing,
        long inExecutiveApproval,
        long inFinance,
        long inOperations,
        long unitsToProduce,
        long completed,
        long cancelled,
        Map<FleetRequestStatus, Long> byStatus,
        long slaAtRisk,
        long slaBreached,
        /** Keyed by stage bucket: marketing, executive, finance, operations. */
        Map<String, SlaCounts> slaByStage) {

    public record SlaCounts(long atRisk, long breached) {
    }
}
