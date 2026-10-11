package com.renault.fleet.fleetrequest.repository;

import com.renault.fleet.fleetrequest.dto.FleetRequestFilter;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.sla.entity.SlaStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
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

    public static Specification<FleetRequest> from(FleetRequestFilter f, Instant now) {
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
        if (f.sla() != null && !f.sla().isEmpty()) {
            specs.add((root, query, cb) -> cb.or(f.sla().stream().map(s -> sla(s, root, cb, now)).toArray(Predicate[]::new)));
        }
        return Specification.allOf(specs);
    }

    /** Same boundaries as {@code SlaClock.status}: due ≤ now is BREACHED, warning ≤ now < due is AT_RISK. */
    private static Predicate sla(SlaStatus status, Root<FleetRequest> root, CriteriaBuilder cb, Instant now) {
        Path<Instant> due = root.get("slaDueAt");
        Path<Instant> warning = root.get("slaWarningAt");
        return switch (status) {
            case BREACHED -> cb.lessThanOrEqualTo(due, now);
            case AT_RISK -> cb.and(cb.lessThanOrEqualTo(warning, now), cb.greaterThan(due, now));
            case ON_TRACK -> cb.greaterThan(warning, now);
        };
    }

    private static java.time.Instant startOf(LocalDate day) {
        return day.atStartOfDay(IST).toInstant();
    }
}
