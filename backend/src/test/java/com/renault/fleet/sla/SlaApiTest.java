package com.renault.fleet.sla;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.entity.AuditLog;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.sla.entity.SlaPolicy;
import com.renault.fleet.sla.repository.SlaPolicyRepository;
import com.renault.fleet.sla.service.SlaBreachJob;
import com.renault.fleet.sla.service.SlaBreachService;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlaApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FleetRequestRepository requests;
    @Autowired private SlaPolicyRepository policies;
    @Autowired private SlaBreachJob job;
    @Autowired private SlaBreachService breaches;
    @Autowired private AuditLogRepository auditLogs;

    private User manager;
    private Session mm;
    private Session mh;
    private Session sup;
    private Session admin;
    private World world;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        manager = users.create(RoleCode.MARKETING_MANAGER);
        mm = loginOk(mockMvc, manager.getUsername());
        mh = loginOk(mockMvc, users.create(RoleCode.MARKETING_HEAD).getUsername());
        sup = loginOk(mockMvc, users.create(RoleCode.SUPERVISOR).getUsername());
        admin = loginOk(mockMvc, users.create(RoleCode.ADMIN).getUsername());
        world = data.world();
    }

    @Test
    void eachStageStartsItsOwnClockFromThePolicyAndUntimedOrTerminalStagesHaveNone() throws Exception {
        Instant before = Instant.now();
        long id = create();
        Map<String, Object> d = detail(id);
        assertClock(d, before, 48);
        assertThat(d.get("slaStatus")).isEqualTo("ON_TRACK");

        send(put("/api/fleet-requests/" + id + "/stock-allocations"), "{\"version\":" + version(id) + ",\"lines\":[]}", mm)
                .andExpect(status().isOk());
        before = Instant.now();
        transition(id, "submit-for-qualification", mm, null);
        assertClock(detail(id), before, 24);

        before = Instant.now();
        transition(id, "forward-to-marketing-head", mm, null);
        assertClock(detail(id), before, 48);

        // RETURNED_TO_MARKETING has no policy: the stage is timed from entry but has no SLA.
        transition(id, "reject", mh, "Revise the deposit");
        d = detail(id);
        assertThat(d.get("stageEnteredAt")).isNotNull();
        assertThat(d.get("slaDueAt")).isNull();
        assertThat(d.get("slaStatus")).isNull();

        transition(id, "cancel", sup, "Dealer withdrew");
        d = detail(id);
        assertThat(d.get("status")).isEqualTo("CANCELLED");
        assertThat(d.get("slaDueAt")).isNull();
        assertThat(d.get("slaStatus")).isNull();
    }

    @Test
    void anInactivePolicyStartsNoClockAndPolicyEditsDoNotMoveRunningClocks() throws Exception {
        long running = create();
        Instant dueBefore = Instant.parse((String) detail(running).get("slaDueAt"));
        SlaPolicy review = policies.findByStage(FleetRequestStatus.PENDING_MARKETING_REVIEW).orElseThrow();
        try {
            patchPolicy(review.getId(), "{\"version\":%d,\"hoursAllowed\":10}".formatted(review.getVersion()), sup).andExpect(status().isOk());
            assertThat(Instant.parse((String) detail(running).get("slaDueAt"))).isEqualTo(dueBefore);
            Instant before = Instant.now();
            assertClock(detail(create()), before, 10);

            long v = policies.findById(review.getId()).orElseThrow().getVersion();
            patchPolicy(review.getId(), "{\"version\":%d,\"active\":false}".formatted(v), sup).andExpect(status().isOk());
            Map<String, Object> d = detail(create());
            assertThat(d.get("slaDueAt")).isNull();
            assertThat(d.get("slaStatus")).isNull();
        } finally {
            SlaPolicy p = policies.findById(review.getId()).orElseThrow();
            p.setHoursAllowed(48);
            p.setActive(true);
            policies.saveAndFlush(p);
        }
    }

    @Test
    void statusIsComputedByTheServerAndFiltersAndSummaryAgree() throws Exception {
        World own = data.world();
        Map<String, Object> summaryBefore = summary();
        FleetRequest breached = clocked(own, FleetRequestStatus.PENDING_MARKETING_REVIEW, Duration.ofHours(-50), 48);
        FleetRequest atRisk = clocked(own, FleetRequestStatus.PAYMENT_PENDING, Duration.ofHours(-130), 168);
        FleetRequest onTrack = clocked(own, FleetRequestStatus.FUNDS_VERIFIED, Duration.ofHours(-1), 24);

        assertThat(detail(breached.getId()).get("slaStatus")).isEqualTo("BREACHED");
        assertThat(detail(atRisk.getId()).get("slaStatus")).isEqualTo("AT_RISK");
        assertThat(detail(onTrack.getId()).get("slaStatus")).isEqualTo("ON_TRACK");

        String base = "/api/fleet-requests?dealerId=" + own.dealer().getId();
        assertThat(numbers(base + "&sla=BREACHED")).containsExactly(breached.getRequestNumber());
        assertThat(numbers(base + "&sla=AT_RISK")).containsExactly(atRisk.getRequestNumber());
        assertThat(numbers(base + "&sla=ON_TRACK")).containsExactly(onTrack.getRequestNumber());
        assertThat(numbers(base + "&sla=AT_RISK&sla=BREACHED")).containsExactlyInAnyOrder(breached.getRequestNumber(), atRisk.getRequestNumber());
        call(get(base + "&sla=BREACHED"), mm).andExpect(jsonPath("$.content[0].slaStatus").value("BREACHED"));
        call(get(base + "&sla=NOPE"), mm).andExpect(status().isBadRequest());

        Map<String, Object> after = summary();
        assertThat(delta(summaryBefore, after, "slaBreached")).isEqualTo(1);
        assertThat(delta(summaryBefore, after, "slaAtRisk")).isEqualTo(1);
        assertThat(stageDelta(summaryBefore, after, "marketing", "breached")).isEqualTo(1);
        assertThat(stageDelta(summaryBefore, after, "finance", "atRisk")).isEqualTo(1);
        assertThat(stageDelta(summaryBefore, after, "finance", "breached")).isZero();
    }

    @Test
    void theScanRecordsEachBreachExactlyOnceAndAgainOnlyAfterTheStageIsReEntered() throws Exception {
        FleetRequest r = clocked(data.world(), FleetRequestStatus.PENDING_EXECUTIVE_APPROVAL, Duration.ofHours(-49), 48);
        long versionBefore = version(r.getId());

        assertThat(job.scan()).isGreaterThanOrEqualTo(1);
        assertThat(job.scan()).isZero();
        List<Map<String, Object>> rows = breachRows(r.getId());
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("from_status")).isEqualTo("PENDING_EXECUTIVE_APPROVAL");
            assertThat(row.get("to_status")).isEqualTo("PENDING_EXECUTIVE_APPROVAL");
            assertThat(row.get("actor_id")).isNull();
        });
        assertThat(requests.findById(r.getId()).orElseThrow().getSlaBreachedAt()).isNotNull();
        // The scan must not make users' next edit a stale-version 409.
        assertThat(version(r.getId())).isEqualTo(versionBefore);
        call(get("/api/fleet-requests/" + r.getId() + "/history"), mm)
                .andExpect(jsonPath("$.content[?(@.action == 'SLA_BREACHED')].actorName")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));

        // Re-entering a stage starts a fresh clock, which can breach (once) again.
        transition(r.getId(), "reject", mh, "Rework");
        jdbc.update("update fleet_requests set status = 'PENDING_EXECUTIVE_APPROVAL', stage_entered_at = ?, sla_warning_at = ?, sla_due_at = ?, sla_breached_at = null where id = ?",
                ts(Duration.ofHours(-60)), ts(Duration.ofHours(-24)), ts(Duration.ofHours(-12)), r.getId());
        job.scan();
        job.scan();
        assertThat(breachRows(r.getId())).hasSize(2);
    }

    @Test
    void concurrentScannersRecordOneBreachAndTheDatabaseRefusesADuplicate() throws Exception {
        FleetRequest r = clocked(data.world(), FleetRequestStatus.FINANCIALLY_CLEARED, Duration.ofHours(-80), 72);
        int threads = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Callable<Boolean> attempt = () -> {
                start.await();
                return breaches.recordIfBreached(r.getId());
            };
            List<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(attempt));
            }
            start.countDown();
            long winners = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(breachRows(r.getId())).hasSize(1);

        Map<String, Object> row = breachRows(r.getId()).getFirst();
        assertThatThrownBy(() -> jdbc.update(
                "insert into workflow_history (fleet_request_id, from_status, to_status, action, metadata) values (?, ?, ?, 'SLA_BREACHED', ?::jsonb)",
                r.getId(), row.get("from_status"), row.get("to_status"), row.get("metadata").toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyASupervisorReadsOrEditsPoliciesAndEveryEditIsAudited() throws Exception {
        String list = call(get("/api/admin/sla-policies"), sup).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7))
                .andReturn().getResponse().getContentAsString();
        Map<String, Integer> hours = Map.of("PENDING_MARKETING_REVIEW", 48, "MARKETING_QUALIFIED", 24, "PENDING_EXECUTIVE_APPROVAL", 48,
                "PENDING_FINANCE_CLEARANCE", 72, "PAYMENT_PENDING", 168, "FUNDS_VERIFIED", 24, "FINANCIALLY_CLEARED", 72);
        hours.forEach((stage, h) -> {
            assertThat(JsonPath.<List<Integer>>read(list, "$.content[?(@.stage == '" + stage + "')].hoursAllowed")).containsExactly(h);
            assertThat(JsonPath.<List<Integer>>read(list, "$.content[?(@.stage == '" + stage + "')].warningThresholdPct")).containsExactly(75);
        });

        for (Session other : List.of(admin, mm, mh)) {
            call(get("/api/admin/sla-policies"), other).andExpect(status().isForbidden());
        }
        SlaPolicy funds = policies.findByStage(FleetRequestStatus.FUNDS_VERIFIED).orElseThrow();
        String body = "{\"version\":%d,\"hoursAllowed\":30}".formatted(funds.getVersion());
        patchPolicy(funds.getId(), body, admin).andExpect(status().isForbidden());
        patchPolicy(funds.getId(), "{\"version\":%d,\"hoursAllowed\":0}".formatted(funds.getVersion()), sup).andExpect(status().isBadRequest());
        patchPolicy(funds.getId(), "{\"version\":%d,\"warningThresholdPct\":100}".formatted(funds.getVersion()), sup).andExpect(status().isBadRequest());
        patchPolicy(funds.getId(), "{\"hoursAllowed\":30}", sup).andExpect(status().isBadRequest());

        try {
            patchPolicy(funds.getId(), body, sup).andExpect(status().isOk())
                    .andExpect(jsonPath("$.hoursAllowed").value(30))
                    .andExpect(jsonPath("$.warningThresholdPct").value(75));
            patchPolicy(funds.getId(), body, sup).andExpect(status().isConflict());

            List<AuditLog> audit = auditLogs.findByEntityTypeAndEntityIdOrderById("SLA_POLICY", "FUNDS_VERIFIED");
            assertThat(audit).last().satisfies(a -> {
                assertThat(a.getAction()).isEqualTo("SLA_POLICY_UPDATE");
                assertThat((Integer) JsonPath.read(a.getBeforeJson(), "$.hoursAllowed")).isEqualTo(24);
                assertThat((Integer) JsonPath.read(a.getAfterJson(), "$.hoursAllowed")).isEqualTo(30);
            });
        } finally {
            SlaPolicy p = policies.findById(funds.getId()).orElseThrow();
            p.setHoursAllowed(24);
            policies.saveAndFlush(p);
        }
        assertThatThrownBy(() -> jdbc.update("update sla_policies set warning_threshold_pct = 0 where id = ?", funds.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into sla_policies (stage, hours_allowed) values ('COMPLETED', 5)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- helpers -------------------------------------------------------------------------------------------

    /** A fixture request whose stage started {@code enteredAgo} ago with the given hours at 75%. */
    private FleetRequest clocked(World w, FleetRequestStatus status, Duration enteredAgo, int hours) {
        FleetRequest r = data.request(w, 5, status, manager, true);
        Instant entered = Instant.now().plus(enteredAgo);
        jdbc.update("update fleet_requests set stage_entered_at = ?, sla_warning_at = ?, sla_due_at = ? where id = ?",
                Timestamp.from(entered), Timestamp.from(entered.plus(Duration.ofMinutes(hours * 60L * 75 / 100))),
                Timestamp.from(entered.plus(Duration.ofHours(hours))), r.getId());
        return r;
    }

    private static Timestamp ts(Duration fromNow) {
        return Timestamp.from(Instant.now().plus(fromNow));
    }

    private List<Map<String, Object>> breachRows(long id) {
        return jdbc.queryForList("select * from workflow_history where fleet_request_id = ? and action = 'SLA_BREACHED' order by id", id);
    }

    private long create() throws Exception {
        String body = """
                {"dealerId":%d,"variantId":%d,"quantity":10,"requiredDeposit":"100000.00"}
                """.formatted(world.dealer().getId(), world.variant().getId());
        String json = send(post("/api/fleet-requests"), body, mm).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private void transition(long id, String endpoint, Session session, String reason) throws Exception {
        String body = reason == null ? "{\"version\":%d}".formatted(version(id)) : "{\"version\":%d,\"reason\":\"%s\"}".formatted(version(id), reason);
        send(post("/api/fleet-requests/" + id + "/" + endpoint), body, session).andExpect(status().isOk());
    }

    private static void assertClock(Map<String, Object> d, Instant notBefore, int hours) {
        Instant entered = Instant.parse((String) d.get("stageEnteredAt"));
        assertThat(entered).isBetween(notBefore.minusSeconds(1), Instant.now().plusSeconds(1));
        assertThat(Instant.parse((String) d.get("slaDueAt"))).isEqualTo(entered.plus(Duration.ofHours(hours)));
        assertThat(Instant.parse((String) d.get("slaWarningAt"))).isEqualTo(entered.plus(Duration.ofMinutes(hours * 60L * 75 / 100)));
    }

    private Map<String, Object> detail(long id) throws Exception {
        return JsonPath.read(call(get("/api/fleet-requests/" + id), sup).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$");
    }

    private long version(long id) throws Exception {
        return ((Number) detail(id).get("version")).longValue();
    }

    private List<String> numbers(String url) throws Exception {
        return JsonPath.read(call(get(url), mm).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.content[*].requestNumber");
    }

    private Map<String, Object> summary() throws Exception {
        return JsonPath.read(call(get("/api/fleet-requests/summary"), mh).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$");
    }

    private static long delta(Map<String, Object> before, Map<String, Object> after, String key) {
        return ((Number) after.get(key)).longValue() - ((Number) before.get(key)).longValue();
    }

    @SuppressWarnings("unchecked")
    private static long stageDelta(Map<String, Object> before, Map<String, Object> after, String stage, String key) {
        Map<String, Object> b = (Map<String, Object>) ((Map<String, Object>) before.get("slaByStage")).get(stage);
        Map<String, Object> a = (Map<String, Object>) ((Map<String, Object>) after.get("slaByStage")).get(stage);
        return ((Number) a.get(key)).longValue() - ((Number) b.get(key)).longValue();
    }

    private ResultActions patchPolicy(long id, String body, Session session) throws Exception {
        return send(patch("/api/admin/sla-policies/" + id), body, session);
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String body, Session session) throws Exception {
        return call(builder.contentType(MediaType.APPLICATION_JSON).content(body), session);
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, Session session) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }
}
