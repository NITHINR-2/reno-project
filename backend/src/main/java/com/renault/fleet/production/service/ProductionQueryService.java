package com.renault.fleet.production.service;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.production.dto.PlantDto;
import com.renault.fleet.production.dto.ProductionOrderDto;
import com.renault.fleet.production.entity.ProductionOrder;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.production.repository.ProductionOrderRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionQueryService {

    private final FleetRequestRepository requests;
    private final ProductionOrderRepository orders;
    private final PlantRepository plants;

    public ProductionQueryService(FleetRequestRepository requests, ProductionOrderRepository orders,
            PlantRepository plants) {
        this.requests = requests;
        this.orders = orders;
        this.plants = plants;
    }

    @Transactional(readOnly = true)
    public PageResponse<PlantDto> plants(Pageable pageable) {
        return PageResponse.from(plants.findAll(pageable),
                p -> new PlantDto(p.getId(), p.getCode(), p.getName(), p.getCity(), p.getState(), p.isActive()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionOrderDto> forRequest(Long requestId, Pageable pageable) {
        if (!requests.existsById(requestId)) {
            throw new NotFoundException("Fleet request " + requestId + " not found.");
        }
        return PageResponse.from(orders.findByFleetRequestId(requestId, pageable), ProductionQueryService::toDto);
    }

    static ProductionOrderDto toDto(ProductionOrder o) {
        return new ProductionOrderDto(o.getId(), o.getProductionNumber(), o.getPlant().getId(), o.getPlant().getName(),
                o.getQuantity(), o.getScheduledStart(), o.getEstimatedCompletion(), o.getCompletedAt(), o.getStatus(),
                o.getScheduledBy() == null ? null : o.getScheduledBy().getFullName(), o.getNotes(), o.getCreatedAt());
    }
}
