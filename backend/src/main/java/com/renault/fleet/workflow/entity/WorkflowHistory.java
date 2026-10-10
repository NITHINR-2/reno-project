package com.renault.fleet.workflow.entity;

import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only record of every workflow transition or system event (a DB trigger rejects UPDATE/DELETE). */
@Entity
@Immutable
@Table(name = "workflow_history")
public class WorkflowHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fleet_request_id", nullable = false)
    private FleetRequest fleetRequest;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private FleetRequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private FleetRequestStatus toStatus;

    @Column(nullable = false, length = 40)
    private String action;

    /** Null for system events (e.g. SLA_BREACHED raised by the scheduler). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(length = 40)
    private String actorRole;

    @Column(length = 1000)
    private String reason;

    @Column(name = "is_override", nullable = false)
    private boolean override;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkflowHistory() {
    }

    public WorkflowHistory(FleetRequest fleetRequest, FleetRequestStatus fromStatus, FleetRequestStatus toStatus,
            String action, User actor, String actorRole, String reason, boolean override, String metadata) {
        this.fleetRequest = fleetRequest;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.action = action;
        this.actor = actor;
        this.actorRole = actorRole;
        this.reason = reason;
        this.override = override;
        this.metadata = metadata;
    }

    public Long getId() { return id; }
    public FleetRequest getFleetRequest() { return fleetRequest; }
    public FleetRequestStatus getFromStatus() { return fromStatus; }
    public FleetRequestStatus getToStatus() { return toStatus; }
    public String getAction() { return action; }
    public User getActor() { return actor; }
    public String getActorRole() { return actorRole; }
    public String getReason() { return reason; }
    public boolean isOverride() { return override; }
    public String getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
}
