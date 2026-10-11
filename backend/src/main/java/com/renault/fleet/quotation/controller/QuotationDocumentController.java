package com.renault.fleet.quotation.controller;

import com.renault.fleet.quotation.dto.QuotationPdf;
import com.renault.fleet.quotation.dto.QuotationVerificationDto;
import com.renault.fleet.quotation.service.QuotationDocumentService;
import com.renault.fleet.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Finance: quotation documents")
public class QuotationDocumentController {

    public static final String SHA256_HEADER = "X-Content-SHA256";
    public static final String ISSUED_SHA256_HEADER = "X-Issued-SHA256";

    private final QuotationDocumentService service;

    public QuotationDocumentController(QuotationDocumentService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/quotations/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('QUOTATION_READ')")
    @Operation(summary = "Download the quotation PDF (audited)",
            description = "Issued quotations are re-rendered from the snapshot frozen on issue; drafts carry a DRAFT "
                    + "watermark and no verification QR code.")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        QuotationPdf pdf = service.download(id, principal);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .header(SHA256_HEADER, pdf.sha256());
        if (pdf.issuedSha256() != null) {
            response.header(ISSUED_SHA256_HEADER, pdf.issuedSha256());
        }
        return response.body(pdf.content());
    }

    @GetMapping("/api/public/quotations/verify/{token}")
    @Operation(summary = "Public: verify a quotation from its QR code (rate-limited)",
            description = "Returns only the quotation number, issue date, total and status; 404 when the code is unknown.")
    public ResponseEntity<QuotationVerificationDto> verify(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.verify(token));
    }
}
