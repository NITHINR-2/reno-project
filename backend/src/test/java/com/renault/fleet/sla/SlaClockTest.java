package com.renault.fleet.sla;

import static org.assertj.core.api.Assertions.assertThat;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.sla.entity.SlaPolicy;
import com.renault.fleet.sla.entity.SlaStatus;
import com.renault.fleet.sla.service.SlaClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class SlaClockTest {

    private static final Instant ENTERED = Instant.parse("2026-10-11T04:30:00Z"); // 10:00 IST

    private static SlaPolicy policy(int hours, int pct) {
        SlaPolicy p = new SlaPolicy(FleetRequestStatus.PENDING_MARKETING_REVIEW, hours);
        p.setWarningThresholdPct(pct);
        return p;
    }

    @ParameterizedTest(name = "{0}h at {1}% warns after {2} min")
    @CsvSource({
            "48, 75, 2160",   // default marketing review: warn at 36h
            "24, 75, 1080",
            "168, 75, 7560",  // payment pending: 126h
            "72, 75, 3240",
            "1, 75, 45",
            "7, 33, 138",     // 420 × 33 / 100 = 138.6 → rounded down to the minute
            "10, 1, 6",
            "10, 99, 594"})
    void warningAndDueAreCalendarOffsetsFromEntry(int hours, int pct, long warnMinutes) {
        SlaClock.Deadlines d = SlaClock.deadlines(ENTERED, policy(hours, pct));
        assertThat(Duration.between(ENTERED, d.warningAt()).toMinutes()).isEqualTo(warnMinutes);
        assertThat(Duration.between(ENTERED, d.dueAt())).isEqualTo(Duration.ofHours(hours));
    }

    @Test
    void statusBoundariesAreInclusiveAtTheWarningAndDueInstants() {
        SlaClock.Deadlines d = SlaClock.deadlines(ENTERED, policy(48, 75));
        Instant warn = d.warningAt();
        Instant due = d.dueAt();

        assertThat(SlaClock.status(warn, due, ENTERED)).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(SlaClock.status(warn, due, warn.minusMillis(1))).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(SlaClock.status(warn, due, warn)).isEqualTo(SlaStatus.AT_RISK);
        assertThat(SlaClock.status(warn, due, due.minusMillis(1))).isEqualTo(SlaStatus.AT_RISK);
        assertThat(SlaClock.status(warn, due, due)).isEqualTo(SlaStatus.BREACHED);
        assertThat(SlaClock.status(warn, due, due.plus(Duration.ofDays(30)))).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void noDueMeansNoSla() {
        assertThat(SlaClock.status(null, null, ENTERED)).isNull();
    }
}
