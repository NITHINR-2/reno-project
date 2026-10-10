package com.renault.fleet.fleetrequest;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.entity.AuditLog;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.dealer.entity.DealerStatus;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.inventory.dto.MapStockRequest;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.service.StockAllocationService;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.repository.WorkflowHistoryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FleetRequestCrudApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private WorkflowHistoryRepository historyRepository;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private StockAllocationService stockAllocations;

    private User manager;
    private Session mm;
    private Session finance;
    private Session admin;
    private World world;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        manager = users.create(RoleCode.MARKETING_MANAGER);
        mm = loginOk(mockMvc, manager.getUsername());
        finance = loginOk(mockMvc, users.create(RoleCode.FINANCE_TEAM).getUsername());
        admin = loginOk(mockMvc, users.create(RoleCode.ADMIN).getUsername());
        world = data.world();
    }

    @Test
    void marketingManagerCreatesARequestInMarketingReview() throws Exception {
        String body = createBody(world.dealer().getId(), 50);
        String json = send(post("/api/fleet-requests"), body, mm)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/fleet-requests/\\d+")))
                .andExpect(jsonPath("$.requestNumber").value(matchesPattern("FLT-\\d{4}-\\d{4,}")))
                .andExpect(jsonPath("$.status").value("PENDING_MARKETING_REVIEW"))
                .andExpect(jsonPath("$.quantity").value(50))
                .andExpect(jsonPath("$.allocatedQty").value(0))
                .andExpect(jsonPath("$.productionRequired").value(50))
                .andExpect(jsonPath("$.requiredDeposit").value(5000000.00))
                .andExpect(jsonPath("$.depositReceived").value(0))
                .andExpect(jsonPath("$.dealer.name").value(world.dealer().getName()))
                .andExpect(jsonPath("$.createdBy.id").value(manager.getId()))
                .andExpect(jsonPath("$.allowedActions").isEmpty())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(json, "$.id");

        assertThat(historyRepository.countByFleetRequestId(id.longValue())).isEqualTo(1);
        List<AuditLog> audit = auditLogs.findByEntityTypeAndEntityIdOrderById("FLEET_REQUEST", id.toString());
        assertThat(audit).singleElement().satisfies(a -> {
            assertThat(a.getAction()).isEqualTo("CREATE");
            assertThat(a.getBeforeJson()).isNull();
            assertThat((Integer) JsonPath.read(a.getAfterJson(), "$.quantity")).isEqualTo(50);
        });
    }

    @Test
    void invalidCreateIsA400WithFieldErrors() throws Exception {
        send(post("/api/fleet-requests"), "{\"quantity\":0,\"requiredDeposit\":-1}", mm)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dealerId").exists())
                .andExpect(jsonPath("$.errors.variantId").exists())
                .andExpect(jsonPath("$.errors.quantity").exists())
                .andExpect(jsonPath("$.errors.requiredDeposit").exists());
    }

    @Test
    void createRulesAndPermissions() throws Exception {
        World pendingDealer = data.world(DealerStatus.PENDING_ONBOARDING);
        send(post("/api/fleet-requests"), createBody(pendingDealer.dealer().getId(), pendingDealer.variant().getId(), 5), mm)
                .andExpect(status().isConflict());
        send(post("/api/fleet-requests"), createBody(Long.MAX_VALUE, 5), mm).andExpect(status().isNotFound());
        send(post("/api/fleet-requests"), createBody(world.dealer().getId(), 5), finance).andExpect(status().isForbidden());
        send(post("/api/fleet-requests"), createBody(world.dealer().getId(), 5), admin).andExpect(status().isForbidden());
    }

    @Test
    void patchEditsOnlyInMarketingReviewAndRecomputesProduction() throws Exception {
        FleetRequest request = data.request(world, 50, manager);
        InventoryStock yard = data.stock(world.variant(), 30);
        AuthenticatedUser actor = new AuthenticatedUser(manager.getId(), manager.getUsername(), "MM", null,
                RoleCode.MARKETING_MANAGER, Set.of());
        stockAllocations.mapStock(request.getId(), new MapStockRequest(null,
                List.of(new MapStockRequest.Line(yard.getId(), 30))), actor);
        long version = 1; // fixture insert = 0, stock mapping = 1

        send(patch("/api/fleet-requests/{id}", request.getId()), "{\"version\":" + version + ",\"quantity\":40,\"notes\":\"fewer units\"}", mm)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(40))
                .andExpect(jsonPath("$.productionRequired").value(10))
                .andExpect(jsonPath("$.notes").value("fewer units"))
                .andExpect(jsonPath("$.version").value(2));
        send(patch("/api/fleet-requests/{id}", request.getId()), "{\"version\":" + version + ",\"quantity\":45}", mm)
                .andExpect(status().isConflict());
        send(patch("/api/fleet-requests/{id}", request.getId()), "{\"version\":2,\"quantity\":20}", mm)
                .andExpect(status().isConflict());
        send(patch("/api/fleet-requests/{id}", request.getId()), "{\"quantity\":20}", mm)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());

        FleetRequest qualified = data.request(world, 10, FleetRequestStatus.MARKETING_QUALIFIED, manager, true);
        send(patch("/api/fleet-requests/{id}", qualified.getId()), "{\"version\":0,\"quantity\":20}", mm)
                .andExpect(status().isConflict());
    }

    @Test
    void listIsPaginatedAndFilterable() throws Exception {
        World mine = data.world();
        FleetRequest a = data.request(mine, 10, manager);
        data.request(mine, 20, FleetRequestStatus.PENDING_EXECUTIVE_APPROVAL, manager, true);
        data.request(mine, 30, FleetRequestStatus.EXECUTIVE_APPROVED, manager, true);

        list("dealerId=" + mine.dealer().getId()).andExpect(jsonPath("$.totalElements").value(3));
        list("dealerId=" + mine.dealer().getId() + "&status=PENDING_EXECUTIVE_APPROVAL&status=EXECUTIVE_APPROVED")
                .andExpect(jsonPath("$.totalElements").value(2));
        list("regionId=" + mine.region().getId() + "&modelId=" + mine.model().getId() + "&size=2&sort=quantity,asc")
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].quantity").value(10))
                .andExpect(jsonPath("$.content[0].regionName").value(mine.region().getName()));
        list("q=" + a.getRequestNumber().toLowerCase()).andExpect(jsonPath("$.totalElements").value(1));
        list("dealerId=" + mine.dealer().getId() + "&createdFrom=" + LocalDate.now().minusDays(1))
                .andExpect(jsonPath("$.totalElements").value(3));
        list("dealerId=" + mine.dealer().getId() + "&createdTo=" + LocalDate.now().minusDays(2))
                .andExpect(jsonPath("$.totalElements").value(0));
        list("status=NOT_A_STATUS").andExpect(status().isBadRequest());
    }

    @Test
    void detailAndHistoryEndpoints() throws Exception {
        FleetRequest request = data.request(world, 10, manager);
        mockMvc.perform(get("/api/fleet-requests/{id}", request.getId()).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestNumber").value(request.getRequestNumber()))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
        mockMvc.perform(get("/api/fleet-requests/{id}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/fleet-requests/{id}/history", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, mm.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/fleet-requests/{id}", request.getId()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isForbidden());
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get("/api/fleet-requests?" + query).header(HttpHeaders.AUTHORIZATION, mm.bearer()));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String body, Session session) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createBody(Long dealerId, int quantity) {
        return createBody(dealerId, world.variant().getId(), quantity);
    }

    private static String createBody(Long dealerId, Long variantId, int quantity) {
        return """
                {"dealerId":%d,"variantId":%d,"quantity":%d,"requiredDeposit":5000000.00,
                 "productionEstimateWeeks":6,"requestedDeliveryDate":"%s"}
                """.formatted(dealerId, variantId, quantity, LocalDate.now().plusMonths(3));
    }
}
