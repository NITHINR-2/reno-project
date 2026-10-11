package com.renault.fleet.dealerportal.dto;

import com.renault.fleet.dealerportal.entity.DealerRequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public final class DealerPortalFilters {

    private DealerPortalFilters() {
    }

    /** {@code q} matches the order number; {@code from}/{@code to} are inclusive IST dates of creation. */
    public record Orders(String q, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    }

    /** {@code q} matches the request number. */
    public record Requests(String q, DealerRequestStatus status) {
    }

    public record Cancel(@NotBlank @Size(max = 1000) String reason, Long version) {
    }
}
