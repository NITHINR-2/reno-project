package com.renault.fleet.catalog.mapper;

import com.renault.fleet.catalog.dto.VehicleModelDto;
import com.renault.fleet.catalog.entity.VehicleModel;
import com.renault.fleet.catalog.entity.VehicleVariant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VehicleModelMapper {

    public VehicleModelDto toDto(VehicleModel model, List<VehicleVariant> variants) {
        return new VehicleModelDto(model.getId(), model.getCode(), model.getName(), model.getVehicleCategory(),
                model.isActive(), variants.stream().map(VehicleModelMapper::toVariant).toList());
    }

    private static VehicleModelDto.Variant toVariant(VehicleVariant v) {
        return new VehicleModelDto.Variant(v.getId(), v.getCode(), v.getName(), v.getFuelType(), v.getUnitPrice(),
                v.isActive());
    }
}
