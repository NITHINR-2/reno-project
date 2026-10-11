package com.renault.fleet.sla.service;

import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.sla.entity.SlaStatus;
import com.renault.fleet.sla.repository.SlaPolicyRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Starts the stage clock whenever a request enters a status; the status itself is always computed here. */
@Service
public class SlaService {

    private final SlaPolicyRepository policies;
    private final Clock clock;

    public SlaService(SlaPolicyRepository policies, Clock clock) {
        this.policies = policies;
        this.clock = clock;
    }

    /** Freezes entered/warning/due from the active policy of the request's current status (none for terminal or untimed stages). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enterStage(FleetRequest request) {
        var entered = clock.instant();
        var deadlines = request.getStatus().isTerminal() ? null
                : policies.findByStage(request.getStatus())
                        .filter(p -> p.isActive())
                        .map(p -> SlaClock.deadlines(entered, p))
                        .orElse(null);
        request.startStageClock(entered, deadlines == null ? null : deadlines.warningAt(),
                deadlines == null ? null : deadlines.dueAt());
    }

    public SlaStatus statusOf(FleetRequest request) {
        return SlaClock.status(request.getSlaWarningAt(), request.getSlaDueAt(), clock.instant());
    }
}
