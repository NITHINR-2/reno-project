package com.renault.fleet.catalog.service;

import com.renault.fleet.catalog.dto.CatalogueFilter;
import com.renault.fleet.catalog.dto.CatalogueModelDto;
import com.renault.fleet.catalog.dto.VehicleModelDto;
import com.renault.fleet.catalog.dto.VehicleModelFilter;
import com.renault.fleet.catalog.entity.VehicleModel;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.catalog.mapper.VehicleModelMapper;
import com.renault.fleet.catalog.repository.VehicleModelRepository;
import com.renault.fleet.catalog.repository.VehicleVariantRepository;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.NotFoundException;
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

/** Read-only vehicle catalogue (models with their variants). */
@Service
@Transactional(readOnly = true)
public class VehicleModelService {

    private final VehicleModelRepository models;
    private final VehicleVariantRepository variants;
    private final VehicleModelMapper mapper;

    public VehicleModelService(VehicleModelRepository models, VehicleVariantRepository variants, VehicleModelMapper mapper) {
        this.models = models;
        this.variants = variants;
        this.mapper = mapper;
    }

    public PageResponse<VehicleModelDto> list(VehicleModelFilter f, Pageable pageable) {
        List<Specification<VehicleModel>> specs = new ArrayList<>();
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().strip().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            specs.add((root, q, cb) -> cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
                    cb.like(cb.lower(root.get("code")), like, '\\')));
        }
        if (f.category() != null && !f.category().isBlank()) {
            specs.add((root, q, cb) -> cb.equal(root.get("vehicleCategory"), f.category().strip().toUpperCase(Locale.ROOT)));
        }
        if (f.active() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("active"), f.active()));
        }
        Page<VehicleModel> page = models.findAll(Specification.allOf(specs), pageable);
        // One query for all variants on the page instead of one per model.
        Map<Long, List<VehicleVariant>> byModel = variants
                .findByModelIdInOrderByCode(page.getContent().stream().map(VehicleModel::getId).toList())
                .stream().collect(Collectors.groupingBy(v -> v.getModel().getId()));
        return PageResponse.from(page, m -> mapper.toDto(m, byModel.getOrDefault(m.getId(), List.of())));
    }

    /** Dealer catalogue: active models that have at least one active variant; inactive variants are left out. */
    public PageResponse<CatalogueModelDto> catalogue(CatalogueFilter f, Pageable pageable) {
        List<Specification<VehicleModel>> specs = new ArrayList<>();
        specs.add((root, q, cb) -> cb.isTrue(root.get("active")));
        specs.add((root, q, cb) -> {
            Subquery<Long> onSale = q.subquery(Long.class);
            Root<VehicleVariant> v = onSale.from(VehicleVariant.class);
            onSale.select(v.get("id")).where(cb.equal(v.get("model"), root), cb.isTrue(v.get("active")));
            return cb.exists(onSale);
        });
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().strip().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            specs.add((root, q, cb) -> cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
                    cb.like(cb.lower(root.get("code")), like, '\\')));
        }
        if (f.bodyType() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("bodyType"), f.bodyType()));
        }
        Page<VehicleModel> page = models.findAll(Specification.allOf(specs), pageable);
        Map<Long, List<VehicleVariant>> byModel = variants
                .findByModelIdInOrderByCode(page.getContent().stream().map(VehicleModel::getId).toList())
                .stream().collect(Collectors.groupingBy(v -> v.getModel().getId()));
        return PageResponse.from(page, m -> mapper.toCatalogue(m, byModel.getOrDefault(m.getId(), List.of())));
    }

    public VehicleModelDto get(Long id) {
        VehicleModel model = models.findById(id)
                .orElseThrow(() -> new NotFoundException("Vehicle model " + id + " not found."));
        return mapper.toDto(model, variants.findByModelIdInOrderByCode(List.of(id)));
    }
}
