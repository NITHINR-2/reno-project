package com.renault.fleet.sla.service;

import com.renault.fleet.sla.entity.SlaPolicy;
import com.renault.fleet.sla.entity.SlaStatus;
import java.time.Duration;
import java.time.Instant;

/** Pure SLA arithmetic: calendar time (Asia/Kolkata has no DST, so instant + N hours is N IST hours). */
public final class SlaClock {

    private SlaClock() {
    }

    public record Deadlines(Instant warningAt, Instant dueAt) {
    }

    /** Warning point = entered + hours × threshold%, rounded down to the minute; due = entered + hours. */
    public static Deadlines deadlines(Instant enteredAt, SlaPolicy policy) {
        long minutes = policy.getHoursAllowed() * 60L;
        return new Deadlines(enteredAt.plus(Duration.ofMinutes(minutes * policy.getWarningThresholdPct() / 100)),
                enteredAt.plus(Duration.ofMinutes(minutes)));
    }

    /** BREACHED at or after the due instant, AT_RISK at or after the warning instant, otherwise ON_TRACK. */
    public static SlaStatus status(Instant warningAt, Instant dueAt, Instant now) {
        if (dueAt == null) {
            return null;
        }
        if (!now.isBefore(dueAt)) {
            return SlaStatus.BREACHED;
        }
        return warningAt != null && !now.isBefore(warningAt) ? SlaStatus.AT_RISK : SlaStatus.ON_TRACK;
    }
}
