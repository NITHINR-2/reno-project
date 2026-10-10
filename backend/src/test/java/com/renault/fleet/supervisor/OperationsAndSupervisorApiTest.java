package com.renault.fleet.supervisor;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.entity.AuditLog;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.dispatch.entity.DispatchStatus;
import com.renault.fleet.dispatch.repository.DispatchRepository;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.inventory.dto.MapStockRequest;
import com.renault.fleet.inventory.entity.AllocationStatus;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import com.renault.fleet.inventory.repository.StockAllocationRepository;
import com.renault.fleet.inventory.service.StockAllocationService;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.entity.ProductionStatus;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.production.repository.ProductionOrderRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.entity.WorkflowHistory;
import com.renault.fleet.workflow.repository.WorkflowHistoryRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Production, dispatch, completion and the Supervisor powers (override, reassign, cancel) over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsAndSupervisorApiTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private FleetRequestRepository requests;
    @Autowired private PlantRepository plants;
    @Autowired private InventoryStockRepository stockRepository;
    @Autowired private StockAllocationRepository allocations;
    @Autowired private StockAllocationService stockAllocations;
    @Autowired private ProductionOrderRepository productionOrders;
    @Autowired private DispatchRepository dispatches;
    @Autowired private WorkflowHistoryRepository historyRepository;
    @Autowired private AuditLogRepository auditLogs;

    private final Map<RoleCode, Session> sessions = new EnumMap<>(RoleCode.class);
    private final Map<RoleCode, User> users = new EnumMap<>(RoleCode.class);

    @BeforeAll
    void setUp() throws Exception {
        TestUsers testUsers = new TestUsers(userRepository, roleRepository, passwordEncoder);
        for (RoleCode role : RoleCode.values()) {
            User user = testUsers.create(role);
            users.put(role, user);
            sessions.put(role, loginOk(mockMvc, user.getUsername()));
        }
    }

    @Test
    void fullHappyPathFromCreationToCompleted() throws Exception {
        World world = data.worldWithGst("0.18");
        InventoryStock yard = data.stock(world.variant(), 30);
        Plant plant = plants.save(new Plant("P-" + suffix(), "Plant " + suffix(), "Pune", "Maharashtra"));

        String created = call(post("/api/fleet-requests"), RoleCode.MARKETING_MANAGER, """
                {"dealerId":%d,"variantId":%d,"plantId":%d,"quantity":50,"requiredDeposit":5000000.00,"productionEstimateWeeks":6}
                """.formatted(world.dealer().getId(), world.variant().getId(), plant.getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Integer) JsonPath.read(created, "$.id")).longValue();
        String r = "/api/fleet-requests/" + id;

        // Marketing
        call(put(r + "/stock-allocations"), RoleCode.MARKETING_MANAGER,
                "{\"lines\":[{\"stockId\":" + yard.getId() + ",\"quantity\":30}]}")
                .andExpect(jsonPath("$.productionRequired").value(20));
        ok(post(r + "/submit-for-qualification"), RoleCode.MARKETING_MANAGER, "MARKETING_QUALIFIED");
        ok(post(r + "/forward-to-marketing-head"), RoleCode.MARKETING_MANAGER, "PENDING_EXECUTIVE_APPROVAL");
        // Executive
        ok(post(r + "/approve"), RoleCode.MARKETING_HEAD, "EXECUTIVE_APPROVED");
        ok(post(r + "/forward-to-finance"), RoleCode.MARKETING_HEAD, "PENDING_FINANCE_CLEARANCE");
        // Finance
        String draft = call(post(r + "/quotations/calculate"), RoleCode.FINANCE_TEAM, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        call(post("/api/quotations/" + JsonPath.read(draft, "$.id") + "/issue"), RoleCode.FINANCE_TEAM, null)
                .andExpect(jsonPath("$.requestStatus").value("PAYMENT_PENDING"));
        String receipt = call(post(r + "/payment-receipts"), RoleCode.FINANCE_TEAM, """
                {"receiptType":"DEPOSIT","paymentMethod":"RTGS","transactionReference":"UTR-%s","amount":5000000.00,"receivedDate":"%s"}
                """.formatted(suffix(), TODAY.minusDays(1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        call(post("/api/payment-receipts/" + JsonPath.read(receipt, "$.id") + "/approve"), RoleCode.FINANCE_TEAM, null)
                .andExpect(jsonPath("$.requestStatus").value("FUNDS_VERIFIED"));
        ok(post(r + "/grant-clearance"), RoleCode.FINANCE_TEAM, "FINANCIALLY_CLEARED");
        // Supervisor: production, dispatch, completion
        call(post(r + "/schedule-dispatch"), RoleCode.SUPERVISOR, "{\"scheduledDate\":\"" + TODAY.plusDays(50) + "\"}")
                .andExpect(status().isConflict()); // 20 units must be produced first
        call(post(r + "/schedule-production"), RoleCode.SUPERVISOR, """
                {"scheduledStart":"%s","estimatedCompletion":"%s"}""".formatted(TODAY.plusDays(1), TODAY.plusDays(42)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRODUCTION_SCHEDULED"));
        call(post(r + "/schedule-dispatch"), RoleCode.SUPERVISOR, """
                {"scheduledDate":"%s","carrier":"Test Logistics"}""".formatted(TODAY.plusDays(45)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPATCH_SCHEDULED"));
        ok(post(r + "/complete"), RoleCode.SUPERVISOR, "COMPLETED");

        var order = productionOrders.findFirstByFleetRequestIdOrderByIdDesc(id).orElseThrow();
        assertThat(order.getQuantity()).isEqualTo(20);
        assertThat(order.getPlant().getId()).isEqualTo(plant.getId());
        assertThat(order.getStatus()).isEqualTo(ProductionStatus.COMPLETED);
        var dispatch = dispatches.findFirstByFleetRequestIdOrderByIdDesc(id).orElseThrow();
        assertThat(dispatch.getQuantity()).isEqualTo(50);
        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.DELIVERED);
        InventoryStock after = stockRepository.findById(yard.getId()).orElseThrow();
        assertThat(after.getQuantityAvailable()).isZero();
        assertThat(after.getQuantityAllocated()).isZero(); // the 30 yard units left with the delivery
        assertThat(allocations.countByFleetRequestIdAndStatus(id, AllocationStatus.CONSUMED)).isEqualTo(1);
        assertThat(historyRepository.findByFleetRequestId(id, PageRequest.of(0, 100, Sort.by("createdAt", "id"))).map(WorkflowHistory::getAction).toList())
                .containsSubsequence("CREATED", "STOCK_MAPPED", "QUALIFY", "FORWARD_TO_EXECUTIVE", "APPROVE",
                        "FORWARD_TO_FINANCE", "ISSUE_QUOTATION", "AWAIT_PAYMENT", "VERIFY_FUNDS", "GRANT_CLEARANCE",
                        "SCHEDULE_PRODUCTION", "SCHEDULE_DISPATCH", "COMPLETE");
        call(post(r + "/cancel"), RoleCode.SUPERVISOR, "{\"reason\":\"too late\"}").andExpect(status().isConflict());
    }

    @Test
    void productionIsSkippedWhenEverythingComesFromStock() throws Exception {
        FleetRequest request = clearedFromStock();
        String r = "/api/fleet-requests/" + request.getId();
        call(post(r + "/schedule-production"), RoleCode.SUPERVISOR, """
                {"scheduledStart":"%s","estimatedCompletion":"%s"}""".formatted(TODAY, TODAY.plusDays(5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("No production is required")));
        assertThat(productionOrders.findFirstByFleetRequestIdOrderByIdDesc(request.getId())).isEmpty();
        call(post(r + "/schedule-dispatch"), RoleCode.SUPERVISOR, "{\"scheduledDate\":\"" + TODAY.plusDays(3) + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPATCH_SCHEDULED"));
        ok(post(r + "/complete"), RoleCode.SUPERVISOR, "COMPLETED");
    }

    @Test
    void schedulingIsSupervisorOnlyAndValidatesDates() throws Exception {
        FleetRequest request = clearedFromStock();
        String r = "/api/fleet-requests/" + request.getId();
        String body = "{\"scheduledDate\":\"" + TODAY.plusDays(3) + "\"}";
        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.MARKETING_MANAGER, RoleCode.MARKETING_HEAD, RoleCode.FINANCE_TEAM)) {
            call(post(r + "/schedule-dispatch"), role, body).andExpect(status().isForbidden());
            call(post(r + "/schedule-production"), role, """
                    {"scheduledStart":"%s","estimatedCompletion":"%s"}""".formatted(TODAY, TODAY.plusDays(5)))
                    .andExpect(status().isForbidden());
            call(post(r + "/complete"), role, null).andExpect(status().isForbidden());
        }
        call(post(r + "/schedule-dispatch"), RoleCode.SUPERVISOR, "{\"scheduledDate\":\"" + TODAY.minusDays(1) + "\"}")
                .andExpect(status().isBadRequest());
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(FleetRequestStatus.FINANCIALLY_CLEARED);
    }

    @Test
    void overrideWithoutAReasonIs400AndUnlistedFieldsAre400() throws Exception {
        FleetRequest request = data.request(data.world(), 10, users.get(RoleCode.MARKETING_MANAGER));
        String r = "/api/fleet-requests/" + request.getId() + "/override";
        call(post(r), RoleCode.SUPERVISOR, "{\"changes\":{\"notes\":\"x\"}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
        call(post(r), RoleCode.SUPERVISOR, "{\"reason\":\"  \",\"changes\":{\"notes\":\"x\"}}")
                .andExpect(status().isBadRequest());
        for (String field : List.of("status", "quantity", "allocatedQty", "productionRequired", "variantId")) {
            call(post(r), RoleCode.SUPERVISOR, "{\"reason\":\"why\",\"changes\":{\"" + field + "\":\"1\"}}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("cannot be overridden")));
        }
        call(post(r), RoleCode.SUPERVISOR, "{\"reason\":\"why\",\"changes\":{}}").andExpect(status().isBadRequest());
        call(post(r), RoleCode.SUPERVISOR, "{\"reason\":\"why\",\"changes\":{\"requiredDeposit\":\"-5\"}}")
                .andExpect(status().isBadRequest());
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("FLEET_REQUEST", request.getId().toString())).isEmpty();
    }

    @Test
    void overrideIsAuditedWithBeforeAfterAndReason() throws Exception {
        FleetRequest request = data.request(data.world(), 10, FleetRequestStatus.EXECUTIVE_APPROVED,
                users.get(RoleCode.MARKETING_MANAGER), true);
        call(post("/api/fleet-requests/" + request.getId() + "/override"), RoleCode.SUPERVISOR, """
                {"reason":"Dealer is a strategic account; deposit waived to 1L","version":0,
                 "changes":{"requiredDeposit":100000.00,"productionEstimateWeeks":8,"notes":null}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredDeposit").value(100000.00))
                .andExpect(jsonPath("$.productionEstimateWeeks").value(8))
                .andExpect(jsonPath("$.status").value("EXECUTIVE_APPROVED"));

        List<AuditLog> audit = auditLogs.findByEntityTypeAndEntityIdOrderById("FLEET_REQUEST", request.getId().toString());
        assertThat(audit).singleElement().satisfies(a -> {
            assertThat(a.getAction()).isEqualTo("OVERRIDE");
            assertThat(a.getActorRole()).isEqualTo("SUPERVISOR");
            assertThat(a.getReason()).isEqualTo("Dealer is a strategic account; deposit waived to 1L");
            assertThat(JsonPath.<Double>read(a.getBeforeJson(), "$.requiredDeposit")).isEqualTo(500000.00);
            assertThat(JsonPath.<Double>read(a.getAfterJson(), "$.requiredDeposit")).isEqualTo(100000.00);
            assertThat(JsonPath.<Integer>read(a.getAfterJson(), "$.productionEstimateWeeks")).isEqualTo(8);
        });
        WorkflowHistory row = historyRepository.findByFleetRequestId(request.getId(), Pageable.unpaged()).getContent().getFirst();
        assertThat(row.getAction()).isEqualTo("OVERRIDE");
        assertThat(row.isOverride()).isTrue();
        assertThat(row.getReason()).startsWith("Dealer is a strategic account");
        assertThat(row.getFromStatus()).isEqualTo(row.getToStatus());

        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.MARKETING_MANAGER, RoleCode.MARKETING_HEAD, RoleCode.FINANCE_TEAM)) {
            call(post("/api/fleet-requests/" + request.getId() + "/override"), role,
                    "{\"reason\":\"x\",\"changes\":{\"notes\":\"x\"}}").andExpect(status().isForbidden());
        }
        FleetRequest closed = data.request(data.world(), 10, FleetRequestStatus.COMPLETED, users.get(RoleCode.MARKETING_MANAGER), true);
        call(post("/api/fleet-requests/" + closed.getId() + "/override"), RoleCode.SUPERVISOR,
                "{\"reason\":\"x\",\"changes\":{\"notes\":\"x\"}}").andExpect(status().isConflict());
    }

    @Test
    void cancelRequiresAReasonReleasesStockAndVoidsOpenWork() throws Exception {
        World world = data.world();
        InventoryStock yard = data.stock(world.variant(), 30);
        User mm = users.get(RoleCode.MARKETING_MANAGER);
        FleetRequest request = data.request(world, 50, mm);
        stockAllocations.mapStock(request.getId(), new MapStockRequest(null,
                List.of(new MapStockRequest.Line(yard.getId(), 30))), actor(mm, RoleCode.MARKETING_MANAGER));
        assertThat(stockRepository.findById(yard.getId()).orElseThrow().getQuantityAvailable()).isZero();
        String r = "/api/fleet-requests/" + request.getId() + "/cancel";

        call(post(r), RoleCode.SUPERVISOR, "{}").andExpect(status().isBadRequest());
        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.MARKETING_MANAGER, RoleCode.MARKETING_HEAD, RoleCode.FINANCE_TEAM)) {
            call(post(r), role, "{\"reason\":\"x\"}").andExpect(status().isForbidden());
        }
        call(post(r), RoleCode.SUPERVISOR, "{\"reason\":\"Dealer withdrew the order\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Dealer withdrew the order"))
                .andExpect(jsonPath("$.allocatedQty").value(0));

        InventoryStock after = stockRepository.findById(yard.getId()).orElseThrow();
        assertThat(after.getQuantityAvailable()).isEqualTo(30);
        assertThat(after.getQuantityAllocated()).isZero();
        assertThat(allocations.countByFleetRequestIdAndStatus(request.getId(), AllocationStatus.RELEASED)).isEqualTo(1);
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("FLEET_REQUEST", request.getId().toString()))
                .extracting(AuditLog::getAction).contains("WORKFLOW_CANCEL");
        call(post(r), RoleCode.SUPERVISOR, "{\"reason\":\"again\"}").andExpect(status().isConflict());
    }

    @Test
    void reassignNeedsAReasonAndAFleetUser() throws Exception {
        FleetRequest request = data.request(data.world(), 10, users.get(RoleCode.MARKETING_MANAGER));
        String r = "/api/fleet-requests/" + request.getId() + "/reassign";
        Long mh = users.get(RoleCode.MARKETING_HEAD).getId();

        call(post(r), RoleCode.SUPERVISOR, "{\"assigneeId\":" + mh + "}").andExpect(status().isBadRequest());
        call(post(r), RoleCode.SUPERVISOR, "{\"assigneeId\":" + users.get(RoleCode.ADMIN).getId() + ",\"reason\":\"x\"}")
                .andExpect(status().isConflict());
        call(post(r), RoleCode.MARKETING_HEAD, "{\"assigneeId\":" + mh + ",\"reason\":\"x\"}").andExpect(status().isForbidden());
        call(post(r), RoleCode.SUPERVISOR, "{\"assigneeId\":" + mh + ",\"reason\":\"Workload balancing\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.id").value(mh));
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("FLEET_REQUEST", request.getId().toString()))
                .singleElement().satisfies(a -> {
                    assertThat(a.getAction()).isEqualTo("REASSIGN");
                    assertThat(a.getReason()).isEqualTo("Workload balancing");
                });
    }

    /** A FINANCIALLY_CLEARED request whose 10 units all come from stock (productionRequired = 0). */
    private FleetRequest clearedFromStock() {
        World world = data.world();
        InventoryStock yard = data.stock(world.variant(), 10);
        User mm = users.get(RoleCode.MARKETING_MANAGER);
        FleetRequest request = data.request(world, 10, mm);
        stockAllocations.mapStock(request.getId(), new MapStockRequest(null,
                List.of(new MapStockRequest.Line(yard.getId(), 10))), actor(mm, RoleCode.MARKETING_MANAGER));
        FleetRequest reloaded = requests.findById(request.getId()).orElseThrow();
        reloaded.setStatus(FleetRequestStatus.FINANCIALLY_CLEARED); // fixture shortcut past marketing and finance
        return requests.save(reloaded);
    }

    private static AuthenticatedUser actor(User user, RoleCode role) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), role, Set.of());
    }

    private void ok(MockHttpServletRequestBuilder builder, RoleCode role, String expectedStatus) throws Exception {
        call(builder, role, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expectedStatus));
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, RoleCode role, String body) throws Exception {
        builder.header(HttpHeaders.AUTHORIZATION, sessions.get(role).bearer());
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
