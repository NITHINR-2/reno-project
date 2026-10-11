package com.renault.fleet.sla.entity;

/** Computed by the backend from the request's frozen stage clock; null when the stage has no SLA. */
public enum SlaStatus {
    ON_TRACK,
    AT_RISK,
    BREACHED
}
