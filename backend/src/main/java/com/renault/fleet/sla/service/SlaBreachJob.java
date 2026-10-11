package com.renault.fleet.sla.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Finds stages whose clock ran out and records each breach once; triggered by {@link SlaBreachScheduler}. */
@Component
public class SlaBreachJob {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachJob.class);

    private final SlaBreachService breaches;

    public SlaBreachJob(SlaBreachService breaches) {
        this.breaches = breaches;
    }

    public int scan() {
        int recorded = 0;
        for (Long id : breaches.dueIds()) {
            try {
                // One transaction per request: one failure doesn't undo the others.
                if (breaches.recordIfBreached(id)) {
                    recorded++;
                }
            } catch (DataIntegrityViolationException duplicate) {
                log.info("SLA breach for request {} was already recorded", id);
            }
        }
        if (recorded > 0) {
            log.info("SLA scan recorded {} new breach(es)", recorded);
        }
        return recorded;
    }
}
