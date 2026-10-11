package com.renault.fleet.analytics.controller;

import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto;
import com.renault.fleet.analytics.service.ExecutiveAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Analytics")
public class ExecutiveAnalyticsController {

    private final ExecutiveAnalyticsService service;

    public ExecutiveAnalyticsController(ExecutiveAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/api/analytics/executive")
    @PreAuthorize("hasAuthority('EXECUTIVE_DASHBOARD_READ')")
    @Operation(summary = "Executive dashboard figures for a period (IST days, inclusive), computed in SQL",
            description = "Defaults to the last 30 days. KPIs carry the previous period of equal length for comparison.")
    public ExecutiveAnalyticsDto executive(
            @Parameter(description = "First day (IST); default 29 days before 'to'")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day (IST); default today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Region id (the dealer's region)") @RequestParam(required = false) Long region,
            @Parameter(description = "Plant id") @RequestParam(required = false) Long plant) {
        return service.executive(from, to, region, plant);
    }
}
