package com.renault.fleet.quotation.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.quotation.dto.QuotationPdf;
import com.renault.fleet.quotation.dto.QuotationVerificationDto;
import com.renault.fleet.quotation.entity.Quotation;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.pdf.QuotationPdfRenderer;
import com.renault.fleet.quotation.pdf.QuotationSnapshot;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.security.AuthenticatedUser;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Quotation documents. On issue the quotation gets a random verification token and an immutable snapshot plus the
 * SHA-256 of its PDF; downloads of issued quotations are re-rendered from that snapshot only. Drafts are rendered
 * from the live draft with a DRAFT watermark and no verification code.
 */
@Service
public class QuotationDocumentService {

    private static final Logger log = LoggerFactory.getLogger(QuotationDocumentService.class);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,64}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final QuotationRepository quotations;
    private final QuotationPdfRenderer renderer;
    private final AuditService audit;
    private final JsonMapper jsonMapper;
    private final String publicBaseUrl;
    private final int validityDays;

    public QuotationDocumentService(QuotationRepository quotations, QuotationPdfRenderer renderer, AuditService audit,
            JsonMapper jsonMapper, @Value("${fleet.public-base-url:http://localhost:5173}") String publicBaseUrl,
            @Value("${fleet.quotation.validity-days:30}") int validityDays) {
        this.quotations = quotations;
        this.renderer = renderer;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.validityDays = validityDays;
    }

    /**
     * Called by {@link QuotationService#issue} after the issue fields are set and before the row is flushed: assigns
     * the token, freezes the snapshot and stores the PDF hash in the same transaction.
     */
    void freezeOnIssue(Quotation quotation) {
        if (quotation.getVerificationToken() == null) {
            quotation.setVerificationToken(newToken());
        }
        freeze(quotation);
    }

    /** Renders the PDF and writes a download audit row. */
    @Transactional
    public QuotationPdf download(Long id, AuthenticatedUser actor) {
        Quotation quotation = quotations.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Quotation " + id + " not found."));
        boolean issued = isIssued(quotation);
        if (issued && quotation.getSnapshotJson() == null) {
            // Issued before V11: freeze now from the issued columns, which never change after issue.
            quotation = quotations.findByIdForUpdate(id).orElseThrow();
            if (quotation.getSnapshotJson() == null) {
                freezeOnIssue(quotation); // also assigns a token if the row never got one
                quotations.saveAndFlush(quotation);
            }
        }
        QuotationSnapshot snapshot = issued
                ? jsonMapper.readValue(quotation.getSnapshotJson(), QuotationSnapshot.class)
                : snapshot(quotation, null);
        byte[] pdf = renderer.render(snapshot);
        String sha256 = QuotationPdfRenderer.sha256(pdf);
        Boolean matchesIssued = issued ? sha256.equals(quotation.getPdfSha256()) : null;
        if (Boolean.FALSE.equals(matchesIssued)) {
            log.warn("Quotation {} re-rendered PDF hash {} differs from the issued hash {} (figures come from the frozen snapshot).",
                    quotation.getQuotationNumber(), sha256, quotation.getPdfSha256());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("quotationNumber", quotation.getQuotationNumber());
        details.put("status", quotation.getStatus().name());
        details.put("sha256", sha256);
        details.put("issuedSha256", quotation.getPdfSha256());
        details.put("matchesIssuedHash", matchesIssued);
        audit.record(AuditEvent.of("QUOTATION_PDF_DOWNLOADED", "QUOTATION", String.valueOf(quotation.getId()))
                .withActor(actor).withChange(null, jsonMapper.writeValueAsString(details)));
        return new QuotationPdf(quotation.getQuotationNumber() + (issued ? "" : "-DRAFT") + ".pdf", pdf, sha256,
                quotation.getPdfSha256());
    }

    /** Public check behind the QR code: only number, issue date, total and status. Unknown or draft → 404. */
    @Transactional(readOnly = true)
    public QuotationVerificationDto verify(String token) {
        Quotation quotation = token == null || !TOKEN.matcher(token).matches() ? null
                : quotations.findByVerificationToken(token).filter(QuotationDocumentService::isIssued).orElse(null);
        if (quotation == null) {
            throw new NotFoundException("No quotation matches this verification code.");
        }
        return new QuotationVerificationDto("GENUINE", quotation.getQuotationNumber(), quotation.getIssueDate(),
                quotation.getTotalAmount(), quotation.getCurrency(), quotation.getStatus().name());
    }

    private void freeze(Quotation quotation) {
        QuotationSnapshot snapshot = snapshot(quotation, publicBaseUrl + "/verify/quotation/" + quotation.getVerificationToken());
        String json = jsonMapper.writeValueAsString(snapshot);
        // Hash what later downloads will render: the snapshot as read back, not the in-memory original.
        byte[] pdf = renderer.render(jsonMapper.readValue(json, QuotationSnapshot.class));
        quotation.freezeDocument(json, QuotationPdfRenderer.sha256(pdf));
    }

    private QuotationSnapshot snapshot(Quotation q, String verificationUrl) {
        Dealer dealer = q.getFleetRequest().getDealer();
        boolean draft = !isIssued(q);
        return new QuotationSnapshot(QuotationSnapshot.SCHEMA_VERSION, q.getQuotationNumber(),
                q.getFleetRequest().getRequestNumber(), draft, q.getIssueDate(), q.getValidUntil(), validityDays,
                q.getIssuedBy() == null ? null : q.getIssuedBy().getFullName(),
                draft || q.getIssuedAt() == null ? q.getCreatedAt() : q.getIssuedAt(),
                new QuotationSnapshot.DealerDetails(dealer.getName(), dealer.getDealerCode(), dealer.getGstin(),
                        dealer.getAddressLine(), dealer.getCity(), dealer.getState(), dealer.getPostalCode(),
                        dealer.getRegion() == null ? null : dealer.getRegion().getName(), dealer.getContactName(),
                        dealer.getContactPhone(), dealer.getContactEmail()),
                q.getLineItems().stream().map(l -> new QuotationSnapshot.Line(l.getLineNo(), l.getDescription(),
                        l.getQuantity(), l.getUnitPrice(), l.getBaseAmount(), l.getGstRate(), l.getGstAmount(),
                        l.getLineTotal())).toList(),
                q.getGstRate(), q.getBaseAmount(), q.getGstAmount(), q.getTotalAmount(), q.getRequiredDeposit(),
                q.getDepositReceived(), q.getDepositShortfall(), q.getCurrency(), q.getTermsText(),
                draft ? null : verificationUrl);
    }

    private static boolean isIssued(Quotation q) {
        return q.getStatus() == QuotationStatus.ISSUED || q.getStatus() == QuotationStatus.SUPERSEDED;
    }

    /** 256 random bits, URL-safe Base64 (43 characters). */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
