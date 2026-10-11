package com.renault.fleet.catalog.dto;

import com.renault.fleet.catalog.entity.BodyType;
import com.renault.fleet.catalog.entity.FuelType;
import com.renault.fleet.catalog.entity.Transmission;
import java.math.BigDecimal;

/**
 * Query-string filters for the dealer catalogue; {@code q} matches name or code. Fuel, transmission and the price
 * range (ex-GST, inclusive) select variants: a model is listed with only its active variants that match them all.
 */
public record CatalogueFilter(String q, BodyType bodyType, FuelType fuelType, Transmission transmission,
        BigDecimal minPrice, BigDecimal maxPrice) {
}
