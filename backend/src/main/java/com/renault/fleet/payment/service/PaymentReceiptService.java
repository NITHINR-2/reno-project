package com.renault.fleet.payment.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.payment.dto.PaymentReceiptDto;
import com.renault.fleet.payment.dto.RecordReceiptRequest;
import com.renault.fleet.payment.entity.PaymentReceipt;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.mapper.PaymentReceiptMapper;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.quotation.entity.Quotation;
import com.renault.fleet.quotation.entity.QuotationPaymentStatus;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.service.WorkflowHistoryService;
import com.renault.fleet.workflow.service.WorkflowService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records and approves deposit/payment receipts. A transaction reference can be recorded once (checked here and by
 * a unique index); approval locks the receipt row and happens exactly once (a repeat is a 409).
 */
@Service
public class PaymentReceiptService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** Finance stages in which money can be recorded and approved. */
    static final Set<FleetRequestStatus> RECEIPT_STATES = EnumSet.of(FleetRequestStatus.PENDING_FINANCE_CLEARANCE,
            FleetRequestStatus.PAYMENT_PENDING, FleetRequestStatus.FUNDS_VERIFIED);

    private final PaymentReceiptRepository receipts;
    private final FleetRequestRepository requests;
    private final QuotationRepository quotations;
    private final UserRepository users;
    private final WorkflowService workflow;
    private final WorkflowHistoryService history;
    private final AuditService audit;
    private final PaymentReceiptMapper mapper;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public PaymentReceiptService(PaymentReceiptRepository receipts, FleetRequestRepository requests,
            QuotationRepository quotations, UserRepository users, WorkflowService workflow,
            WorkflowHistoryService history, AuditService audit, PaymentReceiptMapper mapper, JsonMapper jsonMapper,
            Clock clock) {
        this.receipts = receipts;
        this.requests = requests;
        this.quotations = quotations;
        this.users = users;
        this.workflow = workflow;
        this.history = history;
        this.audit = audit;
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentReceiptDto> forRequest(Long requestId, Pageable pageable) {
        if (!requests.existsById(requestId)) {
            throw new NotFoundException("Fleet request " + requestId + " not found.");
        }
        return PageResponse.from(receipts.findByFleetRequestId(requestId, pageable), mapper::toDto);
    }

    @Transactional(readOnly = true)
    public PaymentReceiptDto get(Long id) {
        return mapper.toDto(receipts.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Payment receipt " + id + " not found.")));
    }

    @Transactional
    public PaymentReceiptDto record(Long requestId, RecordReceiptRequest body, AuthenticatedUser actor) {
        FleetRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("Fleet request " + requestId + " not found."));
        requireReceiptState(request);
        LocalDate today = LocalDate.ofInstant(Instant.now(clock), IST);
        if (body.receivedDate().isAfter(today)) {
            throw new BadRequestException("receivedDate cannot be in the future (today is " + today + " IST).");
        }
        String reference = body.transactionReference().strip();
        if (receipts.existsByNormalisedReference(reference)) {
            throw duplicate(reference);
        }
        PaymentReceipt receipt = new PaymentReceipt(nextNumber(today), request, body.receiptType(), body.paymentMethod(),
                reference, body.amount(), body.receivedDate(), users.getReferenceById(actor.id()));
        quotations.findFirstByFleetRequestIdAndStatus(requestId, QuotationStatus.ISSUED).ifPresent(receipt::setQuotation);
        try {
            receipts.saveAndFlush(receipt);
        } catch (DataIntegrityViolationException ex) {
            String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (cause.contains("ux_payment_receipts_txn_ref")) {
                throw duplicate(reference); // a concurrent insert won the unique index
            }
            throw ex;
        }

        PaymentReceiptDto dto = mapper.toDto(receipt);
        history.record(request, request.getStatus(), request.getStatus(), "RECEIPT_RECORDED", actor, null,
                metadata(receipt));
        audit.record(AuditEvent.of("CREATE", "PAYMENT_RECEIPT", String.valueOf(receipt.getId()))
                .withActor(actor).withChange(null, jsonMapper.writeValueAsString(dto)));
        return dto;
    }

    /**
     * RECORDED → APPROVED, once. Locks the request then the receipt row; a second approval (concurrent or later) is a
     * 409. If approved deposits now cover the required deposit, the request moves to FUNDS_VERIFIED.
     */
    @Transactional
    public PaymentReceiptDto approve(Long receiptId, AuthenticatedUser actor) {
        Long requestId = receipts.findRequestIdById(receiptId)
                .orElseThrow(() -> new NotFoundException("Payment receipt " + receiptId + " not found."));
        FleetRequest request = requests.findByIdForUpdate(requestId).orElseThrow();
        PaymentReceipt receipt = receipts.findByIdForUpdate(receiptId).orElseThrow();
        if (receipt.getStatus() != ReceiptStatus.RECORDED) {
            throw new ConflictException("Receipt " + receipt.getReceiptNumber() + " is already " + receipt.getStatus() + ".");
        }
        requireReceiptState(request);
        String before = jsonMapper.writeValueAsString(mapper.toDto(receipt));

        receipt.setStatus(ReceiptStatus.APPROVED);
        receipt.setReviewedBy(users.getReferenceById(actor.id()));
        receipt.setReviewedAt(Instant.now(clock));
        receipts.saveAndFlush(receipt);
        quotations.findFirstByFleetRequestIdAndStatus(requestId, QuotationStatus.ISSUED).ifPresent(this::refreshPaymentStatus);

        history.record(request, request.getStatus(), request.getStatus(), "RECEIPT_APPROVED", actor, null, metadata(receipt));
        audit.record(AuditEvent.of("APPROVE", "PAYMENT_RECEIPT", String.valueOf(receiptId))
                .withActor(actor).withChange(before, jsonMapper.writeValueAsString(mapper.toDto(receipt))));
        workflow.verifyFundsIfCovered(requestId, actor);
        return mapper.toDto(receipt);
    }

    /** The quotation's payment status follows approved money only; issuing it never changes this. */
    private void refreshPaymentStatus(Quotation quotation) {
        BigDecimal approved = receipts.sumAmount(quotation.getFleetRequest().getId(), ReceiptStatus.APPROVED);
        quotation.setPaymentStatus(QuotationPaymentStatus.from(approved, quotation.getTotalAmount()));
    }

    private static void requireReceiptState(FleetRequest request) {
        if (!RECEIPT_STATES.contains(request.getStatus())) {
            throw new ConflictException("Receipts can only be recorded or approved in " + RECEIPT_STATES
                    + " (the request is " + request.getStatus() + ").");
        }
    }

    private static ConflictException duplicate(String reference) {
        return new ConflictException("Transaction reference '" + reference + "' has already been recorded.");
    }

    private String metadata(PaymentReceipt r) {
        return jsonMapper.writeValueAsString(Map.of("receiptNumber", r.getReceiptNumber(), "receiptType",
                r.getReceiptType(), "amount", r.getAmount(), "transactionReference", r.getTransactionReference()));
    }

    private String nextNumber(LocalDate today) {
        return "RCPT-%d-%05d".formatted(today.getYear(), receipts.nextReceiptNumber());
    }
}
