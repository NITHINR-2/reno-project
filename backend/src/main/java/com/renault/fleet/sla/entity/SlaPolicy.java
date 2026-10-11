package com.renault.fleet.sla.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Hours allowed in one workflow stage before the request is overdue (V10). */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, unique = true, updatable = false)
    private FleetRequestStatus stage;

    @Column(nullable = false)
    private int hoursAllowed;

    @Column(name = "warning_threshold_pct", nullable = false)
    private int warningThresholdPct = 75;

    @Column(nullable = false)
    private boolean active = true;

    protected SlaPolicy() {
    }

    public SlaPolicy(FleetRequestStatus stage, int hoursAllowed) {
        this.stage = stage;
        this.hoursAllowed = hoursAllowed;
    }

    public FleetRequestStatus getStage() { return stage; }
    public int getHoursAllowed() { return hoursAllowed; }
    public void setHoursAllowed(int hoursAllowed) { this.hoursAllowed = hoursAllowed; }
    public int getWarningThresholdPct() { return warningThresholdPct; }
    public void setWarningThresholdPct(int warningThresholdPct) { this.warningThresholdPct = warningThresholdPct; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
