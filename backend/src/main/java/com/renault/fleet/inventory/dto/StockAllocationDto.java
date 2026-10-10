package com.renault.fleet.inventory.dto;

import com.renault.fleet.inventory.entity.AllocationStatus;
import java.time.Instant;

public record StockAllocationDto(
        Long id,
        Long stockId,
        Long locationId,
        String locationCode,
        String locationName,
        int quantity,
        AllocationStatus status,
        String allocatedBy,
        Instant createdAt) {
}
