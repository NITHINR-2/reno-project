package com.renault.fleet.fleetrequest.service;

/** {@code productionRequired = max(0, requestedQty - allocatedEligibleStock)}. */
public final class ProductionCalculator {

    private ProductionCalculator() {
    }

    public static int required(int requestedQty, int allocatedEligibleStock) {
        if (requestedQty < 0 || allocatedEligibleStock < 0) {
            throw new IllegalArgumentException("Quantities cannot be negative");
        }
        return Math.max(0, requestedQty - allocatedEligibleStock);
    }
}
