package com.renault.fleet.dealer.service;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.dealer.dto.RegionDto;
import com.renault.fleet.dealer.repository.RegionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegionService {

    private final RegionRepository regions;

    public RegionService(RegionRepository regions) {
        this.regions = regions;
    }

    @Transactional(readOnly = true)
    public PageResponse<RegionDto> list(Pageable pageable) {
        return PageResponse.from(regions.findAll(pageable),
                r -> new RegionDto(r.getId(), r.getCode(), r.getName(), r.isActive()));
    }
}
