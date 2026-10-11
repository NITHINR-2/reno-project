package com.renault.fleet.dealerportal;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.entity.AuditLog;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.dealerportal.service.DealerPaymentService;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Dealer payment-proof uploads (pending receipts, type/size checks) and own-quotation PDF downloads. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DealerPaymentApiTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7\n% proof\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private FleetRequestRepository requests;
    @Autowired private PaymentReceiptRepository receipts;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private QuotationRepository quotations;

    private final Map<RoleCode, Session> hq = new EnumMap<>(RoleCode.class);
    private World a;
    private Session dealerA;
    private Session dealerB;
    private User creator;
    private int refNo;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        for (RoleCode role : List.of(RoleCode.MARKETING_MANAGER, RoleCode.FINANCE_TEAM, RoleCode.MARKETING_HEAD)) {
            User user = users.create(role);
            hq.put(role, loginOk(mockMvc, user.getUsername()));
            if (role == RoleCode.MARKETING_MANAGER) creator = user;
        }
        a = data.worldWithGst("0.1800");
        World b = data.world();
        dealerA = loginOk(mockMvc, dealerUser(users, a).getUsername());
        dealerB = loginOk(mockMvc, dealerUser(users, b).getUsername());
    }

    @Test
    void uploadingProofRecordsAPendingReceiptThatFinanceCanReviewAndApprove() throws Exception {
        FleetRequest request = withIssuedQuotation();
        String ref = nextRef();
        String json = upload(request.getId(), dealerA, file("bank slip.pdf", "application/pdf", PDF_BYTES), receipt(ref, "2500000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.hasProof").value(true))
                .andExpect(jsonPath("$.transactionReference").value(ref))
                .andReturn().getResponse().getContentAsString();
        long receiptId = ((Integer) JsonPath.read(json, "$.id")).longValue();
        assertThat(receipts.findById(receiptId).orElseThrow().getStatus()).isEqualTo(ReceiptStatus.RECORDED);
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(FleetRequestStatus.PAYMENT_PENDING);

        as(get("/api/dealer/requests/" + request.getId()), dealerA).andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.request.canUploadPaymentProof").value(true))
                .andExpect(jsonPath("$.quotation.totalAmount").value(70800000.00))
                .andExpect(jsonPath("$.receipts[?(@.id == %d)].hasProof".formatted(receiptId)).value(true));

        // Finance sees the same file, then approves; the deposit (1M + 2.5M of 5M) is still short.
        as(get("/api/payment-receipts/" + receiptId + "/proof"), hq.get(RoleCode.FINANCE_TEAM)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bank slip.pdf\""))
                .andExpect(content().bytes(PDF_BYTES));
        as(get("/api/payment-receipts/" + receiptId + "/proof"), dealerA).andExpect(status().isForbidden());
        as(post("/api/payment-receipts/" + receiptId + "/approve"), hq.get(RoleCode.FINANCE_TEAM)).andExpect(status().isOk());
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("PAYMENT_RECEIPT", String.valueOf(receiptId)))
                .extracting(AuditLog::getAction).containsExactly("CREATE", "APPROVE");

        // The same transaction reference can't be recorded twice.
        upload(request.getId(), dealerA, file("again.png", "image/png", PNG_BYTES), receipt(ref, "10.00"))
                .andExpect(status().isConflict());
    }

    @Test
    void uploadsAreCheckedByContentAndSize() throws Exception {
        FleetRequest request = withIssuedQuotation();
        long before = receipts.count();
        upload(request.getId(), dealerA, file("note.txt", "text/plain", "hello".getBytes()), receipt(nextRef(), "1.00"))
                .andExpect(status().isUnsupportedMediaType());
        // A renamed text file claiming to be a PDF is still rejected: the bytes decide.
        upload(request.getId(), dealerA, file("fake.pdf", "application/pdf", "not a pdf".getBytes()), receipt(nextRef(), "1.00"))
                .andExpect(status().isUnsupportedMediaType());
        byte[] tooBig = Arrays.copyOf(PDF_BYTES, DealerPaymentService.MAX_PROOF_BYTES + 1);
        upload(request.getId(), dealerA, file("big.pdf", "application/pdf", tooBig), receipt(nextRef(), "1.00"))
                .andExpect(status().isContentTooLarge());
        upload(request.getId(), dealerA, file("empty.pdf", "application/pdf", new byte[0]), receipt(nextRef(), "1.00"))
                .andExpect(status().isBadRequest());
        upload(request.getId(), dealerA, file("p.pdf", "application/pdf", PDF_BYTES), receipt(nextRef(), "-5"))
                .andExpect(status().isBadRequest());
        assertThat(receipts.count()).isEqualTo(before);

        byte[] exactlyMax = Arrays.copyOf(PDF_BYTES, DealerPaymentService.MAX_PROOF_BYTES);
        upload(request.getId(), dealerA, file("max.pdf", "application/pdf", exactlyMax), receipt(nextRef(), "1.00"))
                .andExpect(status().isCreated());
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
        upload(request.getId(), dealerA, file("../../etc/photo.jpg", "image/jpeg", jpeg), receipt(nextRef(), "1.00"))
                .andExpect(status().isCreated());
    }

    @Test
    void proofsAreOnlyAcceptedForOwnRequestsAwaitingPayment() throws Exception {
        FleetRequest inReview = data.request(a, 5, creator);
        upload(inReview.getId(), dealerA, file("p.pdf", "application/pdf", PDF_BYTES), receipt(nextRef(), "1.00"))
                .andExpect(status().isConflict());
        FleetRequest awaiting = withIssuedQuotation();
        upload(awaiting.getId(), dealerB, file("p.pdf", "application/pdf", PDF_BYTES), receipt(nextRef(), "1.00"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDealerDownloadsOnlyItsOwnIssuedQuotation() throws Exception {
        FleetRequest request = withIssuedQuotation();
        byte[] pdf = as(get("/api/dealer/requests/" + request.getId() + "/quotation/pdf"), dealerA)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        Long quotationId = quotations.findFirstByFleetRequestIdAndStatus(request.getId(), QuotationStatus.ISSUED)
                .orElseThrow().getId();
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("QUOTATION", String.valueOf(quotationId)))
                .filteredOn(l -> l.getAction().equals("QUOTATION_PDF_DOWNLOADED"))
                .extracting(AuditLog::getActorRole).containsExactly("DEALER");

        as(get("/api/dealer/requests/" + request.getId() + "/quotation/pdf"), dealerB).andExpect(status().isNotFound());
        as(get("/api/quotations/" + quotationId + "/pdf"), dealerA).andExpect(status().isForbidden());

        // A draft is never offered to the dealer.
        FleetRequest draftOnly = data.request(a, 10, FleetRequestStatus.PENDING_FINANCE_CLEARANCE, creator, true);
        as(post("/api/fleet-requests/" + draftOnly.getId() + "/quotations/calculate"), hq.get(RoleCode.FINANCE_TEAM))
                .andExpect(status().isCreated());
        as(get("/api/dealer/requests/" + draftOnly.getId() + "/quotation/pdf"), dealerA).andExpect(status().isNotFound());
        as(get("/api/dealer/requests/" + draftOnly.getId()), dealerA).andExpect(jsonPath("$.quotation").doesNotExist());
    }

    // --- helpers ---

    /** FLT-2026-0891 figures: Finance records and approves a ₹10,00,000 deposit, then issues → PAYMENT_PENDING. */
    private FleetRequest withIssuedQuotation() throws Exception {
        FleetRequest request = data.request(a, 50, FleetRequestStatus.PENDING_FINANCE_CLEARANCE, creator, true,
                new BigDecimal("5000000.00"));
        String receipt = as(post("/api/fleet-requests/" + request.getId() + "/payment-receipts")
                .contentType(MediaType.APPLICATION_JSON).content(receipt(nextRef(), "1000000.00")),
                hq.get(RoleCode.FINANCE_TEAM)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        as(post("/api/payment-receipts/" + JsonPath.read(receipt, "$.id") + "/approve"), hq.get(RoleCode.FINANCE_TEAM))
                .andExpect(status().isOk());
        String draft = as(post("/api/fleet-requests/" + request.getId() + "/quotations/calculate"), hq.get(RoleCode.FINANCE_TEAM))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        as(post("/api/quotations/" + JsonPath.read(draft, "$.id") + "/issue"), hq.get(RoleCode.FINANCE_TEAM))
                .andExpect(status().isOk());
        return request;
    }

    private User dealerUser(TestUsers users, World world) {
        User user = users.create(RoleCode.DEALER);
        user.setDealer(world.dealer());
        return userRepository.save(user);
    }

    private String nextRef() {
        return "UTR-DLR-" + System.nanoTime() + "-" + (++refNo);
    }

    private static String receipt(String ref, String amount) {
        return """
                {"receiptType":"DEPOSIT","paymentMethod":"NEFT","transactionReference":"%s","amount":%s,"receivedDate":"%s"}
                """.formatted(ref, amount, LocalDate.now().minusDays(1));
    }

    private static MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    private ResultActions upload(Long requestId, Session session, MockMultipartFile file, String receiptJson) throws Exception {
        return mockMvc.perform(multipart("/api/dealer/requests/" + requestId + "/payment-proofs").file(file)
                .file(new MockMultipartFile("receipt", "", MediaType.APPLICATION_JSON_VALUE,
                        receiptJson.getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }

    private ResultActions as(MockHttpServletRequestBuilder builder, Session session) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }
}
