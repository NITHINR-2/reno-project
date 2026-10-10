package com.renault.fleet.common.persistence;

import com.renault.fleet.common.error.ConflictException;

/** Explicit optimistic-lock check for a client-supplied version (on top of Hibernate's {@code @Version}). */
public final class Versions {

    private Versions() {
    }

    /** No-op when {@code expected} is null; 409 when the entity has moved on. */
    public static void check(BaseEntity entity, Long expected) {
        if (expected != null && !expected.equals(entity.getVersion())) {
            throw new ConflictException("The record was changed by someone else (version " + entity.getVersion()
                    + ", you sent " + expected + "). Reload it and try again.");
        }
    }
}
