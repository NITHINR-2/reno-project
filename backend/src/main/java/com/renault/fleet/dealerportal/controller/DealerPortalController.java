package com.renault.fleet.dealerportal.controller;

import com.renault.fleet.catalog.dto.CatalogueFilter;
import com.renault.fleet.catalog.dto.CatalogueModelDto;
import com.renault.fleet.catalog.service.VehicleModelService;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.dealerportal.dto.CreateDealerOrderRequest;
import com.renault.fleet.dealerportal.dto.DealerPortalDtos;
import com.renault.fleet.dealerportal.dto.DealerPortalFilters;
import com.renault.fleet.dealerportal.service.DealerPaymentService;
import com.renault.fleet.dealerportal.service.DealerPortalService;
import com.renault.fleet.payment.dto.RecordReceiptRequest;
import com.renault.fleet.quotation.controller.QuotationDocumentController;
import com.renault.fleet.quotation.dto.QuotationPdf;
import com.renault.fleet.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The Dealer Portal API. Everything is scoped to the signed-in dealer; HQ roles get 403, other dealers' data 404. */
@RestController
@RequestMapping("/api/dealer")
@PreAuthorize("hasAuthority('DEALER_PORTAL')")
@Tag(name = "Dealer Portal")
public class DealerPortalController {

    private final DealerPortalService portal;
    private final DealerPaymentService payments;
    private final VehicleModelService catalogue;

    public DealerPortalController(DealerPortalService portal, DealerPaymentService payments,
            VehicleModelService catalogue) {
        this.portal = portal;
        this.payments = payments;
        this.catalogue = catalogue;
    }

    @GetMapping("/catalogue")
    @Operation(summary = "Active models with their active variants and ex-GST list prices")
    public PageResponse<CatalogueModelDto> catalogue(@ParameterObject CatalogueFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return catalogue.catalogue(filter, pageable);
    }

    @GetMapping("/plants")
    @Operation(summary = "Plants the dealer can order from")
    public java.util.List<DealerPortalDtos.Plant> plants(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return portal.plants(principal);
    }

    @GetMapping("/catalogue/availability")
    @Operation(summary = "Free units per variant and colour at a plant (display only; orders re-check under lock)")
    public ResponseEntity<DealerPortalDtos.PlantAvailability> availability(@RequestParam Long plantId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(portal.availability(plantId, principal));
    }

    @PostMapping("/orders")
    @Operation(summary = "Place an order; each line becomes one fleet request in marketing review")
    public ResponseEntity<DealerPortalDtos.OrderDetail> createOrder(@Valid @RequestBody CreateDealerOrderRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        DealerPortalDtos.OrderDetail created = portal.createOrder(body, principal);
        return ResponseEntity.created(URI.create("/api/dealer/orders/" + created.id())).body(created);
    }

    @GetMapping("/orders")
    @Operation(summary = "List the dealer's orders (filter by order number and IST creation dates)")
    public PageResponse<DealerPortalDtos.OrderSummary> orders(@ParameterObject DealerPortalFilters.Orders filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return portal.listOrders(filter, pageable, principal);
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "One of the dealer's orders with its lines")
    public DealerPortalDtos.OrderDetail order(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return portal.getOrder(id, principal);
    }

    @GetMapping("/requests")
    @Operation(summary = "List the dealer's fleet requests (filter by simplified status and request number)")
    public PageResponse<DealerPortalDtos.Request> requests(@ParameterObject DealerPortalFilters.Requests filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return portal.listRequests(filter, pageable, principal);
    }

    @GetMapping("/requests/{id}")
    @Operation(summary = "One request with its issued quotation and payment receipts")
    public DealerPortalDtos.RequestDetail request(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return portal.getRequest(id, principal);
    }

    @PostMapping("/requests/{id}/cancel")
    @Operation(summary = "Withdraw a request while it is in review (409 afterwards); a reason is required")
    public DealerPortalDtos.RequestDetail cancel(@PathVariable Long id, @Valid @RequestBody DealerPortalFilters.Cancel body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return portal.cancel(id, body, principal);
    }

    @PostMapping(value = "/requests/{id}/payment-proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload proof of payment (PDF/PNG/JPEG, max 5 MB); records a receipt pending Finance approval")
    public ResponseEntity<DealerPortalDtos.Receipt> uploadProof(@PathVariable Long id,
            @Valid @RequestPart("receipt") RecordReceiptRequest receipt, @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        DealerPortalDtos.Receipt created = payments.uploadProof(id, receipt, file, principal);
        return ResponseEntity.created(URI.create("/api/dealer/requests/" + id)).body(created);
    }

    @GetMapping(value = "/requests/{id}/quotation/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the issued quotation PDF of one of the dealer's requests (audited)")
    public ResponseEntity<byte[]> quotationPdf(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        QuotationPdf pdf = payments.quotationPdf(id, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .header(QuotationDocumentController.SHA256_HEADER, pdf.sha256())
                .body(pdf.content());
    }
}
