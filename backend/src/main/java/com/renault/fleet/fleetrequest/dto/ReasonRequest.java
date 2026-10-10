package com.renault.fleet.fleetrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for actions that require a reason (reject, cancel). */
public record ReasonRequest(@NotBlank @Size(max = 1000) String reason, Long version) {

    public TransitionRequest toTransition() {
        return new TransitionRequest(reason, version);
    }
}
