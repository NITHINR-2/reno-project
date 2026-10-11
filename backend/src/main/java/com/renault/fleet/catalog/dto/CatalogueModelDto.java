package com.renault.fleet.catalog.dto;

import com.renault.fleet.catalog.entity.BodyType;
import com.renault.fleet.catalog.entity.FuelType;
import com.renault.fleet.catalog.entity.Transmission;
import java.math.BigDecimal;
import java.util.List;

/** The dealer catalogue: an active model with its active variants. Prices are ex-GST list prices. */
public record CatalogueModelDto(
        Long id,
        String code,
        String name,
        String description,
        BodyType bodyType,
        Integer seatingCapacity,
        String imageUrl,
        List<Variant> variants,
        List<Colour> colours) {

    public record Variant(Long id, String code, String name, FuelType fuelType, Transmission transmission,
            BigDecimal unitPrice) {
    }

    public record Colour(Long id, String name, String hexCode) {
    }
}
