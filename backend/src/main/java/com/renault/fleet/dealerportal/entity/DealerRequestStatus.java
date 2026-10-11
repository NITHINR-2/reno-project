package com.renault.fleet.dealerportal.entity;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.util.EnumSet;
import java.util.Set;

/**
 * The simplified status a dealer sees. HQ-internal steps (qualification, executive approval, a return to Marketing,
 * finance review) collapse into {@link #PROCESSING}. Declared in pipeline order: an order shows its least advanced
 * open line.
 */
public enum DealerRequestStatus {
    IN_REVIEW(EnumSet.of(FleetRequestStatus.PENDING_MARKETING_REVIEW)),
    PROCESSING(EnumSet.of(FleetRequestStatus.MARKETING_QUALIFIED, FleetRequestStatus.PENDING_EXECUTIVE_APPROVAL,
            FleetRequestStatus.RETURNED_TO_MARKETING, FleetRequestStatus.EXECUTIVE_APPROVED,
            FleetRequestStatus.PENDING_FINANCE_CLEARANCE)),
    AWAITING_PAYMENT(EnumSet.of(FleetRequestStatus.QUOTATION_ISSUED, FleetRequestStatus.PAYMENT_PENDING)),
    CONFIRMED(EnumSet.of(FleetRequestStatus.FUNDS_VERIFIED, FleetRequestStatus.FINANCIALLY_CLEARED)),
    IN_PRODUCTION(EnumSet.of(FleetRequestStatus.PRODUCTION_SCHEDULED)),
    DISPATCH_SCHEDULED(EnumSet.of(FleetRequestStatus.DISPATCH_SCHEDULED)),
    DELIVERED(EnumSet.of(FleetRequestStatus.COMPLETED)),
    CANCELLED(EnumSet.of(FleetRequestStatus.CANCELLED));

    private final Set<FleetRequestStatus> internal;

    DealerRequestStatus(Set<FleetRequestStatus> internal) {
        this.internal = internal;
    }

    public Set<FleetRequestStatus> internal() {
        return internal;
    }

    public static DealerRequestStatus of(FleetRequestStatus status) {
        for (DealerRequestStatus s : values()) {
            if (s.internal.contains(status)) {
                return s;
            }
        }
        throw new IllegalStateException("No dealer status for " + status);
    }
}
