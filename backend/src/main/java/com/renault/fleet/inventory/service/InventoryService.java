package com.renault.fleet.inventory.service;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.inventory.dto.InventoryLocationDto;
import com.renault.fleet.inventory.dto.InventoryLocationFilter;
import com.renault.fleet.inventory.dto.InventoryStockDto;
import com.renault.fleet.inventory.dto.InventoryStockFilter;
import com.renault.fleet.inventory.entity.InventoryLocation;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.mapper.InventoryMapper;
import com.renault.fleet.inventory.repository.InventoryLocationRepository;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of inventory: locations, stock levels and the stock eligible for a fleet request. */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryLocationRepository locations;
    private final InventoryStockRepository stock;
    private final FleetRequestRepository requests;
    private final InventoryMapper mapper;

    public InventoryService(InventoryLocationRepository locations, InventoryStockRepository stock,
            FleetRequestRepository requests, InventoryMapper mapper) {
        this.locations = locations;
        this.stock = stock;
        this.requests = requests;
        this.mapper = mapper;
    }

    public PageResponse<InventoryLocationDto> locations(InventoryLocationFilter f, Pageable pageable) {
        List<Specification<InventoryLocation>> specs = new ArrayList<>();
        if (f.type() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("locationType"), f.type()));
        }
        if (f.regionId() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("region").get("id"), f.regionId()));
        }
        if (f.plantId() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("plant").get("id"), f.plantId()));
        }
        if (f.active() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("active"), f.active()));
        }
        return PageResponse.from(locations.findAll(Specification.allOf(specs), pageable), mapper::toLocation);
    }

    public PageResponse<InventoryStockDto> stock(InventoryStockFilter f, Pageable pageable) {
        List<Specification<InventoryStock>> specs = new ArrayList<>();
        if (f.variantId() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("variant").get("id"), f.variantId()));
        }
        if (f.modelId() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("variant").get("model").get("id"), f.modelId()));
        }
        if (f.locationId() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("location").get("id"), f.locationId()));
        }
        if (f.locationType() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("location").get("locationType"), f.locationType()));
        }
        if (Boolean.TRUE.equals(f.availableOnly())) {
            specs.add((root, q, cb) -> cb.greaterThan(root.get("quantityAvailable"), 0));
        }
        if (Boolean.TRUE.equals(f.activeLocationsOnly())) {
            specs.add((root, q, cb) -> cb.isTrue(root.get("location").get("active")));
        }
        return PageResponse.from(stock.findAll(Specification.allOf(specs), pageable), mapper::toStock);
    }

    /** Stock that may be mapped to the request: same variant, active location, units available. */
    public PageResponse<InventoryStockDto> stockOptions(Long requestId, Pageable pageable) {
        Long variantId = requests.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Fleet request " + requestId + " not found."))
                .getVariant().getId();
        return stock(new InventoryStockFilter(variantId, null, null, null, true, true), pageable);
    }
}
