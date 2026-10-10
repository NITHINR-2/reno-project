package com.renault.fleet.fleetrequest.dto;

import jakarta.validation.constraints.Size;

/** Optional body for workflow actions: a note/reason and the version the client last saw. */
public record TransitionRequest(@Size(max = 1000) String reason, Long version) {

    public static final TransitionRequest EMPTY = new TransitionRequest(null, null);
}
