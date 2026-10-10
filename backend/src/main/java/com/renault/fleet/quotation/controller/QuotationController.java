package com.renault.fleet.quotation.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.quotation.dto.CalculateQuotationRequest;
import com.renault.fleet.quotation.dto.IssueQuotationRequest;
import com.renault.fleet.quotation.dto.QuotationDto;
import com.renault.fleet.quotation.service.QuotationService;
import com.renault.fleet.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Finance: quotations")
public class QuotationController {

    private final QuotationService service;

    public QuotationController(QuotationService service) {
        this.service = service;
    }

    @PostMapping("/api/fleet-requests/{id}/quotations/calculate")
    @PreAuthorize("hasAuthority('QUOTATION_ISSUE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Calculate a DRAFT GST quotation (base, GST, total, deposit shortfall); replaces earlier drafts",
            description = "Allowed in PENDING_FINANCE_CLEARANCE. Uses the variant's unit price and the GST rate effective "
                    + "today (IST) for the model's vehicle category.")
    public QuotationDto calculate(@PathVariable Long id, @Valid @RequestBody(required = false) CalculateQuotationRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.calculate(id, body, principal);
    }

    @GetMapping("/api/fleet-requests/{id}/quotations")
    @PreAuthorize("hasAuthority('QUOTATION_READ')")
    @Operation(summary = "Quotations of a fleet request (newest first)")
    public PageResponse<QuotationDto> forRequest(@PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.forRequest(id, pageable);
    }

    @GetMapping("/api/quotations/{id}")
    @PreAuthorize("hasAuthority('QUOTATION_READ')")
    @Operation(summary = "Get one quotation with its line breakdown")
    public QuotationDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/api/quotations/{id}/issue")
    @PreAuthorize("hasAuthority('QUOTATION_ISSUE')")
    @Operation(summary = "Issue a DRAFT quotation: request → QUOTATION_ISSUED → PAYMENT_PENDING (issuing is not payment)")
    public QuotationDto issue(@PathVariable Long id, @RequestBody(required = false) IssueQuotationRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.issue(id, body, principal);
    }
}
