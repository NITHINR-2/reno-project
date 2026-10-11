package com.renault.fleet.catalog.dto;

import com.renault.fleet.catalog.entity.BodyType;
import com.renault.fleet.catalog.entity.FuelType;
import java.math.BigDecimal;
import java.util.List;

public record VehicleModelDto(
        Long id,
        String code,
        String name,
        String vehicleCategory,
        boolean active,
        List<Variant> variants,
        String description,
        BodyType bodyType,
        Integer seatingCapacity,
        String imageUrl,
        Long version) {

    public record Variant(Long id, String code, String name, FuelType fuelType, BigDecimal unitPrice, boolean active,
            Long version) {
    }
}
