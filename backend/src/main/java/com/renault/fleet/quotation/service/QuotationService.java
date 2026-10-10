package com.renault.fleet.quotation.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.common.persistence.Versions;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.quotation.dto.CalculateQuotationRequest;
import com.renault.fleet.quotation.dto.IssueQuotationRequest;
import com.renault.fleet.quotation.dto.QuotationDto;
import com.renault.fleet.quotation.entity.Quotation;
import com.renault.fleet.quotation.entity.QuotationLineItem;
import com.renault.fleet.quotation.entity.QuotationPaymentStatus;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.entity.TaxRate;
import com.renault.fleet.quotation.mapper.QuotationMapper;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.quotation.repository.TaxRateRepository;
import com.renault.fleet.quotation.service.QuotationCalculator.Breakdown;
import com.renault.fleet.quotation.service.QuotationCalculator.LineResult;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.workflow.service.WorkflowFacts;
import com.renault.fleet.workflow.service.WorkflowHistoryService;
import com.renault.fleet.workflow.service.WorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calculates DRAFT quotations and issues them. Issuing moves the request through the WorkflowService
 * (ISSUE_QUOTATION, then automatically to PAYMENT_PENDING) and never records or implies a payment.
 */
@Service
public class QuotationService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final QuotationRepository quotations;
    private final FleetRequestRepository requests;
    private final TaxRateRepository taxRates;
    private final UserRepository users;
    private final WorkflowService workflow;
    private final WorkflowFacts facts;
    private final PaymentReceiptRepository receipts;
    private final WorkflowHistoryService history;
    private final AuditService audit;
    private final QuotationMapper mapper;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final int validityDays;
    private final String defaultTerms;

    public QuotationService(QuotationRepository quotations, FleetRequestRepository requests, TaxRateRepository taxRates,
            UserRepository users, WorkflowService workflow, WorkflowFacts facts, PaymentReceiptRepository receipts,
            WorkflowHistoryService history,
            AuditService audit, QuotationMapper mapper, JsonMapper jsonMapper, Clock clock,
            @Value("${fleet.quotation.validity-days:30}") int validityDays,
            @Value("${fleet.quotation.default-terms:}") String defaultTerms) {
        this.quotations = quotations;
        this.requests = requests;
        this.taxRates = taxRates;
        this.users = users;
        this.workflow = workflow;
        this.facts = facts;
        this.receipts = receipts;
        this.history = history;
        this.audit = audit;
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.validityDays = validityDays;
        this.defaultTerms = defaultTerms;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuotationDto> forRequest(Long requestId, Pageable pageable) {
        if (!requests.existsById(requestId)) {
            throw new NotFoundException("Fleet request " + requestId + " not found.");
        }
        return PageResponse.from(quotations.findByFleetRequestId(requestId, pageable), mapper::toDto);
    }

    @Transactional(readOnly = true)
    public QuotationDto get(Long id) {
        return mapper.toDto(quotations.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Quotation " + id + " not found.")));
    }

    /** Creates a fresh DRAFT (earlier drafts become CANCELLED). Only in PENDING_FINANCE_CLEARANCE. */
    @Transactional
    public QuotationDto calculate(Long requestId, CalculateQuotationRequest body, AuthenticatedUser actor) {
        FleetRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("Fleet request " + requestId + " not found."));
        if (request.getStatus() != FleetRequestStatus.PENDING_FINANCE_CLEARANCE) {
            throw new ConflictException("Quotations can only be calculated in PENDING_FINANCE_CLEARANCE (the request is "
                    + request.getStatus() + ").");
        }
        TaxRate rate = effectiveRate(request);
        Breakdown breakdown = breakdown(request, rate);

        for (Quotation old : quotations.findByFleetRequestIdAndStatus(requestId, QuotationStatus.DRAFT)) {
            old.setStatus(QuotationStatus.CANCELLED);
        }
        Quotation draft = new Quotation(nextNumber(), request, rate, users.getReferenceById(actor.id()));
        VehicleVariant variant = request.getVariant();
        LineResult line = breakdown.lines().getFirst();
        draft.addLineItem(new QuotationLineItem(1, variant, variant.getModel().getName() + " " + variant.getName(),
                line.quantity(), line.unitPrice(), line.baseAmount(), rate.getRate(), line.gstAmount(), line.lineTotal()));
        apply(draft, breakdown);
        String terms = body == null || body.termsText() == null || body.termsText().isBlank() ? defaultTerms : body.termsText();
        draft.setTermsText(terms == null || terms.isBlank() ? null : terms);
        quotations.saveAndFlush(draft);

        QuotationDto dto = mapper.toDto(draft);
        history.record(request, request.getStatus(), request.getStatus(), "QUOTATION_CALCULATED", actor, null,
                jsonMapper.writeValueAsString(Map.of("quotationNumber", draft.getQuotationNumber(),
                        "totalAmount", draft.getTotalAmount())));
        audit.record(AuditEvent.of("QUOTATION_CALCULATED", "QUOTATION", String.valueOf(draft.getId()))
                .withActor(actor).withChange(null, jsonMapper.writeValueAsString(dto)));
        return dto;
    }

    /**
     * DRAFT → ISSUED, then the request goes PENDING_FINANCE_CLEARANCE → QUOTATION_ISSUED → PAYMENT_PENDING. The
     * draft is re-checked against current price, tax rate and approved deposits; a stale draft is a 409.
     */
    @Transactional
    public QuotationDto issue(Long quotationId, IssueQuotationRequest body, AuthenticatedUser actor) {
        Long requestId = quotations.findRequestIdById(quotationId)
                .orElseThrow(() -> new NotFoundException("Quotation " + quotationId + " not found."));
        FleetRequest request = requests.findByIdForUpdate(requestId).orElseThrow();
        Quotation quotation = quotations.findByIdForUpdate(quotationId).orElseThrow();
        Versions.check(quotation, body == null ? null : body.version());
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new ConflictException("Quotation " + quotation.getQuotationNumber() + " is " + quotation.getStatus()
                    + "; only a DRAFT can be issued.");
        }
        if (request.getStatus() != FleetRequestStatus.PENDING_FINANCE_CLEARANCE) {
            throw new ConflictException("Cannot ISSUE_QUOTATION a request in status " + request.getStatus() + ".");
        }
        TaxRate rate = effectiveRate(request);
        Breakdown current = breakdown(request, rate);
        if (!rate.getId().equals(quotation.getTaxRate().getId()) || !matches(quotation, current)) {
            throw new ConflictException("Quotation " + quotation.getQuotationNumber()
                    + " is out of date (price, GST rate or approved deposits changed). Recalculate it before issuing.");
        }
        String before = jsonMapper.writeValueAsString(mapper.toDto(quotation));

        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, IST);
        quotation.setStatus(QuotationStatus.ISSUED);
        quotation.setIssueDate(today);
        quotation.setValidUntil(today.plusDays(validityDays));
        quotation.setIssuedAt(now);
        quotation.setIssuedBy(users.getReferenceById(actor.id()));
        // Money approved before issue still counts; the quotation itself never does.
        quotation.setPaymentStatus(QuotationPaymentStatus.from(
                receipts.sumAmount(requestId, ReceiptStatus.APPROVED), quotation.getTotalAmount()));
        quotations.saveAndFlush(quotation); // the partial unique index enforces one ISSUED per request here

        workflow.transition(requestId, WorkflowAction.ISSUE_QUOTATION, null, actor);
        // Deposits approved before issue may already cover the requirement; that is a receipt fact, not this quotation.
        workflow.verifyFundsIfCovered(requestId, actor);

        QuotationDto dto = mapper.toDto(quotation);
        audit.record(AuditEvent.of("QUOTATION_ISSUED", "QUOTATION", String.valueOf(quotationId))
                .withActor(actor).withChange(before, jsonMapper.writeValueAsString(dto)));
        return dto;
    }

    private Breakdown breakdown(FleetRequest request, TaxRate rate) {
        try {
            return QuotationCalculator.calculate(
                    List.of(new QuotationCalculator.LineInput(request.getQuantity(), request.getVariant().getUnitPrice())),
                    rate.getRate(), request.getRequiredDeposit(), facts.eligibleDeposits(request.getId()));
        } catch (IllegalArgumentException ex) {
            throw new ConflictException(ex.getMessage());
        }
    }

    private TaxRate effectiveRate(FleetRequest request) {
        String category = request.getVariant().getModel().getVehicleCategory();
        LocalDate today = LocalDate.ofInstant(Instant.now(clock), IST);
        return taxRates.findEffective(category, today).orElseThrow(() -> new ConflictException(
                "No GST rate is configured for vehicle category " + category + " on " + today + ". Finance must add one."));
    }

    private static void apply(Quotation q, Breakdown b) {
        q.setBaseAmount(b.baseAmount());
        q.setGstAmount(b.gstAmount());
        q.setTotalAmount(b.totalAmount());
        q.setRequiredDeposit(b.requiredDeposit());
        q.setDepositReceived(b.depositReceived());
        q.setDepositShortfall(b.depositShortfall());
    }

    private static boolean matches(Quotation q, Breakdown b) {
        return q.getBaseAmount().compareTo(b.baseAmount()) == 0
                && q.getGstAmount().compareTo(b.gstAmount()) == 0
                && q.getTotalAmount().compareTo(b.totalAmount()) == 0
                && q.getRequiredDeposit().compareTo(b.requiredDeposit()) == 0
                && q.getDepositReceived().compareTo(b.depositReceived()) == 0;
    }

    private String nextNumber() {
        int year = LocalDate.ofInstant(Instant.now(clock), IST).getYear();
        return "QT-%d-%05d".formatted(year, quotations.nextQuotationNumber());
    }
}
