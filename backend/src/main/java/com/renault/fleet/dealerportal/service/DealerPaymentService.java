package com.renault.fleet.dealerportal.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.error.ApiException;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.dealerportal.dto.DealerPortalDtos;
import com.renault.fleet.dealerportal.mapper.DealerPortalMapper;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.payment.dto.PaymentReceiptDto;
import com.renault.fleet.payment.dto.RecordReceiptRequest;
import com.renault.fleet.payment.entity.PaymentProof;
import com.renault.fleet.payment.entity.PaymentReceipt;
import com.renault.fleet.payment.repository.PaymentProofRepository;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.payment.service.PaymentReceiptService;
import com.renault.fleet.quotation.dto.QuotationPdf;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.pdf.QuotationPdfRenderer;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.quotation.service.QuotationDocumentService;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.repository.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dealer payment proofs and quotation downloads, for the dealer's own requests only (404 otherwise). A proof upload
 * records a pending (RECORDED) receipt through {@link PaymentReceiptService}, so the duplicate-reference check, receipt
 * numbering, history and audit are the same as when Finance records one; Finance still approves it.
 */
@Service
public class DealerPaymentService {

    public static final int MAX_PROOF_BYTES = 5 * 1024 * 1024;

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final DealerPortalService portal;
    private final PaymentReceiptService receiptService;
    private final PaymentReceiptRepository receipts;
    private final PaymentProofRepository proofs;
    private final QuotationRepository quotations;
    private final QuotationDocumentService documents;
    private final UserRepository users;
    private final DealerPortalMapper mapper;
    private final AuditService audit;
    private final JsonMapper jsonMapper;

    public DealerPaymentService(DealerPortalService portal, PaymentReceiptService receiptService,
            PaymentReceiptRepository receipts, PaymentProofRepository proofs, QuotationRepository quotations,
            QuotationDocumentService documents, UserRepository users, DealerPortalMapper mapper, AuditService audit,
            JsonMapper jsonMapper) {
        this.portal = portal;
        this.receiptService = receiptService;
        this.receipts = receipts;
        this.proofs = proofs;
        this.quotations = quotations;
        this.documents = documents;
        this.users = users;
        this.mapper = mapper;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public DealerPortalDtos.Receipt uploadProof(Long requestId, RecordReceiptRequest receipt, MultipartFile file,
            AuthenticatedUser actor) {
        FleetRequest request = portal.ownRequest(requestId, actor);
        if (!DealerPortalMapper.PAYMENT_PROOF_STATES.contains(request.getStatus())) {
            throw new ConflictException("Payment proof can be uploaded once a quotation is issued and payment is open.");
        }
        byte[] content = read(file);
        String contentType = detectType(content);
        PaymentReceiptDto recorded = receiptService.record(requestId, receipt, actor);
        PaymentReceipt saved = receipts.getReferenceById(recorded.id());
        PaymentProof proof = proofs.saveAndFlush(new PaymentProof(saved, fileName(file, contentType), contentType,
                content, QuotationPdfRenderer.sha256(content), users.getReferenceById(actor.id())));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("receiptId", recorded.id());
        details.put("fileName", proof.getFileName());
        details.put("contentType", contentType);
        details.put("sizeBytes", proof.getSizeBytes());
        details.put("sha256", proof.getSha256());
        audit.record(AuditEvent.of("UPLOAD", "PAYMENT_PROOF", String.valueOf(proof.getId()))
                .withActor(actor).withChange(null, jsonMapper.writeValueAsString(details)));
        return mapper.toReceipt(receipts.findById(recorded.id()).orElseThrow(), true);
    }

    /** The PDF of the request's current ISSUED quotation (drafts are never available to dealers). Audited. */
    @Transactional
    public QuotationPdf quotationPdf(Long requestId, AuthenticatedUser actor) {
        portal.ownRequest(requestId, actor);
        Long quotationId = quotations.findFirstByFleetRequestIdAndStatus(requestId, QuotationStatus.ISSUED)
                .orElseThrow(() -> new NotFoundException("No issued quotation for request " + requestId + "."))
                .getId();
        return documents.download(quotationId, actor);
    }

    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attach the payment proof file.");
        }
        if (file.getSize() > MAX_PROOF_BYTES) {
            throw tooLarge();
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_PROOF_BYTES) {
                throw tooLarge();
            }
            return bytes;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** The type comes from the file's leading bytes, never from the client's Content-Type or file name. */
    static String detectType(byte[] content) {
        if (startsWith(content, PDF)) return "application/pdf";
        if (startsWith(content, PNG)) return "image/png";
        if (startsWith(content, JPEG)) return "image/jpeg";
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                "Payment proof must be a PDF, PNG or JPEG file.");
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        return content.length >= magic.length && Arrays.equals(content, 0, magic.length, magic, 0, magic.length);
    }

    /** Keeps only a safe base name; falls back to a generic one. */
    private static String fileName(MultipartFile file, String contentType) {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String base = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1)
                .replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        if (base.isEmpty() || base.chars().allMatch(c -> c == '.' || c == '_')) {
            base = "payment-proof." + switch (contentType) {
                case "application/pdf" -> "pdf";
                case "image/png" -> "png";
                default -> "jpg";
            };
        }
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private static ApiException tooLarge() {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE, "Payload Too Large",
                "Payment proof must be at most 5 MB.");
    }
}
