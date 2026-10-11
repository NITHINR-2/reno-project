package com.renault.fleet.dealerportal.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.dealer.entity.DealerStatus;
import com.renault.fleet.dealer.repository.DealerRepository;
import com.renault.fleet.dealerportal.dto.CreateDealerOrderRequest;
import com.renault.fleet.dealerportal.dto.DealerPortalDtos;
import com.renault.fleet.dealerportal.dto.DealerPortalFilters;
import com.renault.fleet.dealerportal.entity.DealerOrder;
import com.renault.fleet.dealerportal.mapper.DealerPortalMapper;
import com.renault.fleet.dealerportal.repository.DealerOrderRepository;
import com.renault.fleet.fleetrequest.dto.TransitionRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.fleetrequest.service.FleetRequestService;
import com.renault.fleet.payment.entity.PaymentReceipt;
import com.renault.fleet.payment.repository.PaymentProofRepository;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.workflow.service.WorkflowService;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dealer orders and the dealer's view of its fleet requests. Every query is scoped to the principal's
 * {@code dealerId}; another dealer's order or request is reported as 404, never 403, so ids can't be probed.
 */
@Service
public class DealerPortalService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DealerOrderRepository orders;
    private final FleetRequestRepository requests;
    private final DealerRepository dealers;
    private final UserRepository users;
    private final QuotationRepository quotations;
    private final PaymentReceiptRepository receipts;
    private final PaymentProofRepository proofs;
    private final FleetRequestService fleetRequests;
    private final WorkflowService workflow;
    private final DealerPortalMapper mapper;
    private final AuditService audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public DealerPortalService(DealerOrderRepository orders, FleetRequestRepository requests, DealerRepository dealers,
            UserRepository users, QuotationRepository quotations, PaymentReceiptRepository receipts,
            PaymentProofRepository proofs, FleetRequestService fleetRequests, WorkflowService workflow,
            DealerPortalMapper mapper, AuditService audit, JsonMapper jsonMapper, Clock clock) {
        this.orders = orders;
        this.requests = requests;
        this.dealers = dealers;
        this.users = users;
        this.quotations = quotations;
        this.receipts = receipts;
        this.proofs = proofs;
        this.fleetRequests = fleetRequests;
        this.workflow = workflow;
        this.mapper = mapper;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** The principal's dealership id; a DEALER account without one sees nothing. */
    public static Long dealerId(AuthenticatedUser actor) {
        if (actor.dealerId() == null) {
            throw new AccessDeniedException("This account is not linked to a dealer.");
        }
        return actor.dealerId();
    }

    @Transactional
    public DealerPortalDtos.OrderDetail createOrder(CreateDealerOrderRequest body, AuthenticatedUser actor) {
        Dealer dealer = dealers.findById(dealerId(actor)).orElseThrow(() -> new AccessDeniedException("Dealer not found."));
        if (dealer.getStatus() != DealerStatus.ACTIVE) {
            throw new ConflictException("Your dealership is " + dealer.getStatus() + "; only ACTIVE dealers can order.");
        }
        Set<Long> seen = new HashSet<>();
        for (CreateDealerOrderRequest.Line line : body.lines()) {
            if (!seen.add(line.variantId())) {
                throw new BadRequestException("Variant " + line.variantId() + " appears more than once; combine the lines.");
            }
        }
        String notes = body.notes() == null || body.notes().isBlank() ? null : body.notes().strip();
        DealerOrder order = orders.saveAndFlush(new DealerOrder(nextOrderNumber(), dealer,
                users.getReferenceById(actor.id()), body.requestedDeliveryDate(), notes));
        List<FleetRequest> lines = new ArrayList<>();
        for (int i = 0; i < body.lines().size(); i++) {
            CreateDealerOrderRequest.Line line = body.lines().get(i);
            lines.add(fleetRequests.createForDealerOrder(order, i + 1, line.variantId(), line.quantity(), actor));
        }
        DealerPortalDtos.OrderDetail dto = mapper.toDetail(order, lines);
        audit.record(AuditEvent.of("CREATE", "DEALER_ORDER", String.valueOf(order.getId()))
                .withActor(actor).withChange(null, jsonMapper.writeValueAsString(dto)));
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<DealerPortalDtos.OrderSummary> listOrders(DealerPortalFilters.Orders f, Pageable pageable,
            AuthenticatedUser actor) {
        Long dealerId = dealerId(actor);
        List<Specification<DealerOrder>> specs = new ArrayList<>();
        specs.add((root, q, cb) -> cb.equal(root.get("dealer").get("id"), dealerId));
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + escape(f.q()) + "%";
            specs.add((root, q, cb) -> cb.like(cb.lower(root.get("orderNumber")), like, '\\'));
        }
        if (f.from() != null) {
            specs.add((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay(IST).toInstant()));
        }
        if (f.to() != null) {
            specs.add((root, q, cb) -> cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay(IST).toInstant()));
        }
        Page<DealerOrder> page = orders.findAll(Specification.allOf(specs), pageable);
        Map<Long, List<FleetRequest>> lines = linesOf(page.getContent().stream().map(DealerOrder::getId).toList());
        return PageResponse.from(page, o -> mapper.toSummary(o, lines.getOrDefault(o.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public DealerPortalDtos.OrderDetail getOrder(Long id, AuthenticatedUser actor) {
        DealerOrder order = orders.findByIdAndDealerId(id, dealerId(actor))
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found."));
        return mapper.toDetail(order, linesOf(List.of(id)).getOrDefault(id, List.of()));
    }

    @Transactional(readOnly = true)
    public PageResponse<DealerPortalDtos.Request> listRequests(DealerPortalFilters.Requests f, Pageable pageable,
            AuthenticatedUser actor) {
        Long dealerId = dealerId(actor);
        List<Specification<FleetRequest>> specs = new ArrayList<>();
        specs.add((root, q, cb) -> cb.equal(root.get("dealer").get("id"), dealerId));
        if (f.status() != null) {
            specs.add((root, q, cb) -> root.get("status").in(f.status().internal()));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + escape(f.q()) + "%";
            specs.add((root, q, cb) -> cb.like(cb.lower(root.get("requestNumber")), like, '\\'));
        }
        return PageResponse.from(requests.findAll(Specification.allOf(specs), pageable), mapper::toRequest);
    }

    @Transactional(readOnly = true)
    public DealerPortalDtos.RequestDetail getRequest(Long id, AuthenticatedUser actor) {
        FleetRequest request = ownRequest(id, actor);
        DealerPortalDtos.Quotation quotation = quotations
                .findFirstByFleetRequestIdAndStatus(id, QuotationStatus.ISSUED)
                .map(mapper::toQuotation)
                .orElse(null);
        List<PaymentReceipt> own = receipts.findByFleetRequestId(id, Pageable.unpaged()).getContent();
        Set<Long> withProof = own.isEmpty() ? Set.of()
                : Set.copyOf(proofs.findReceiptIdsWithProof(own.stream().map(PaymentReceipt::getId).toList()));
        return new DealerPortalDtos.RequestDetail(mapper.toRequest(request), quotation,
                own.stream().map(p -> mapper.toReceipt(p, withProof.contains(p.getId()))).toList());
    }

    /** Withdraws a request; the workflow allows a DEALER only from PENDING_MARKETING_REVIEW (409 afterwards). */
    @Transactional
    public DealerPortalDtos.RequestDetail cancel(Long id, DealerPortalFilters.Cancel body, AuthenticatedUser actor) {
        ownRequest(id, actor);
        workflow.transition(id, WorkflowAction.CANCEL, new TransitionRequest(body.reason(), body.version()), actor);
        return getRequest(id, actor);
    }

    /** The caller's own request, or 404. */
    @Transactional(readOnly = true)
    public FleetRequest ownRequest(Long id, AuthenticatedUser actor) {
        return requests.findByIdAndDealerId(id, dealerId(actor))
                .orElseThrow(() -> new NotFoundException("Request " + id + " not found."));
    }

    private Map<Long, List<FleetRequest>> linesOf(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return requests.findByDealerOrderIdInOrderByDealerOrderLineNo(orderIds).stream()
                .collect(Collectors.groupingBy(r -> r.getDealerOrder().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    private String nextOrderNumber() {
        int year = ZonedDateTime.now(clock).withZoneSameInstant(IST).getYear();
        return "DO-%d-%04d".formatted(year, orders.nextOrderNumber());
    }

    private static String escape(String q) {
        return q.strip().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_");
    }
}
