package com.renault.fleet.analytics;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Executive analytics over a hand-built fixture in its own plant (so other tests' data never leaks in). Every
 * expected figure below is worked out by hand from the fixture; June 2025 is the period, May 2025 the previous one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutiveAnalyticsApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private PlantRepository plants;
    @Autowired private JdbcTemplate jdbc;

    private final Map<RoleCode, Session> sessions = new EnumMap<>(RoleCode.class);
    private User creator;
    private Plant plant;
    private World a;
    private World b;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        for (RoleCode role : RoleCode.values()) {
            User user = users.create(role);
            sessions.put(role, loginOk(mockMvc, user.getUsername()));
            if (role == RoleCode.MARKETING_MANAGER) creator = user;
        }
        String s = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        plant = plants.save(new Plant("P-" + s, "Plant " + s, "Pune", "Maharashtra"));
        a = data.world();
        b = data.worldWithGst("0.1800");

        // A1: 10 units (4 from stock), completed; cleared 120 h after creation; MQ ran 48 h against a 24 h SLA.
        long a1 = request(a, 10, FleetRequestStatus.COMPLETED, "2025-06-05T10:00", 4);
        history(a1, null, "PENDING_MARKETING_REVIEW", "CREATED", "2025-06-05T10:00");
        history(a1, "PENDING_MARKETING_REVIEW", "MARKETING_QUALIFIED", "QUALIFY", "2025-06-06T10:00");
        history(a1, "MARKETING_QUALIFIED", "MARKETING_QUALIFIED", "SLA_BREACHED", "2025-06-07T10:03");
        history(a1, "MARKETING_QUALIFIED", "PENDING_EXECUTIVE_APPROVAL", "FORWARD_TO_EXECUTIVE", "2025-06-08T10:00");
        history(a1, "PENDING_EXECUTIVE_APPROVAL", "EXECUTIVE_APPROVED", "APPROVE", "2025-06-09T10:00");
        history(a1, "EXECUTIVE_APPROVED", "PENDING_FINANCE_CLEARANCE", "FORWARD_TO_FINANCE", "2025-06-09T12:00");
        history(a1, "PENDING_FINANCE_CLEARANCE", "FINANCIALLY_CLEARED", "GRANT_CLEARANCE", "2025-06-10T10:00");
        history(a1, "FINANCIALLY_CLEARED", "COMPLETED", "COMPLETE", "2025-06-12T12:00");
        // A2: 5 units, still in marketing review (open since June, so its SLA is decided: breached).
        long a2 = request(a, 5, FleetRequestStatus.PENDING_MARKETING_REVIEW, "2025-06-20T09:00", 0);
        history(a2, null, "PENDING_MARKETING_REVIEW", "CREATED", "2025-06-20T09:00");
        // B1: 20 units, cancelled after 24 h: counted as a request, never as value or demand.
        long b1 = request(b, 20, FleetRequestStatus.CANCELLED, "2025-06-15T09:00", 0);
        history(b1, null, "PENDING_MARKETING_REVIEW", "CREATED", "2025-06-15T09:00");
        history(b1, "PENDING_MARKETING_REVIEW", "CANCELLED", "CANCEL", "2025-06-16T09:00");
        // B2: 30 units all from stock, waiting in finance; its issued quotation's base (₹3 Cr) is the order value.
        long b2 = request(b, 30, FleetRequestStatus.PENDING_FINANCE_CLEARANCE, "2025-06-25T10:00", 30);
        history(b2, null, "PENDING_MARKETING_REVIEW", "CREATED", "2025-06-25T10:00");
        history(b2, "PENDING_MARKETING_REVIEW", "PENDING_FINANCE_CLEARANCE", "FORWARD_TO_FINANCE", "2025-06-26T10:00");
        issuedQuotation(b2, b, "30000000.00");
        // Previous period (May): A3, 2 units. Earlier (April): A4, 1 unit, only in the 12-month series.
        long a3 = request(a, 2, FleetRequestStatus.PENDING_MARKETING_REVIEW, "2025-05-20T10:00", 0);
        history(a3, null, "PENDING_MARKETING_REVIEW", "CREATED", "2025-05-20T10:00");
        request(a, 1, FleetRequestStatus.PENDING_MARKETING_REVIEW, "2025-04-10T10:00", 0);
    }

    @Test
    void kpisCompareWithThePreviousPeriod() throws Exception {
        june().andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value("2025-06-01"))
                .andExpect(jsonPath("$.period.previousFrom").value("2025-05-02"))
                .andExpect(jsonPath("$.period.previousTo").value("2025-05-31"))
                .andExpect(jsonPath("$.kpis.totalOrderValue.value").value(48000000.00))
                .andExpect(jsonPath("$.kpis.totalOrderValue.previous").value(2400000.00))
                .andExpect(jsonPath("$.kpis.totalOrderValue.changePct").value(1900.0))
                .andExpect(jsonPath("$.kpis.requestCount.value").value(4))
                .andExpect(jsonPath("$.kpis.requestCount.changePct").value(300.0))
                .andExpect(jsonPath("$.kpis.avgHoursToClearance.value").value(120.0))
                .andExpect(jsonPath("$.kpis.avgHoursToClearance.previous").value(nullValue()))
                .andExpect(jsonPath("$.kpis.avgHoursToClearance.changePct").value(nullValue()))
                // 9 decided timed stage visits entered in June, 6 within SLA.
                .andExpect(jsonPath("$.kpis.slaCompliancePct.value").value(66.7))
                .andExpect(jsonPath("$.kpis.overdueRequests.value").value(1))
                .andExpect(jsonPath("$.kpis.overdueRequests.previous").value(0))
                .andExpect(jsonPath("$.kpis.overdueRequests.changePct").value(nullValue()));
    }

    @Test
    void chartsAreComputedPerWidget() throws Exception {
        june().andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyOrderValue", hasSize(12)))
                .andExpect(jsonPath("$.monthlyOrderValue[0].month").value("2024-07"))
                .andExpect(jsonPath("$.monthlyOrderValue[9].month").value("2025-04"))
                .andExpect(jsonPath("$.monthlyOrderValue[9].orderValue").value(1200000.00))
                .andExpect(jsonPath("$.monthlyOrderValue[10].orderValue").value(2400000.00))
                .andExpect(jsonPath("$.monthlyOrderValue[11].orderValue").value(48000000.00))
                .andExpect(jsonPath("$.monthlyOrderValue[11].requests").value(4))
                .andExpect(jsonPath("$.monthlyOrderValue[0].orderValue").value(0))
                .andExpect(jsonPath("$.pipeline", hasSize(14)))
                .andExpect(jsonPath("$.pipeline[0].status").value("PENDING_MARKETING_REVIEW"))
                .andExpect(jsonPath("$.pipeline[0].count").value(1))
                .andExpect(jsonPath("$.pipeline[5].status").value("PENDING_FINANCE_CLEARANCE"))
                .andExpect(jsonPath("$.pipeline[5].count").value(1))
                .andExpect(jsonPath("$.pipeline[12].count").value(1))
                .andExpect(jsonPath("$.pipeline[13].count").value(1))
                .andExpect(jsonPath("$.pipeline[1].count").value(0))
                .andExpect(jsonPath("$.stageDurations[*].status").value(contains("PENDING_MARKETING_REVIEW",
                        "MARKETING_QUALIFIED", "PENDING_EXECUTIVE_APPROVAL", "EXECUTIVE_APPROVED",
                        "PENDING_FINANCE_CLEARANCE", "FINANCIALLY_CLEARED")))
                .andExpect(jsonPath("$.stageDurations[0].avgHours").value(24.0))
                .andExpect(jsonPath("$.stageDurations[0].samples").value(3))
                .andExpect(jsonPath("$.stageDurations[1].avgHours").value(48.0))
                .andExpect(jsonPath("$.stageDurations[3].avgHours").value(2.0))
                .andExpect(jsonPath("$.stageDurations[4].avgHours").value(22.0))
                .andExpect(jsonPath("$.stageDurations[5].avgHours").value(50.0))
                .andExpect(jsonPath("$.stageDurations[?(@.slowest == true)].status").value(contains("FINANCIALLY_CLEARED")))
                .andExpect(jsonPath("$.topDealers[*].dealerName").value(contains(b.dealer().getName(), a.dealer().getName())))
                .andExpect(jsonPath("$.topDealers[0].orderValue").value(30000000.00))
                .andExpect(jsonPath("$.topDealers[1].orderValue").value(18000000.00))
                .andExpect(jsonPath("$.topDealers[1].requests").value(2))
                .andExpect(jsonPath("$.topDealers[1].regionName").value(a.region().getName()))
                .andExpect(jsonPath("$.demandByRegion[*].name").value(contains(b.region().getName(), a.region().getName())))
                .andExpect(jsonPath("$.demandByRegion[0].units").value(30))
                .andExpect(jsonPath("$.demandByRegion[1].units").value(15))
                .andExpect(jsonPath("$.demandByModel[*].name").value(contains(b.model().getName(), a.model().getName())))
                .andExpect(jsonPath("$.stockVsProduction.fromStock").value(34))
                .andExpect(jsonPath("$.stockVsProduction.toProduce").value(11))
                .andExpect(jsonPath("$.stockVsProduction.byModel", hasSize(2)));
    }

    @Test
    void regionFilterNarrowsEveryFigure() throws Exception {
        call(get("/api/analytics/executive").param("from", "2025-06-01").param("to", "2025-06-30")
                .param("plant", plant.getId().toString()).param("region", a.region().getId().toString()), RoleCode.SUPERVISOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.regionId").value(a.region().getId()))
                .andExpect(jsonPath("$.kpis.totalOrderValue.value").value(18000000.00))
                .andExpect(jsonPath("$.kpis.requestCount.value").value(2))
                .andExpect(jsonPath("$.topDealers", hasSize(1)))
                .andExpect(jsonPath("$.stockVsProduction.fromStock").value(4));
    }

    @Test
    void emptyPeriodsReturnZerosAndNulls() throws Exception {
        call(get("/api/analytics/executive").param("from", "2020-01-01").param("to", "2020-01-31")
                .param("plant", plant.getId().toString()), RoleCode.MARKETING_HEAD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalOrderValue.value").value(0))
                .andExpect(jsonPath("$.kpis.requestCount.value").value(0))
                .andExpect(jsonPath("$.kpis.slaCompliancePct.value").value(nullValue()))
                .andExpect(jsonPath("$.topDealers", hasSize(0)))
                .andExpect(jsonPath("$.stageDurations", hasSize(0)))
                .andExpect(jsonPath("$.monthlyOrderValue", hasSize(12)));
    }

    @Test
    void defaultsToTheLastThirtyDaysAndValidatesTheRange() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        call(get("/api/analytics/executive"), RoleCode.MARKETING_HEAD).andExpect(status().isOk())
                .andExpect(jsonPath("$.period.to").value(today.toString()))
                .andExpect(jsonPath("$.period.from").value(today.minusDays(29).toString()));
        call(get("/api/analytics/executive").param("from", "2025-07-01").param("to", "2025-06-01"), RoleCode.SUPERVISOR)
                .andExpect(status().isBadRequest());
        call(get("/api/analytics/executive").param("from", "2020-01-01").param("to", "2025-06-01"), RoleCode.SUPERVISOR)
                .andExpect(status().isBadRequest());
        call(get("/api/analytics/executive").param("from", "not-a-date"), RoleCode.SUPERVISOR)
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyMarketingHeadAndSupervisorMayRead() throws Exception {
        for (RoleCode role : new RoleCode[] {RoleCode.ADMIN, RoleCode.MARKETING_MANAGER, RoleCode.FINANCE_TEAM}) {
            call(get("/api/analytics/executive"), role).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/analytics/executive")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private ResultActions june() throws Exception {
        return call(get("/api/analytics/executive").param("from", "2025-06-01").param("to", "2025-06-30")
                .param("plant", plant.getId().toString()), RoleCode.MARKETING_HEAD);
    }

    private long request(World world, int qty, FleetRequestStatus status, String createdIst, int allocated) {
        FleetRequest r = data.request(world, qty, status, creator, false);
        jdbc.update("update fleet_requests set created_at = ?, plant_id = ?, allocated_qty = ?, production_required = ? "
                + "where id = ?", ist(createdIst), plant.getId(), allocated, qty - allocated, r.getId());
        return r.getId();
    }

    private void history(long requestId, String from, String to, String action, String atIst) {
        jdbc.update("insert into workflow_history (fleet_request_id, from_status, to_status, action, created_at) "
                + "values (?, ?, ?, ?, ?)", requestId, from, to, action, ist(atIst));
    }

    private void issuedQuotation(long requestId, World world, String base) {
        Long taxRateId = jdbc.queryForObject("select id from tax_rates where vehicle_category = ?", Long.class,
                world.model().getVehicleCategory());
        Long userId = creator.getId();
        jdbc.update("""
                insert into quotations (quotation_number, fleet_request_id, status, tax_rate_id, gst_rate, base_amount,
                    gst_amount, total_amount, issue_date, valid_until, issued_at, issued_by_id, created_by_id)
                values (?, ?, 'ISSUED', ?, 0.1800, ?, 0, ?, date '2025-06-27', date '2025-07-27', ?, ?, ?)
                """, "QT-T-" + requestId, requestId, taxRateId, new java.math.BigDecimal(base),
                new java.math.BigDecimal(base), ist("2025-06-27T10:00"), userId, userId);
    }

    private static OffsetDateTime ist(String localDateTime) {
        return OffsetDateTime.parse(localDateTime + ":00+05:30");
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            RoleCode role) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, sessions.get(role).bearer()));
    }
}
