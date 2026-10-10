package com.renault.fleet.catalog.dto;

/** Query-string filters for {@code GET /api/vehicle-models}; {@code q} matches name or code. */
public record VehicleModelFilter(String q, String category, Boolean active) {
}
