package com.renault.fleet.dealer.dto;

import com.renault.fleet.dealer.entity.DealerStatus;

/** Query-string filters for {@code GET /api/dealers}; {@code q} matches name or code. */
public record DealerFilter(String q, Long regionId, DealerStatus status) {
}
