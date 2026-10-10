package com.renault.fleet.health.dto;

import java.time.Instant;

public record HealthResponse(String status, String database, String version, Instant timestamp) {
}
