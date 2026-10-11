package com.renault.fleet.fleetrequest.repository;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;

/** Per-status count of SLA clocks that are at risk or overdue right now. */
public interface SlaTotals {
    FleetRequestStatus getStatus();

    long getAtRisk();

    long getBreached();
}
