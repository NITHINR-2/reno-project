package com.renault.fleet.quotation;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.payment.service.PaymentReceiptService;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Quotation calculation and issue, receipts, approval and clearance, through HTTP with real JWTs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinanceApiTest {

    private static final BigDecimal REQUIRED = new BigDecimal("5000000.00");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private FleetRequestRepository requests;
    @Autowired private QuotationRepository quotations;
    @Autowired private PaymentReceiptRepository receipts;
    @Autowired private PaymentReceiptService receiptService;

    private final Map<RoleCode, Session> sessions = new EnumMap<>(RoleCode.class);
    private User creator;
    private User financeUser;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        for (RoleCode role : RoleCode.values()) {
            User user = users.create(role);
            sessions.put(role, loginOk(mockMvc, user.getUsername()));
            if (role == RoleCode.MARKETING_MANAGER) creator = user;
            if (role == RoleCode.FINANCE_TEAM) financeUser = user;
        }
    }

    /** 50 units at ₹12,00,000, 18% GST, ₹50,00,000 deposit required: the FLT-2026-0891 numbers. */
    private FleetRequest financeRequest() {
        World world = data.worldWithGst("0.1800");
        return data.request(world, 50, FleetRequestStatus.PENDING_FINANCE_CLEARANCE, creator, true, REQUIRED);
    }

    @Test
    void draftQuotationCarriesTheFullBreakdownAndRecalculationReplacesTheDraft() throws Exception {
        FleetRequest request = financeRequest();
        approve(record(request.getId(), "DEPOSIT", "1000000.00"));

        String first = calculate(request.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.quotationNumber").value(org.hamcrest.Matchers.matchesPattern("QT-\\d{4}-\\d{5,}")))
                .andExpect(jsonPath("$.gstRate").value(0.18))
                .andExpect(jsonPath("$.baseAmount").value(60000000.00))
                .andExpect(jsonPath("$.gstAmount").value(10800000.00))
                .andExpect(jsonPath("$.totalAmount").value(70800000.00))
                .andExpect(jsonPath("$.requiredDeposit").value(5000000.00))
                .andExpect(jsonPath("$.depositReceived").value(1000000.00))
                .andExpect(jsonPath("$.depositShortfall").value(4000000.00))
                .andExpect(jsonPath("$.lines[0].quantity").value(50))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(1200000.00))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(70800000.00))
                .andExpect(jsonPath("$.issueDate").doesNotExist())
                .andExpect(jsonPath("$.termsText").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Long firstId = ((Integer) JsonPath.read(first, "$.id")).longValue();

        calculate(request.getId()).andExpect(status().isCreated());
        assertThat(quotations.findById(firstId).orElseThrow().getStatus()).isEqualTo(QuotationStatus.CANCELLED);
        assertThat(quotations.findByFleetRequestIdAndStatus(request.getId(), QuotationStatus.DRAFT)).hasSize(1);
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(FleetRequestStatus.PENDING_FINANCE_CLEARANCE);
    }

    @Test
    void issuingIsNotPaymentAndMovesTheRequestToPaymentPending() throws Exception {
        FleetRequest request = financeRequest();
        Long quotationId = draftId(request.getId());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        issue(quotationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.requestStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.issueDate").value(today.toString()))
                .andExpect(jsonPath("$.validUntil").value(today.plusDays(30).toString()))
                .andExpect(jsonPath("$.depositReceived").value(0))
                .andExpect(jsonPath("$.depositShortfall").value(5000000.00));

        // No receipt was created, nothing counts as paid, and funds are not verified.
        assertThat(receipts.sumAmount(request.getId(), ReceiptStatus.APPROVED)).isEqualByComparingTo("0");
        assertThat(receipts.findByFleetRequestId(request.getId(), org.springframework.data.domain.Pageable.unpaged())).isEmpty();
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(FleetRequestStatus.PAYMENT_PENDING);
        call(get("/api/fleet-requests/" + request.getId() + "/history?size=50"), RoleCode.FINANCE_TEAM)
                .andExpect(jsonPath("$.content[*].action",
                        org.hamcrest.Matchers.contains("QUOTATION_CALCULATED", "ISSUE_QUOTATION", "AWAIT_PAYMENT")))
                .andExpect(jsonPath("$.content[2].actorId").doesNotExist());
        call(post("/api/fleet-requests/" + request.getId() + "/grant-clearance"), RoleCode.FINANCE_TEAM)
                .andExpect(status().isConflict());

        issue(quotationId).andExpect(status().isConflict());
        calculate(request.getId()).andExpect(status().isConflict());
    }

    @Test
    void receiptsApprovalVerifiesFundsAndClearanceFollows() throws Exception {
        FleetRequest request = financeRequest();
        Long id = request.getId();
        issue(draftId(id)).andExpect(status().isOk());

        Long first = record(id, "DEPOSIT", "3000000.00");
        Long payment = record(id, "PAYMENT", "1000000.00");
        approve(first);
        approve(payment);
        // PAYMENT-type money does not count towards the deposit: 30L of 50L.
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(FleetRequestStatus.PAYMENT_PENDING);
        assertThat(quotations.findFirstByFleetRequestIdAndStatus(id, QuotationStatus.ISSUED).orElseThrow().getPaymentStatus())
                .hasToString("PARTIALLY_PAID");

        Long second = record(id, "DEPOSIT", "2000000.00");
        call(post("/api/payment-receipts/" + second + "/approve"), RoleCode.FINANCE_TEAM)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.requestStatus").value("FUNDS_VERIFIED"));

        call(post("/api/fleet-requests/" + id + "/grant-clearance"), RoleCode.FINANCE_TEAM)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINANCIALLY_CLEARED"));
        record(id, "DEPOSIT", "1.00", status().isConflict());
    }

    @Test
    void depositsApprovedBeforeIssueVerifyFundsOnIssue() throws Exception {
        FleetRequest request = financeRequest();
        approve(record(request.getId(), "DEPOSIT", "5000000.00"));
        issue(draftId(request.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.requestStatus").value("FUNDS_VERIFIED"))
                .andExpect(jsonPath("$.depositShortfall").value(0.00));
    }

    @Test
    void aDraftMadeStaleByANewApprovalCannotBeIssued() throws Exception {
        FleetRequest request = financeRequest();
        Long quotationId = draftId(request.getId());
        approve(record(request.getId(), "DEPOSIT", "100.00"));
        issue(quotationId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("out of date")));
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(FleetRequestStatus.PENDING_FINANCE_CLEARANCE);
    }

    @Test
    void calculationNeedsAConfiguredRateAndTheFinanceStage() throws Exception {
        World noRate = data.world(com.renault.fleet.dealer.entity.DealerStatus.ACTIVE, "TNORATE_" + suffix());
        FleetRequest unrated = data.request(noRate, 5, FleetRequestStatus.PENDING_FINANCE_CLEARANCE, creator, true);
        calculate(unrated.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No GST rate")));

        FleetRequest early = data.request(data.worldWithGst("0.18"), 5, FleetRequestStatus.EXECUTIVE_APPROVED, creator, true);
        calculate(early.getId()).andExpect(status().isConflict());
        record(early.getId(), "DEPOSIT", "10.00", status().isConflict());
    }

    @Test
    void duplicateTransactionReferenceIsAConflictWhateverTheCaseOrSpacing() throws Exception {
        FleetRequest request = financeRequest();
        String ref = "UTR-" + suffix();
        receipt(request.getId(), "DEPOSIT", "100.00", ref).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.receiptNumber").value(org.hamcrest.Matchers.matchesPattern("RCPT-\\d{4}-\\d{5,}")));
        receipt(request.getId(), "DEPOSIT", "100.00", "  " + ref.toLowerCase() + " ").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("already been recorded")));
        FleetRequest other = financeRequest();
        receipt(other.getId(), "PAYMENT", "5.00", ref).andExpect(status().isConflict());
    }

    @Test
    void receiptValidation() throws Exception {
        FleetRequest request = financeRequest();
        call(post("/api/fleet-requests/" + request.getId() + "/payment-receipts")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0,\"transactionReference\":\"\"}"), RoleCode.FINANCE_TEAM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.transactionReference").exists())
                .andExpect(jsonPath("$.errors.receiptType").exists())
                .andExpect(jsonPath("$.errors.receivedDate").exists());
        String future = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2).toString();
        call(post("/api/fleet-requests/" + request.getId() + "/payment-receipts").contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson("DEPOSIT", "10.00", "UTR-" + suffix(), future)), RoleCode.FINANCE_TEAM)
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvingTwiceIsAConflictAndUnknownIsNotFound() throws Exception {
        FleetRequest request = financeRequest();
        Long receiptId = record(request.getId(), "DEPOSIT", "100.00");
        approve(receiptId);
        call(post("/api/payment-receipts/" + receiptId + "/approve"), RoleCode.SUPERVISOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("already APPROVED")));
        assertThat(receipts.sumAmount(request.getId(), ReceiptStatus.APPROVED)).isEqualByComparingTo("100.00");
        call(post("/api/payment-receipts/" + Long.MAX_VALUE + "/approve"), RoleCode.FINANCE_TEAM).andExpect(status().isNotFound());
    }

    @Test
    void concurrentApprovalsOfOneReceiptApproveItExactlyOnce() throws Exception {
        FleetRequest request = financeRequest();
        Long receiptId = record(request.getId(), "DEPOSIT", "100.00");
        AuthenticatedUser fin = new AuthenticatedUser(financeUser.getId(), financeUser.getUsername(), "FIN", null,
                RoleCode.FINANCE_TEAM, Set.of());
        int threads = 4;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return receiptService.approve(receiptId, fin);
                }));
            }
            go.countDown();
            int ok = 0;
            int conflicts = 0;
            for (Future<?> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                    ok++;
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(ConflictException.class);
                    conflicts++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflicts).isEqualTo(threads - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(receipts.sumAmount(request.getId(), ReceiptStatus.APPROVED)).isEqualByComparingTo("100.00");
    }

    @Test
    void rolesWithoutTheFinancePermissionsGet403() throws Exception {
        FleetRequest request = financeRequest();
        Long quotationId = draftId(request.getId());
        Long receiptId = record(request.getId(), "DEPOSIT", "10.00");
        String rid = request.getId().toString();
        String receiptBody = receiptJson("DEPOSIT", "10.00", "UTR-" + suffix(), LocalDate.now().minusDays(1).toString());

        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.MARKETING_MANAGER, RoleCode.MARKETING_HEAD)) {
            call(post("/api/fleet-requests/" + rid + "/quotations/calculate"), role).andExpect(status().isForbidden());
            call(post("/api/quotations/" + quotationId + "/issue"), role).andExpect(status().isForbidden());
            call(post("/api/fleet-requests/" + rid + "/payment-receipts").contentType(MediaType.APPLICATION_JSON)
                    .content(receiptBody), role).andExpect(status().isForbidden());
            call(post("/api/payment-receipts/" + receiptId + "/approve"), role).andExpect(status().isForbidden());
            call(post("/api/fleet-requests/" + rid + "/grant-clearance"), role).andExpect(status().isForbidden());
        }
        // Reads: MH may view quotations and review deposits; MM and ADMIN may not.
        call(get("/api/quotations/" + quotationId), RoleCode.MARKETING_HEAD).andExpect(status().isOk());
        call(get("/api/fleet-requests/" + rid + "/payment-receipts"), RoleCode.MARKETING_HEAD).andExpect(status().isOk());
        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.MARKETING_MANAGER)) {
            call(get("/api/quotations/" + quotationId), role).andExpect(status().isForbidden());
            call(get("/api/fleet-requests/" + rid + "/quotations"), role).andExpect(status().isForbidden());
            call(get("/api/payment-receipts/" + receiptId), role).andExpect(status().isForbidden());
        }
        // Nothing changed.
        assertThat(quotations.findById(quotationId).orElseThrow().getStatus()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(receipts.findById(receiptId).orElseThrow().getStatus()).isEqualTo(ReceiptStatus.RECORDED);
        // SUPERVISOR can do everything Finance can.
        call(post("/api/quotations/" + draftId(request.getId()) + "/issue"), RoleCode.SUPERVISOR).andExpect(status().isOk());
    }

    // --- helpers ---

    private ResultActions calculate(Long requestId) throws Exception {
        return call(post("/api/fleet-requests/" + requestId + "/quotations/calculate"), RoleCode.FINANCE_TEAM);
    }

    private Long draftId(Long requestId) throws Exception {
        String json = calculate(requestId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Integer) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions issue(Long quotationId) throws Exception {
        return call(post("/api/quotations/" + quotationId + "/issue"), RoleCode.FINANCE_TEAM);
    }

    private Long record(Long requestId, String type, String amount) throws Exception {
        String json = receipt(requestId, type, amount, "UTR-" + suffix()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Integer) JsonPath.read(json, "$.id")).longValue();
    }

    private void record(Long requestId, String type, String amount,
            org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        receipt(requestId, type, amount, "UTR-" + suffix()).andExpect(expected);
    }

    private ResultActions receipt(Long requestId, String type, String amount, String ref) throws Exception {
        return call(post("/api/fleet-requests/" + requestId + "/payment-receipts").contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(type, amount, ref, LocalDate.now().minusDays(1).toString())), RoleCode.FINANCE_TEAM);
    }

    private void approve(Long receiptId) throws Exception {
        call(post("/api/payment-receipts/" + receiptId + "/approve"), RoleCode.FINANCE_TEAM).andExpect(status().isOk());
    }

    private static String receiptJson(String type, String amount, String ref, String date) {
        return """
                {"receiptType":"%s","paymentMethod":"NEFT","transactionReference":"%s","amount":%s,"receivedDate":"%s"}
                """.formatted(type, ref, amount, date);
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, RoleCode role) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, sessions.get(role).bearer()));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
