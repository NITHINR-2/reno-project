package com.renault.fleet.dealer.mapper;

import com.renault.fleet.dealer.dto.DealerDto;
import com.renault.fleet.dealer.entity.Dealer;
import org.springframework.stereotype.Component;

@Component
public class DealerMapper {

    /** Expects the region to be fetched. */
    public DealerDto toDto(Dealer d) {
        return new DealerDto(d.getId(), d.getDealerCode(), d.getName(), d.getRegion().getId(), d.getRegion().getName(),
                d.getGstin(), d.getContactName(), d.getContactEmail(), d.getContactPhone(), d.getAddressLine(),
                d.getCity(), d.getState(), d.getPostalCode(), d.getStatus(), d.getOnboardedAt(), d.getVersion(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
