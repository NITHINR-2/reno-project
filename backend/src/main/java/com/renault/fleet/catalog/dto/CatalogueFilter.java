package com.renault.fleet.catalog.dto;

import com.renault.fleet.catalog.entity.BodyType;

/** Query-string filters for the dealer catalogue; {@code q} matches name or code. */
public record CatalogueFilter(String q, BodyType bodyType) {
}
