package com.renault.fleet.catalog.service;

import com.renault.fleet.catalog.dto.CatalogueFilter;
import com.renault.fleet.catalog.dto.CatalogueModelDto;
import com.renault.fleet.catalog.dto.VehicleModelDto;
import com.renault.fleet.catalog.dto.VehicleModelFilter;
import com.renault.fleet.catalog.entity.VehicleColour;
import com.renault.fleet.catalog.entity.VehicleModel;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.catalog.mapper.VehicleModelMapper;
import com.renault.fleet.catalog.repository.VehicleColourRepository;
import com.renault.fleet.catalog.repository.VehicleModelRepository;
import com.renault.fleet.catalog.repository.VehicleVariantRepository;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.NotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only vehicle catalogue (models with their variants and colours). */
@Service
@Transactional(readOnly = true)
public class VehicleModelService {

    private final VehicleModelRepository models;
    private final VehicleVariantRepository variants;
    private final VehicleColourRepository colours;
    private final VehicleModelMapper mapper;

    public VehicleModelService(VehicleModelRepository models, VehicleVariantRepository variants,
            VehicleColourRepository colours, VehicleModelMapper mapper) {
        this.models = models;
        this.variants = variants;
        this.colours = colours;
        this.mapper = mapper;
    }

    public PageResponse<VehicleModelDto> list(VehicleModelFilter f, Pageable pageable) {
        List<Specification<VehicleModel>> specs = new ArrayList<>();
        if (f.q() != null && !f.q().isBlank()) {
            specs.add(text(f.q()));
        }
        if (f.category() != null && !f.category().isBlank()) {
            specs.add((root, q, cb) -> cb.equal(root.get("vehicleCategory"), f.category().strip().toUpperCase(Locale.ROOT)));
        }
        if (f.active() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("active"), f.active()));
        }
        Page<VehicleModel> page = models.findAll(Specification.allOf(specs), pageable);
        List<Long> ids = page.getContent().stream().map(VehicleModel::getId).toList();
        // One query each for the page's variants and colours instead of one per model.
        Map<Long, List<VehicleVariant>> variantsByModel = variantsOf(ids);
        Map<Long, List<VehicleColour>> coloursByModel = coloursOf(ids);
        return PageResponse.from(page, m -> mapper.toDto(m, variantsByModel.getOrDefault(m.getId(), List.of()),
                coloursByModel.getOrDefault(m.getId(), List.of())));
    }

    /**
     * Dealer catalogue: active models with at least one active variant matching the fuel, transmission and price
     * filters; only those variants and the model's active colours are returned.
     */
    public PageResponse<CatalogueModelDto> catalogue(CatalogueFilter f, Pageable pageable) {
        if (f.minPrice() != null && f.maxPrice() != null && f.minPrice().compareTo(f.maxPrice()) > 0) {
            throw new BadRequestException("minPrice must not exceed maxPrice.");
        }
        List<Specification<VehicleModel>> specs = new ArrayList<>();
        specs.add((root, q, cb) -> cb.isTrue(root.get("active")));
        specs.add((root, q, cb) -> {
            Subquery<Long> onSale = q.subquery(Long.class);
            Root<VehicleVariant> v = onSale.from(VehicleVariant.class);
            List<Predicate> where = new ArrayList<>(List.of(cb.equal(v.get("model"), root), cb.isTrue(v.get("active"))));
            where.addAll(variantPredicates(f, v, cb));
            onSale.select(v.get("id")).where(where.toArray(Predicate[]::new));
            return cb.exists(onSale);
        });
        if (f.q() != null && !f.q().isBlank()) {
            specs.add(text(f.q()));
        }
        if (f.bodyType() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("bodyType"), f.bodyType()));
        }
        Page<VehicleModel> page = models.findAll(Specification.allOf(specs), pageable);
        List<Long> ids = page.getContent().stream().map(VehicleModel::getId).toList();
        Map<Long, List<VehicleVariant>> variantsByModel = variantsOf(ids);
        Map<Long, List<VehicleColour>> coloursByModel = coloursOf(ids);
        return PageResponse.from(page, m -> mapper.toCatalogue(m, variantsByModel.getOrDefault(m.getId(), List.of()),
                coloursByModel.getOrDefault(m.getId(), List.of()), v -> matches(f, v)));
    }

    public VehicleModelDto get(Long id) {
        VehicleModel model = models.findById(id)
                .orElseThrow(() -> new NotFoundException("Vehicle model " + id + " not found."));
        return mapper.toDto(model, variants.findByModelIdInOrderByCode(List.of(id)),
                colours.findByModelIdInOrderByName(List.of(id)));
    }

    private static List<Predicate> variantPredicates(CatalogueFilter f, Root<VehicleVariant> v, CriteriaBuilder cb) {
        List<Predicate> where = new ArrayList<>();
        if (f.fuelType() != null) where.add(cb.equal(v.get("fuelType"), f.fuelType()));
        if (f.transmission() != null) where.add(cb.equal(v.get("transmission"), f.transmission()));
        if (f.minPrice() != null) where.add(cb.greaterThanOrEqualTo(v.get("unitPrice"), f.minPrice()));
        if (f.maxPrice() != null) where.add(cb.lessThanOrEqualTo(v.get("unitPrice"), f.maxPrice()));
        return where;
    }

    /** The same variant filter as {@link #variantPredicates}, applied in memory to the page's variants. */
    private static boolean matches(CatalogueFilter f, VehicleVariant v) {
        return (f.fuelType() == null || v.getFuelType() == f.fuelType())
                && (f.transmission() == null || v.getTransmission() == f.transmission())
                && (f.minPrice() == null || v.getUnitPrice().compareTo(f.minPrice()) >= 0)
                && (f.maxPrice() == null || v.getUnitPrice().compareTo(f.maxPrice()) <= 0);
    }

    private static Specification<VehicleModel> text(String query) {
        String like = "%" + query.strip().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, q, cb) -> cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
                cb.like(cb.lower(root.get("code")), like, '\\'));
    }

    private Map<Long, List<VehicleVariant>> variantsOf(List<Long> modelIds) {
        return variants.findByModelIdInOrderByCode(modelIds).stream()
                .collect(Collectors.groupingBy(v -> v.getModel().getId()));
    }

    private Map<Long, List<VehicleColour>> coloursOf(List<Long> modelIds) {
        return colours.findByModelIdInOrderByName(modelIds).stream()
                .collect(Collectors.groupingBy(c -> c.getModel().getId()));
    }
}
