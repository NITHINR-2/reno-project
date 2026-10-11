package com.renault.fleet.catalog.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.catalog.dto.ColourRequest;
import com.renault.fleet.catalog.dto.CreateVehicleModelRequest;
import com.renault.fleet.catalog.dto.UpdateColourRequest;
import com.renault.fleet.catalog.dto.CreateVehicleVariantRequest;
import com.renault.fleet.catalog.dto.UpdateVehicleModelRequest;
import com.renault.fleet.catalog.dto.UpdateVehicleVariantRequest;
import com.renault.fleet.catalog.dto.VehicleModelDto;
import com.renault.fleet.catalog.entity.VehicleColour;
import com.renault.fleet.catalog.entity.VehicleModel;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.catalog.mapper.VehicleModelMapper;
import com.renault.fleet.catalog.repository.VehicleColourRepository;
import com.renault.fleet.catalog.repository.VehicleModelRepository;
import com.renault.fleet.catalog.repository.VehicleVariantRepository;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.common.persistence.Versions;
import com.renault.fleet.security.AuthenticatedUser;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Catalogue maintenance (ADMIN, SUPERVISOR). Models and variants are never deleted, because fleet requests and
 * quotations reference them; {@code active=false} takes them off sale. Every change is audited.
 */
@Service
public class CatalogueAdminService {

    private final VehicleModelRepository models;
    private final VehicleVariantRepository variants;
    private final VehicleColourRepository colours;
    private final VehicleModelMapper mapper;
    private final AuditService audit;
    private final JsonMapper jsonMapper;

    public CatalogueAdminService(VehicleModelRepository models, VehicleVariantRepository variants,
            VehicleColourRepository colours, VehicleModelMapper mapper, AuditService audit, JsonMapper jsonMapper) {
        this.models = models;
        this.variants = variants;
        this.colours = colours;
        this.mapper = mapper;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public VehicleModelDto createModel(CreateVehicleModelRequest body, AuthenticatedUser actor) {
        if (models.findByCode(body.code()).isPresent()) {
            throw new ConflictException("Model code " + body.code() + " is already in use.");
        }
        VehicleModel model = new VehicleModel(body.code(), body.name().strip(), body.vehicleCategory());
        model.setDescription(blankToNull(body.description()));
        model.setBodyType(body.bodyType());
        model.setSeatingCapacity(body.seatingCapacity());
        model.setImageUrl(body.imageUrl());
        models.saveAndFlush(model);
        VehicleModelDto dto = mapper.toDto(model, List.of(), List.of());
        audit.record(AuditEvent.of("CREATE", "VEHICLE_MODEL", String.valueOf(model.getId()))
                .withActor(actor).withChange(null, json(dto)));
        return dto;
    }

    @Transactional
    public VehicleModelDto updateModel(Long id, UpdateVehicleModelRequest body, AuthenticatedUser actor) {
        VehicleModel model = model(id);
        Versions.check(model, body.version());
        String before = json(dto(model));
        if (body.name() != null) model.setName(body.name().strip());
        if (body.vehicleCategory() != null) model.setVehicleCategory(body.vehicleCategory());
        if (body.description() != null) model.setDescription(blankToNull(body.description()));
        if (body.bodyType() != null) model.setBodyType(body.bodyType());
        if (body.seatingCapacity() != null) model.setSeatingCapacity(body.seatingCapacity());
        if (body.imageUrl() != null) model.setImageUrl(body.imageUrl());
        if (body.active() != null) model.setActive(body.active());
        models.saveAndFlush(model);
        VehicleModelDto dto = dto(model);
        audit.record(AuditEvent.of("UPDATE", "VEHICLE_MODEL", String.valueOf(id))
                .withActor(actor).withChange(before, json(dto)));
        return dto;
    }

    @Transactional
    public VehicleModelDto.Variant createVariant(Long modelId, CreateVehicleVariantRequest body, AuthenticatedUser actor) {
        VehicleModel model = model(modelId);
        if (variants.findByCode(body.code()).isPresent()) {
            throw new ConflictException("Variant code " + body.code() + " is already in use.");
        }
        VehicleVariant newVariant = new VehicleVariant(model, body.code(), body.name().strip(), body.fuelType(),
                body.unitPrice());
        if (body.transmission() != null) newVariant.setTransmission(body.transmission());
        VehicleVariant variant = variants.saveAndFlush(newVariant);
        VehicleModelDto.Variant dto = VehicleModelMapper.toVariant(variant);
        audit.record(AuditEvent.of("CREATE", "VEHICLE_VARIANT", String.valueOf(variant.getId()))
                .withActor(actor).withChange(null, json(dto)));
        return dto;
    }

    @Transactional
    public VehicleModelDto.Variant updateVariant(Long id, UpdateVehicleVariantRequest body, AuthenticatedUser actor) {
        VehicleVariant variant = variants.findById(id)
                .orElseThrow(() -> new NotFoundException("Vehicle variant " + id + " not found."));
        Versions.check(variant, body.version());
        String before = json(VehicleModelMapper.toVariant(variant));
        if (body.name() != null) variant.setName(body.name().strip());
        if (body.unitPrice() != null) variant.setUnitPrice(body.unitPrice());
        if (body.active() != null) variant.setActive(body.active());
        if (body.transmission() != null) variant.setTransmission(body.transmission());
        variants.saveAndFlush(variant);
        VehicleModelDto.Variant dto = VehicleModelMapper.toVariant(variant);
        audit.record(AuditEvent.of("UPDATE", "VEHICLE_VARIANT", String.valueOf(id))
                .withActor(actor).withChange(before, json(dto)));
        return dto;
    }

    @Transactional
    public VehicleModelDto.Colour createColour(Long modelId, ColourRequest body, AuthenticatedUser actor) {
        VehicleModel model = model(modelId);
        String name = body.name().strip();
        if (colours.nameTaken(modelId, name, null)) {
            throw new ConflictException("Model " + model.getCode() + " already has a colour named " + name + ".");
        }
        VehicleColour colour = colours.saveAndFlush(new VehicleColour(model, name, body.hexCode().toUpperCase(Locale.ROOT)));
        VehicleModelDto.Colour dto = VehicleModelMapper.toColour(colour);
        audit.record(AuditEvent.of("CREATE", "VEHICLE_COLOUR", String.valueOf(colour.getId()))
                .withActor(actor).withChange(null, json(dto)));
        return dto;
    }

    @Transactional
    public VehicleModelDto.Colour updateColour(Long id, UpdateColourRequest body, AuthenticatedUser actor) {
        VehicleColour colour = colours.findById(id)
                .orElseThrow(() -> new NotFoundException("Colour " + id + " not found."));
        Versions.check(colour, body.version());
        String before = json(VehicleModelMapper.toColour(colour));
        if (body.name() != null) {
            String name = body.name().strip();
            if (colours.nameTaken(colour.getModel().getId(), name, id)) {
                throw new ConflictException("This model already has a colour named " + name + ".");
            }
            colour.setName(name);
        }
        if (body.hexCode() != null) colour.setHexCode(body.hexCode().toUpperCase(Locale.ROOT));
        if (body.active() != null) colour.setActive(body.active());
        colours.saveAndFlush(colour);
        VehicleModelDto.Colour dto = VehicleModelMapper.toColour(colour);
        audit.record(AuditEvent.of("UPDATE", "VEHICLE_COLOUR", String.valueOf(id))
                .withActor(actor).withChange(before, json(dto)));
        return dto;
    }

    private VehicleModel model(Long id) {
        return models.findById(id).orElseThrow(() -> new NotFoundException("Vehicle model " + id + " not found."));
    }

    private VehicleModelDto dto(VehicleModel model) {
        return mapper.toDto(model, variants.findByModelIdInOrderByCode(List.of(model.getId())),
                colours.findByModelIdInOrderByName(List.of(model.getId())));
    }

    private String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
