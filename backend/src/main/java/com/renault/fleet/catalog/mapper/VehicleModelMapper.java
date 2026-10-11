package com.renault.fleet.catalog.mapper;

import com.renault.fleet.catalog.dto.CatalogueModelDto;
import com.renault.fleet.catalog.dto.VehicleModelDto;
import com.renault.fleet.catalog.entity.VehicleColour;
import com.renault.fleet.catalog.entity.VehicleModel;
import com.renault.fleet.catalog.entity.VehicleVariant;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class VehicleModelMapper {

    public VehicleModelDto toDto(VehicleModel model, List<VehicleVariant> variants, List<VehicleColour> colours) {
        return new VehicleModelDto(model.getId(), model.getCode(), model.getName(), model.getVehicleCategory(),
                model.isActive(), variants.stream().map(VehicleModelMapper::toVariant).toList(), model.getDescription(),
                model.getBodyType(), model.getSeatingCapacity(), model.getImageUrl(), model.getVersion(),
                colours.stream().map(VehicleModelMapper::toColour).toList());
    }

    /** Dealer view: only active variants that pass {@code variantFilter} and active colours; no admin fields. */
    public CatalogueModelDto toCatalogue(VehicleModel model, List<VehicleVariant> variants, List<VehicleColour> colours,
            Predicate<VehicleVariant> variantFilter) {
        return new CatalogueModelDto(model.getId(), model.getCode(), model.getName(), model.getDescription(),
                model.getBodyType(), model.getSeatingCapacity(), model.getImageUrl(),
                variants.stream().filter(VehicleVariant::isActive).filter(variantFilter)
                        .map(v -> new CatalogueModelDto.Variant(v.getId(), v.getCode(), v.getName(), v.getFuelType(),
                                v.getTransmission(), v.getUnitPrice()))
                        .toList(),
                colours.stream().filter(VehicleColour::isActive)
                        .map(c -> new CatalogueModelDto.Colour(c.getId(), c.getName(), c.getHexCode()))
                        .toList());
    }

    public static VehicleModelDto.Variant toVariant(VehicleVariant v) {
        return new VehicleModelDto.Variant(v.getId(), v.getCode(), v.getName(), v.getFuelType(), v.getTransmission(),
                v.getUnitPrice(), v.isActive(), v.getVersion());
    }

    public static VehicleModelDto.Colour toColour(VehicleColour c) {
        return new VehicleModelDto.Colour(c.getId(), c.getName(), c.getHexCode(), c.isActive(), c.getVersion());
    }
}
