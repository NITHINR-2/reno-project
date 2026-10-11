package com.renault.fleet.payment.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.payment.dto.PaymentReceiptDto;
import com.renault.fleet.payment.dto.RecordReceiptRequest;
import com.renault.fleet.payment.entity.PaymentProof;
import com.renault.fleet.payment.service.PaymentProofService;
import com.renault.fleet.payment.service.PaymentReceiptService;
import com.renault.fleet.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Finance: payment receipts")
public class PaymentReceiptController {

    private final PaymentReceiptService service;
    private final PaymentProofService proofs;

    public PaymentReceiptController(PaymentReceiptService service, PaymentProofService proofs) {
        this.service = service;
        this.proofs = proofs;
    }

    @PostMapping("/api/fleet-requests/{id}/payment-receipts")
    @PreAuthorize("hasAuthority('PAYMENT_RECORD')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a deposit or payment receipt (transaction reference must be unique)")
    public PaymentReceiptDto record(@PathVariable Long id, @Valid @RequestBody RecordReceiptRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.record(id, body, principal);
    }

    @GetMapping("/api/fleet-requests/{id}/payment-receipts")
    @PreAuthorize("hasAnyAuthority('PAYMENT_RECORD', 'DEPOSIT_REVIEW')")
    @Operation(summary = "Receipts of a fleet request (newest first)")
    public PageResponse<PaymentReceiptDto> forRequest(@PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.forRequest(id, pageable);
    }

    @GetMapping("/api/payment-receipts/{id}")
    @PreAuthorize("hasAnyAuthority('PAYMENT_RECORD', 'DEPOSIT_REVIEW')")
    @Operation(summary = "Get one receipt")
    public PaymentReceiptDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/api/payment-receipts/{id}/proof")
    @PreAuthorize("hasAnyAuthority('PAYMENT_RECORD', 'DEPOSIT_REVIEW')")
    @Operation(summary = "Download the payment proof a dealer uploaded with this receipt (audited)")
    public ResponseEntity<byte[]> proof(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        PaymentProof proof = proofs.forReceipt(id, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proof.getContentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(proof.getFileName()).build().toString())
                .body(proof.getContent());
    }

    @PostMapping("/api/payment-receipts/{id}/approve")
    @PreAuthorize("hasAuthority('PAYMENT_APPROVE')")
    @Operation(summary = "Approve a recorded receipt (row-locked, exactly once; a repeat is 409)",
            description = "When approved deposits cover the required deposit, the request moves PAYMENT_PENDING → FUNDS_VERIFIED.")
    public PaymentReceiptDto approve(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.approve(id, principal);
    }
}
