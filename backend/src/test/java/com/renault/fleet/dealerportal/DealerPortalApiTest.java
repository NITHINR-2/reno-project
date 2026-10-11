package com.renault.fleet.dealerportal;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.catalog.entity.FuelType;
import com.renault.fleet.catalog.entity.VehicleModel;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.catalog.repository.VehicleModelRepository;
import com.renault.fleet.catalog.repository.VehicleVariantRepository;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.repository.WorkflowHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Dealer Portal: catalogue, orders (one request per line), dealer scoping (404), HQ isolation (403), cancel. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DealerPortalApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private VehicleModelRepository models;
    @Autowired private VehicleVariantRepository variants;
    @Autowired private FleetRequestRepository requests;
    @Autowired private WorkflowHistoryRepository history;
    @Autowired private AuditLogRepository auditLogs;

    private final Map<RoleCode, Session> hq = new EnumMap<>(RoleCode.class);
    private World a;
    private World b;
    private Session dealerA;
    private Session dealerB;
    private User dealerUserA;
    private User creator;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        for (RoleCode role : RoleCode.values()) {
            if (role != RoleCode.DEALER) {
                User user = users.create(role);
                hq.put(role, loginOk(mockMvc, user.getUsername()));
                if (role == RoleCode.MARKETING_MANAGER) creator = user;
            }
        }
        a = data.world();
        b = data.world();
        dealerUserA = dealerUser(users, a);
        dealerA = loginOk(mockMvc, dealerUserA.getUsername());
        dealerB = loginOk(mockMvc, dealerUser(users, b).getUsername());
    }

    @Test
    void meReportsTheDealershipAndOnlyThePortalPermission() throws Exception {
        as(get("/api/auth/me"), dealerA).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEALER"))
                .andExpect(jsonPath("$.dealerId").value(a.dealer().getId()))
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andExpect(jsonPath("$.permissions[0]").value("DEALER_PORTAL"));
    }

    @Test
    void catalogueListsOnlyActiveModelsWithTheirActiveVariants() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        VehicleModel shown = models.save(new VehicleModel("CAT-" + s, "Catalogue " + s, "SUV"));
        VehicleVariant onSale = variants.save(new VehicleVariant(shown, "CAT-" + s + "-A", "On sale", FuelType.PETROL, new BigDecimal("900000.00")));
        VehicleVariant retired = variants.save(new VehicleVariant(shown, "CAT-" + s + "-B", "Retired", FuelType.DIESEL, new BigDecimal("950000.00")));
        retired.setActive(false);
        variants.save(retired);
        VehicleModel hidden = new VehicleModel("CAT-" + s + "X", "Catalogue " + s + " hidden", "SUV");
        hidden.setActive(false);
        variants.save(new VehicleVariant(models.save(hidden), "CAT-" + s + "-X", "Hidden", FuelType.PETROL, new BigDecimal("1.00")));
        VehicleModel noVariants = models.save(new VehicleModel("CAT-" + s + "N", "Catalogue " + s + " empty", "SUV"));

        as(get("/api/dealer/catalogue").param("q", "CAT-" + s), dealerA).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(shown.getId()))
                .andExpect(jsonPath("$.content[0].variants.length()").value(1))
                .andExpect(jsonPath("$.content[0].variants[0].id").value(onSale.getId()))
                .andExpect(jsonPath("$.content[0].variants[0].unitPrice").value(900000.00))
                .andExpect(jsonPath("$.content[0].active").doesNotExist());
        assertThat(noVariants.getId()).isNotNull();
    }

    @Test
    void catalogueCrudIsForAdminAndSupervisorOnly() throws Exception {
        String code = "CRUD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String created = as(post("/api/admin/catalogue/models").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"%s","name":"Crud Model","vehicleCategory":"SUV","bodyType":"SUV","seatingCapacity":5,
                 "imageUrl":"https://img.example/crud.png","description":"Test model"}""".formatted(code)),
                hq.get(RoleCode.ADMIN)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.bodyType").value("SUV")).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");
        as(post("/api/admin/catalogue/models/" + id + "/variants").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"%s-V","name":"Crud V","fuelType":"PETROL","unitPrice":700000.00}""".formatted(code)),
                hq.get(RoleCode.SUPERVISOR)).andExpect(status().isCreated());
        as(patch("/api/admin/catalogue/models/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"active\":false}"), hq.get(RoleCode.ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        as(get("/api/dealer/catalogue").param("q", code), dealerA).andExpect(jsonPath("$.totalElements").value(0));
        as(post("/api/admin/catalogue/models").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"BAD\",\"name\":\"x\",\"vehicleCategory\":\"SUV\",\"imageUrl\":\"http://plain\"}"),
                hq.get(RoleCode.ADMIN)).andExpect(status().isBadRequest());
        as(patch("/api/admin/catalogue/models/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"imageUrl\":\"/cars/crud.jpg\"}"), hq.get(RoleCode.ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").value("/cars/crud.jpg"));
        as(patch("/api/admin/catalogue/models/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"imageUrl\":\"/cars/../../x y.jpg\"}"), hq.get(RoleCode.ADMIN))
                .andExpect(status().isBadRequest());
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("VEHICLE_MODEL", String.valueOf(id))).hasSize(3);
        for (RoleCode role : List.of(RoleCode.MARKETING_MANAGER, RoleCode.MARKETING_HEAD, RoleCode.FINANCE_TEAM)) {
            as(get("/api/admin/catalogue/models"), hq.get(role)).andExpect(status().isForbidden());
        }
        as(get("/api/admin/catalogue/models"), dealerA).andExpect(status().isForbidden());
    }

    @Test
    void anOrderCreatesOneFleetRequestPerLineInMarketingReview() throws Exception {
        VehicleVariant second = variants.save(new VehicleVariant(a.model(), "V2-" + UUID.randomUUID().toString().substring(0, 8),
                "Second", FuelType.DIESEL, new BigDecimal("1000000.00")));
        LocalDate delivery = LocalDate.now().plusMonths(2);
        String json = order(dealerA, """
                {"lines":[{"variantId":%d,"quantity":12},{"variantId":%d,"quantity":3}],
                 "requestedDeliveryDate":"%s","notes":"Two lines"}""".formatted(a.variant().getId(), second.getId(), delivery))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.matchesPattern("DO-\\d{4}-\\d{4,}")))
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(12))
                .andExpect(jsonPath("$.lines[1].variantCode").value(second.getCode()))
                .andExpect(jsonPath("$.lines[0].canCancel").value(true))
                .andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(json, "$.id");
        List<Integer> ids = JsonPath.read(json, "$.lines[*].id");

        for (int i = 0; i < ids.size(); i++) {
            FleetRequest r = requests.findDetailedById(ids.get(i).longValue()).orElseThrow();
            assertThat(r.getStatus()).isEqualTo(FleetRequestStatus.PENDING_MARKETING_REVIEW);
            assertThat(r.getDealer().getId()).isEqualTo(a.dealer().getId());
            assertThat(r.getDealerOrder().getId()).isEqualTo(orderId);
            assertThat(r.getDealerOrderLineNo()).isEqualTo(i + 1);
            assertThat(r.getCreatedBy().getId()).isEqualTo(dealerUserA.getId());
            assertThat(r.getRequestedDeliveryDate()).isEqualTo(delivery);
            assertThat(r.getSlaDueAt()).isNotNull();
            assertThat(history.countByFleetRequestId(r.getId())).isEqualTo(1);
        }
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("DEALER_ORDER", String.valueOf(orderId))).hasSize(1);

        // HQ sees them as ordinary requests; the dealer sees them in its own lists.
        as(get("/api/fleet-requests/" + ids.get(0)), hq.get(RoleCode.MARKETING_MANAGER)).andExpect(status().isOk());
        as(get("/api/dealer/orders/" + orderId), dealerA).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2));
        as(get("/api/dealer/orders").param("q", (String) JsonPath.read(json, "$.orderNumber")), dealerA)
                .andExpect(jsonPath("$.content[0].lineCount").value(2))
                .andExpect(jsonPath("$.content[0].totalQuantity").value(15));
    }

    @Test
    void invalidOrdersAreRejectedWithoutCreatingRequests() throws Exception {
        long before = requests.count();
        order(dealerA, "{\"lines\":[]}").andExpect(status().isBadRequest());
        order(dealerA, "{\"lines\":[{\"variantId\":%d,\"quantity\":0}]}".formatted(a.variant().getId()))
                .andExpect(status().isBadRequest());
        order(dealerA, "{\"lines\":[{\"variantId\":%d,\"quantity\":1},{\"variantId\":%1$d,\"quantity\":2}]}"
                .formatted(a.variant().getId())).andExpect(status().isBadRequest());
        VehicleVariant retired = variants.save(new VehicleVariant(a.model(), "R-" + UUID.randomUUID().toString().substring(0, 8),
                "Retired", FuelType.PETROL, new BigDecimal("1.00")));
        retired.setActive(false);
        variants.save(retired);
        order(dealerA, "{\"lines\":[{\"variantId\":%d,\"quantity\":1},{\"variantId\":%d,\"quantity\":1}]}"
                .formatted(a.variant().getId(), retired.getId())).andExpect(status().isConflict());
        assertThat(requests.count()).isEqualTo(before); // the valid first line was rolled back too
    }

    @Test
    void anotherDealersOrdersAndRequestsAreNotFound() throws Exception {
        String json = order(dealerA, "{\"lines\":[{\"variantId\":%d,\"quantity\":2}]}".formatted(a.variant().getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(json, "$.id");
        int requestId = JsonPath.read(json, "$.lines[0].id");
        FleetRequest hqRaised = data.request(a, 5, creator);

        as(get("/api/dealer/orders/" + orderId), dealerB).andExpect(status().isNotFound());
        as(get("/api/dealer/requests/" + requestId), dealerB).andExpect(status().isNotFound());
        as(get("/api/dealer/requests/" + hqRaised.getId()), dealerB).andExpect(status().isNotFound());
        as(cancel(requestId, "not mine"), dealerB).andExpect(status().isNotFound());
        as(get("/api/dealer/requests/" + requestId + "/quotation/pdf"), dealerB).andExpect(status().isNotFound());
        as(get("/api/dealer/orders/999999999"), dealerB).andExpect(status().isNotFound());
        as(get("/api/dealer/orders"), dealerB).andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(orderId)).isEmpty());
        as(get("/api/dealer/requests"), dealerB)
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(requestId)).isEmpty());
        assertThat(requests.findById((long) requestId).orElseThrow().getStatus())
                .isEqualTo(FleetRequestStatus.PENDING_MARKETING_REVIEW);

        // Requests HQ raised for the dealership are visible to it, without internal fields.
        as(get("/api/dealer/requests/" + hqRaised.getId()), dealerA).andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.request.orderId").doesNotExist())
                .andExpect(jsonPath("$.request.notes").doesNotExist())
                .andExpect(jsonPath("$.request.slaStatus").doesNotExist());
    }

    @Test
    void dealersGetForbiddenOnHqApisAndHqGetsForbiddenOnThePortal() throws Exception {
        FleetRequest request = data.request(a, 3, creator);
        List<MockHttpServletRequestBuilder> hqCalls = List.of(
                get("/api/fleet-requests"), get("/api/fleet-requests/" + request.getId()),
                get("/api/fleet-requests/summary"), get("/api/vehicle-models"), get("/api/dealers"),
                get("/api/inventory/stock"), get("/api/admin/users"), get("/api/admin/audit-logs"),
                get("/api/analytics/executive"), get("/api/fleet-requests/" + request.getId() + "/quotations"),
                get("/api/fleet-requests/" + request.getId() + "/payment-receipts"),
                post("/api/fleet-requests/" + request.getId() + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"),
                post("/api/fleet-requests/" + request.getId() + "/submit-for-qualification"));
        for (MockHttpServletRequestBuilder call : hqCalls) {
            as(call, dealerA).andExpect(status().isForbidden());
        }
        for (Session session : hq.values()) {
            as(get("/api/dealer/orders"), session).andExpect(status().isForbidden());
            as(get("/api/dealer/catalogue"), session).andExpect(status().isForbidden());
        }
    }

    @Test
    void aDealerCanCancelOnlyWhileTheRequestIsInReview() throws Exception {
        String json = order(dealerA, "{\"lines\":[{\"variantId\":%d,\"quantity\":4}]}".formatted(a.variant().getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.lines[0].id");
        as(post("/api/dealer/requests/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"), dealerA)
                .andExpect(status().isBadRequest());
        as(cancel(id, "Budget withdrawn"), dealerA).andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("CANCELLED"))
                .andExpect(jsonPath("$.request.canCancel").value(false));
        FleetRequest cancelled = requests.findById((long) id).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(FleetRequestStatus.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo("Budget withdrawn");
        assertThat(history.countByFleetRequestId((long) id)).isEqualTo(2);
        as(cancel(id, "again"), dealerA).andExpect(status().isConflict());

        for (FleetRequestStatus after : List.of(FleetRequestStatus.MARKETING_QUALIFIED,
                FleetRequestStatus.RETURNED_TO_MARKETING, FleetRequestStatus.PAYMENT_PENDING)) {
            FleetRequest reviewed = data.request(a, 4, after, creator, true);
            as(cancel(reviewed.getId(), "too late"), dealerA).andExpect(status().isConflict());
            assertThat(requests.findById(reviewed.getId()).orElseThrow().getStatus()).isEqualTo(after);
        }
    }

    // --- helpers ---

    private User dealerUser(TestUsers users, World world) {
        User user = users.create(RoleCode.DEALER);
        user.setDealer(world.dealer());
        return userRepository.save(user);
    }

    private ResultActions order(Session session, String body) throws Exception {
        return as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(body), session);
    }

    private static MockHttpServletRequestBuilder cancel(long id, String reason) {
        return post("/api/dealer/requests/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}");
    }

    private ResultActions as(MockHttpServletRequestBuilder builder, Session session) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }
}
