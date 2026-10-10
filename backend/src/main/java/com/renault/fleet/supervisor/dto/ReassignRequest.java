package com.renault.fleet.supervisor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReassignRequest(@NotNull Long assigneeId, @NotBlank @Size(max = 1000) String reason, Long version) {
}
