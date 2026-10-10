package com.renault.fleet.fleetrequest.repository;

import com.renault.fleet.fleetrequest.dto.FleetRequestFilter;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Builds the list filter. Dates are interpreted as IST calendar days (the business timezone). */
public final class FleetRequestSpecifications {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private FleetRequestSpecifications() {
    }

    public static Specification<FleetRequest> from(FleetRequestFilter f) {
        List<Specification<FleetRequest>> specs = new ArrayList<>();
        if (f.status() != null && !f.status().isEmpty()) {
            specs.add((root, query, cb) -> root.get("status").in(f.status()));
        }
        if (f.dealerId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("dealer").get("id"), f.dealerId()));
        }
        if (f.regionId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("dealer").get("region").get("id"), f.regionId()));
        }
        if (f.variantId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("variant").get("id"), f.variantId()));
        }
        if (f.modelId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("variant").get("model").get("id"), f.modelId()));
        }
        if (f.plantId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("plant").get("id"), f.plantId()));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().strip().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("requestNumber")), like, '\\'),
                    cb.like(cb.lower(root.get("dealer").get("name")), like, '\\')));
        }
        if (f.createdFrom() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startOf(f.createdFrom())));
        }
        if (f.createdTo() != null) {
            specs.add((root, query, cb) -> cb.lessThan(root.get("createdAt"), startOf(f.createdTo().plusDays(1))));
        }
        return Specification.allOf(specs);
    }

    private static java.time.Instant startOf(LocalDate day) {
        return day.atStartOfDay(IST).toInstant();
    }
}
