package com.renault.fleet.fleetrequest.repository;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;

/** One row of the per-status aggregate behind the dashboard summary. */
public interface StatusTotals {
    FleetRequestStatus getStatus();

    long getRequests();

    long getProductionUnits();
}
