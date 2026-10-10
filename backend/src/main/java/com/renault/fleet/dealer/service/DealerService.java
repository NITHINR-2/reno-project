package com.renault.fleet.dealer.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.common.persistence.Versions;
import com.renault.fleet.dealer.dto.CreateDealerDto;
import com.renault.fleet.dealer.dto.DealerDto;
import com.renault.fleet.dealer.dto.DealerFilter;
import com.renault.fleet.dealer.dto.UpdateDealerDto;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.dealer.entity.DealerStatus;
import com.renault.fleet.dealer.entity.Region;
import com.renault.fleet.dealer.mapper.DealerMapper;
import com.renault.fleet.dealer.repository.DealerRepository;
import com.renault.fleet.dealer.repository.RegionRepository;
import com.renault.fleet.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Dealer listing and onboarding. Every change is audited with before/after JSON. */
@Service
public class DealerService {

    private final DealerRepository dealers;
    private final RegionRepository regions;
    private final DealerMapper mapper;
    private final AuditService audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public DealerService(DealerRepository dealers, RegionRepository regions, DealerMapper mapper, AuditService audit,
            JsonMapper jsonMapper, Clock clock) {
        this.dealers = dealers;
        this.regions = regions;
        this.mapper = mapper;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<DealerDto> list(DealerFilter f, Pageable pageable) {
        List<Specification<Dealer>> specs = new ArrayList<>();
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().strip().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            specs.add((root, q, cb) -> cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
                    cb.like(cb.lower(root.get("dealerCode")), like, '\\')));
        }
        if (f.regionId() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("region").get("id"), f.regionId()));
        }
        if (f.status() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("status"), f.status()));
        }
        return PageResponse.from(dealers.findAll(Specification.allOf(specs), pageable), mapper::toDto);
    }

    @Transactional(readOnly = true)
    public DealerDto get(Long id) {
        return mapper.toDto(find(id));
    }

    @Transactional
    public DealerDto create(CreateDealerDto dto, AuthenticatedUser actor) {
        if (dealers.existsByDealerCodeIgnoreCase(dto.dealerCode())) {
            throw new ConflictException("Dealer code " + dto.dealerCode() + " is already in use.");
        }
        Dealer dealer = new Dealer(dto.dealerCode(), dto.name().strip(), region(dto.regionId()), dto.city().strip(),
                dto.state().strip());
        dealer.setGstin(dto.gstin());
        dealer.setContactName(dto.contactName());
        dealer.setContactEmail(dto.contactEmail());
        dealer.setContactPhone(dto.contactPhone());
        dealer.setAddressLine(dto.addressLine());
        dealer.setPostalCode(dto.postalCode());
        DealerDto created = mapper.toDto(dealers.saveAndFlush(dealer));
        audit.record(AuditEvent.of("CREATE", "DEALER", String.valueOf(created.id()))
                .withActor(actor).withChange(null, jsonMapper.writeValueAsString(created)));
        return created;
    }

    @Transactional
    public DealerDto update(Long id, UpdateDealerDto dto, AuthenticatedUser actor) {
        Dealer dealer = find(id);
        Versions.check(dealer, dto.version());
        String before = jsonMapper.writeValueAsString(mapper.toDto(dealer));
        if (dto.name() != null) dealer.setName(dto.name().strip());
        if (dto.regionId() != null) dealer.setRegion(region(dto.regionId()));
        if (dto.gstin() != null) dealer.setGstin(dto.gstin());
        if (dto.contactName() != null) dealer.setContactName(dto.contactName());
        if (dto.contactEmail() != null) dealer.setContactEmail(dto.contactEmail());
        if (dto.contactPhone() != null) dealer.setContactPhone(dto.contactPhone());
        if (dto.addressLine() != null) dealer.setAddressLine(dto.addressLine());
        if (dto.city() != null) dealer.setCity(dto.city().strip());
        if (dto.state() != null) dealer.setState(dto.state().strip());
        if (dto.postalCode() != null) dealer.setPostalCode(dto.postalCode());
        if (dto.status() != null && dto.status() != dealer.getStatus()) {
            changeStatus(dealer, dto.status());
        }
        DealerDto updated = mapper.toDto(dealers.saveAndFlush(dealer));
        audit.record(AuditEvent.of("UPDATE", "DEALER", String.valueOf(id))
                .withActor(actor).withChange(before, jsonMapper.writeValueAsString(updated)));
        return updated;
    }

    /** Onboarding is one-way: a dealer can be activated or suspended, never returned to PENDING_ONBOARDING. */
    private void changeStatus(Dealer dealer, DealerStatus target) {
        if (target == DealerStatus.PENDING_ONBOARDING) {
            throw new ConflictException("A dealer cannot be returned to PENDING_ONBOARDING.");
        }
        if (dealer.getOnboardedAt() == null) {
            dealer.setOnboardedAt(Instant.now(clock));
        }
        dealer.setStatus(target);
    }

    private Dealer find(Long id) {
        return dealers.findWithRegionById(id).orElseThrow(() -> new NotFoundException("Dealer " + id + " not found."));
    }

    private Region region(Long regionId) {
        Region region = regions.findById(regionId)
                .orElseThrow(() -> new NotFoundException("Region " + regionId + " not found."));
        if (!region.isActive()) {
            throw new ConflictException("Region " + region.getCode() + " is not active.");
        }
        return region;
    }
}
