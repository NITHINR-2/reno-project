package com.renault.fleet.fleetrequest.service;

import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.CANCELLED;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.COMPLETED;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.DISPATCH_SCHEDULED;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.EXECUTIVE_APPROVED;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.FINANCIALLY_CLEARED;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.FUNDS_VERIFIED;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.MARKETING_QUALIFIED;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.PAYMENT_PENDING;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.PENDING_EXECUTIVE_APPROVAL;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.PENDING_FINANCE_CLEARANCE;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.PENDING_MARKETING_REVIEW;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.PRODUCTION_SCHEDULED;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.QUOTATION_ISSUED;
import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.RETURNED_TO_MARKETING;

import com.renault.fleet.fleetrequest.dto.FleetRequestMetricsDto;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.fleetrequest.repository.StatusTotals;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FleetRequestMetricsService {

    static final Set<FleetRequestStatus> MARKETING =
            EnumSet.of(PENDING_MARKETING_REVIEW, RETURNED_TO_MARKETING, MARKETING_QUALIFIED);
    static final Set<FleetRequestStatus> EXECUTIVE = EnumSet.of(PENDING_EXECUTIVE_APPROVAL, EXECUTIVE_APPROVED);
    static final Set<FleetRequestStatus> FINANCE =
            EnumSet.of(PENDING_FINANCE_CLEARANCE, QUOTATION_ISSUED, PAYMENT_PENDING, FUNDS_VERIFIED);
    static final Set<FleetRequestStatus> OPERATIONS =
            EnumSet.of(FINANCIALLY_CLEARED, PRODUCTION_SCHEDULED, DISPATCH_SCHEDULED);
    static final Set<FleetRequestStatus> TERMINAL = EnumSet.of(COMPLETED, CANCELLED);

    private final FleetRequestRepository repository;

    public FleetRequestMetricsService(FleetRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public FleetRequestMetricsDto summary() {
        Map<FleetRequestStatus, Long> byStatus = new EnumMap<>(FleetRequestStatus.class);
        for (FleetRequestStatus status : FleetRequestStatus.values()) {
            byStatus.put(status, 0L);
        }
        long unitsToProduce = 0;
        for (StatusTotals row : repository.totalsByStatus()) {
            byStatus.put(row.getStatus(), row.getRequests());
            // Units already scheduled for production still count until the request completes.
            if (!TERMINAL.contains(row.getStatus())) {
                unitsToProduce += row.getProductionUnits();
            }
        }
        return new FleetRequestMetricsDto(
                sum(byStatus, EnumSet.complementOf(EnumSet.copyOf(TERMINAL))),
                sum(byStatus, MARKETING),
                sum(byStatus, EXECUTIVE),
                sum(byStatus, FINANCE),
                sum(byStatus, OPERATIONS),
                unitsToProduce,
                byStatus.get(COMPLETED),
                byStatus.get(CANCELLED),
                byStatus);
    }

    private static long sum(Map<FleetRequestStatus, Long> byStatus, Set<FleetRequestStatus> statuses) {
        return statuses.stream().mapToLong(byStatus::get).sum();
    }
}
