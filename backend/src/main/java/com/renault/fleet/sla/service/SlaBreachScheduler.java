package com.renault.fleet.sla.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the breach scan every 5 minutes; off in the test profile, where tests call {@link SlaBreachJob#scan()}. */
@Component
@ConditionalOnProperty(name = "fleet.sla.breach-scan.enabled", havingValue = "true", matchIfMissing = true)
public class SlaBreachScheduler {

    private final SlaBreachJob job;

    public SlaBreachScheduler(SlaBreachJob job) {
        this.job = job;
    }

    @Scheduled(fixedDelayString = "${fleet.sla.breach-scan.interval:PT5M}",
            initialDelayString = "${fleet.sla.breach-scan.initial-delay:PT1M}")
    public void run() {
        job.scan();
    }
}
