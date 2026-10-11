package com.renault.fleet.quotation;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.entity.AuditLog;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.quotation.controller.QuotationDocumentController;
import com.renault.fleet.quotation.entity.Quotation;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Quotation PDF download, frozen issue snapshots, and the public, rate-limited verification endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuotationDocumentApiTest {

    private static final BigDecimal REQUIRED = new BigDecimal("5000000.00");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private QuotationRepository quotations;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private JdbcTemplate jdbc;

    private final Map<RoleCode, Session> sessions = new EnumMap<>(RoleCode.class);
    private User creator;
    private int clientNo;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        for (RoleCode role : RoleCode.values()) {
            User user = users.create(role);
            sessions.put(role, loginOk(mockMvc, user.getUsername()));
            if (role == RoleCode.MARKETING_MANAGER) creator = user;
        }
    }

    @Test
    void issuingFreezesASnapshotTokenAndPdfHashThatDownloadsReproduce() throws Exception {
        World world = data.worldWithGst("0.1800");
        Long id = issued(world);
        Quotation quotation = quotations.findById(id).orElseThrow();
        assertThat(quotation.getVerificationToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(quotation.getPdfSha256()).matches("[0-9a-f]{64}");
        assertThat(quotation.getSnapshotJson()).contains("\"http://portal.test/verify/quotation/"
                + quotation.getVerificationToken() + "\"", "\"draft\": false", "\"totalAmount\": 70800000.00");
        call(get("/api/quotations/" + id), RoleCode.FINANCE_TEAM)
                .andExpect(jsonPath("$.pdfSha256").value(quotation.getPdfSha256()));

        MvcResult first = pdf(id, RoleCode.FINANCE_TEAM).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + quotation.getQuotationNumber() + ".pdf\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(QuotationDocumentController.SHA256_HEADER, quotation.getPdfSha256()))
                .andExpect(header().string(QuotationDocumentController.ISSUED_SHA256_HEADER, quotation.getPdfSha256()))
                .andReturn();
        String text = text(first.getResponse().getContentAsByteArray());
        assertThat(text).contains(quotation.getQuotationNumber(), world.dealer().getName(), "₹12,00,000.00",
                "₹6,00,00,000.00", "GST @ 18%", "₹1,08,00,000.00", "₹7,08,00,000.00", "₹40,00,000.00",
                "Indian Rupees Seven Crore Eight Lakh Only", "TAX QUOTATION",
                "http://portal.test/verify/quotation/" + quotation.getVerificationToken());
        assertThat(text).doesNotContain("DRAFT");

        // Price and dealer change after issue: the issued document must not move.
        jdbc.update("update vehicle_variants set unit_price = 1500000.00 where id = ?", world.variant().getId());
        jdbc.update("update dealers set name = 'Renamed Dealer' where id = ?", world.dealer().getId());
        byte[] again = pdf(id, RoleCode.SUPERVISOR).andExpect(status().isOk())
                .andExpect(header().string(QuotationDocumentController.SHA256_HEADER, quotation.getPdfSha256()))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(again).isEqualTo(first.getResponse().getContentAsByteArray());
        assertThat(text(again)).contains(world.dealer().getName(), "₹7,08,00,000.00").doesNotContain("Renamed Dealer");

        List<AuditLog> downloads = auditLogs.findByEntityTypeAndEntityIdOrderById("QUOTATION", String.valueOf(id))
                .stream().filter(a -> a.getAction().equals("QUOTATION_PDF_DOWNLOADED")).toList();
        assertThat(downloads).hasSize(2);
        assertThat(downloads.getFirst().getActorUsername()).isNotBlank();
        assertThat(downloads.getFirst().getAfterJson()).contains("\"matchesIssuedHash\": true")
                .contains(quotation.getPdfSha256());
    }

    @Test
    void draftPdfIsWatermarkedHasNoQrAndIsNotVerifiable() throws Exception {
        FleetRequest request = financeRequest(data.worldWithGst("0.1800"));
        Long id = draftId(request.getId());
        Quotation draft = quotations.findById(id).orElseThrow();
        assertThat(draft.getVerificationToken()).isNull();
        assertThat(draft.getSnapshotJson()).isNull();

        MvcResult result = pdf(id, RoleCode.FINANCE_TEAM).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + draft.getQuotationNumber() + "-DRAFT.pdf\""))
                .andExpect(header().doesNotExist(QuotationDocumentController.ISSUED_SHA256_HEADER))
                .andReturn();
        String text = text(result.getResponse().getContentAsByteArray());
        assertThat(text).contains("DRAFT QUOTATION", "Not issued (draft)", "₹7,08,00,000.00")
                .doesNotContain("/verify/quotation/");
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("QUOTATION", String.valueOf(id)))
                .anyMatch(a -> a.getAction().equals("QUOTATION_PDF_DOWNLOADED"));
    }

    @Test
    void pdfAccessFollowsQuotationRead() throws Exception {
        Long id = issued(data.worldWithGst("0.1800"));
        pdf(id, RoleCode.FINANCE_TEAM).andExpect(status().isOk());
        pdf(id, RoleCode.SUPERVISOR).andExpect(status().isOk());
        pdf(id, RoleCode.MARKETING_HEAD).andExpect(status().isOk());
        pdf(id, RoleCode.ADMIN).andExpect(status().isForbidden());
        pdf(id, RoleCode.MARKETING_MANAGER).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/quotations/" + id + "/pdf")).andExpect(status().isUnauthorized());
        pdf(Long.MAX_VALUE, RoleCode.FINANCE_TEAM).andExpect(status().isNotFound());
    }

    @Test
    void publicVerificationShowsOnlyTheMinimalFieldsWithoutAuthentication() throws Exception {
        World world = data.worldWithGst("0.1800");
        Long id = issued(world);
        Quotation quotation = quotations.findById(id).orElseThrow();
        String body = verify(quotation.getVerificationToken(), nextClient())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.result").value("GENUINE"))
                .andExpect(jsonPath("$.quotationNumber").value(quotation.getQuotationNumber()))
                .andExpect(jsonPath("$.issueDate").value(quotation.getIssueDate().toString()))
                .andExpect(jsonPath("$.totalAmount").value(70800000.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> fields = JsonPath.read(body, "$");
        assertThat(fields.keySet()).containsExactlyInAnyOrder("result", "quotationNumber", "issueDate", "totalAmount",
                "currency", "status");
        assertThat(body).doesNotContain(world.dealer().getName(), "deposit", "Deposit", "contact");
    }

    @Test
    void unknownMalformedAndDraftCodesAreNotFound() throws Exception {
        String client = nextClient();
        verify("A".repeat(43), client).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No quotation matches this verification code."));
        verify("short", client).andExpect(status().isNotFound());
        verify("bad.token.with.dots.padding.padding", client).andExpect(status().isNotFound());
        verify("bad%27token%3B--padding-padding", client).andExpect(status().isBadRequest()); // firewall

        // A token planted on a DRAFT (impossible through the API; the DB check forbids it) must never verify.
        Long draft = draftId(financeRequest(data.worldWithGst("0.1800")).getId());
        assertThatThrownBy(() -> jdbc.update("update quotations set verification_token = ? where id = ?",
                "D".repeat(43), draft)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void publicVerificationIsRateLimitedPerClient() throws Exception {
        String client = nextClient();
        int limited = 0;
        String retryAfter = null;
        // Limit is 5/min in the test profile; 11 calls cross at most one window boundary, so at least one is refused.
        for (int i = 0; i < 11; i++) {
            MvcResult result = verify("Z".repeat(43), client).andReturn();
            if (result.getResponse().getStatus() == 429) {
                limited++;
                retryAfter = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
                assertThat(result.getResponse().getContentAsString()).contains("Too Many Requests");
            } else {
                assertThat(result.getResponse().getStatus()).isEqualTo(404);
            }
        }
        assertThat(limited).isPositive();
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 60);
        verify("Z".repeat(43), nextClient()).andExpect(status().isNotFound()); // other clients are unaffected
    }

    @Test
    void theFrozenSnapshotIsImmutableInTheDatabase() throws Exception {
        Long id = issued(data.worldWithGst("0.1800"));
        assertThatThrownBy(() -> jdbc.update("update quotations set snapshot_json = '{}'::jsonb where id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update quotations set pdf_sha256 = ? where id = ?", "0".repeat(64), id))
                .isInstanceOf(DataAccessException.class);
        // Other columns (payment status moves with receipts) stay writable.
        assertThat(jdbc.update("update quotations set payment_status = 'UNPAID' where id = ?", id)).isEqualTo(1);
    }

    @Test
    void quotationsIssuedBeforeV11AreFrozenOnFirstDownload() throws Exception {
        Long id = issued(data.worldWithGst("0.1800"));
        // Simulate a pre-V11 row: the trigger guards a written snapshot, so bypass it for this setup only.
        jdbc.execute("alter table quotations disable trigger trg_quotations_freeze_snapshot");
        try {
            jdbc.update("update quotations set snapshot_json = null, pdf_sha256 = null where id = ?", id);
        } finally {
            jdbc.execute("alter table quotations enable trigger trg_quotations_freeze_snapshot");
        }
        String hash = pdf(id, RoleCode.FINANCE_TEAM).andExpect(status().isOk())
                .andReturn().getResponse().getHeader(QuotationDocumentController.SHA256_HEADER);
        Quotation frozen = quotations.findById(id).orElseThrow();
        assertThat(frozen.getSnapshotJson()).isNotNull();
        assertThat(frozen.getPdfSha256()).isEqualTo(hash);
        pdf(id, RoleCode.FINANCE_TEAM).andExpect(header().string(QuotationDocumentController.SHA256_HEADER, hash));
    }

    // --- helpers ---

    private FleetRequest financeRequest(World world) {
        return data.request(world, 50, FleetRequestStatus.PENDING_FINANCE_CLEARANCE, creator, true, REQUIRED);
    }

    /** FLT-2026-0891 figures with ₹10,00,000 of the ₹50,00,000 deposit approved, issued by Finance. */
    private Long issued(World world) throws Exception {
        FleetRequest request = financeRequest(world);
        String receipt = call(post("/api/fleet-requests/" + request.getId() + "/payment-receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"receiptType":"DEPOSIT","paymentMethod":"NEFT","transactionReference":"UTR-DOC-%d-%d","amount":1000000.00,"receivedDate":"%s"}
                        """.formatted(request.getId(), System.nanoTime(), java.time.LocalDate.now().minusDays(1))),
                RoleCode.FINANCE_TEAM).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        call(post("/api/payment-receipts/" + JsonPath.read(receipt, "$.id") + "/approve"), RoleCode.FINANCE_TEAM)
                .andExpect(status().isOk());
        Long id = draftId(request.getId());
        call(post("/api/quotations/" + id + "/issue"), RoleCode.FINANCE_TEAM).andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfSha256").isString());
        return id;
    }

    private Long draftId(Long requestId) throws Exception {
        String json = call(post("/api/fleet-requests/" + requestId + "/quotations/calculate"), RoleCode.FINANCE_TEAM)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Integer) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions pdf(Long id, RoleCode role) throws Exception {
        return call(get("/api/quotations/" + id + "/pdf"), role);
    }

    private ResultActions verify(String token, String client) throws Exception {
        return mockMvc.perform(get("/api/public/quotations/verify/" + token).with(r -> {
            r.setRemoteAddr(client);
            return r;
        }));
    }

    private String nextClient() {
        return "10.66.0." + (++clientNo);
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, RoleCode role) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, sessions.get(role).bearer()));
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
