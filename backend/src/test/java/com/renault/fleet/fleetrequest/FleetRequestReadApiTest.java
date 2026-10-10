package com.renault.fleet.fleetrequest;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Phase 5A read endpoints: dashboard summary, production orders, dispatches, regions, plants, sorting. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FleetRequestReadApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;

    private User manager;
    private Session mm;
    private Session mh;
    private Session admin;
    private World world;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        manager = users.create(RoleCode.MARKETING_MANAGER);
        mm = loginOk(mockMvc, manager.getUsername());
        mh = loginOk(mockMvc, users.create(RoleCode.MARKETING_HEAD).getUsername());
        admin = loginOk(mockMvc, users.create(RoleCode.ADMIN).getUsername());
        world = data.world();
    }

    @Test
    void summaryCountsEachStageBucketAndUnitsToProduce() throws Exception {
        Map<String, Object> before = summary(mh);

        data.request(world, 7, FleetRequestStatus.PENDING_MARKETING_REVIEW, manager, false);
        data.request(world, 3, FleetRequestStatus.RETURNED_TO_MARKETING, manager, false);
        data.request(world, 5, FleetRequestStatus.PENDING_EXECUTIVE_APPROVAL, manager, true);
        data.request(world, 4, FleetRequestStatus.PAYMENT_PENDING, manager, true);
        data.request(world, 2, FleetRequestStatus.DISPATCH_SCHEDULED, manager, true);
        data.request(world, 9, FleetRequestStatus.COMPLETED, manager, true);
        data.request(world, 11, FleetRequestStatus.CANCELLED, manager, false);

        Map<String, Object> after = summary(mh);
        assertThat(delta(before, after, "activeRequests")).isEqualTo(5);
        assertThat(delta(before, after, "inMarketing")).isEqualTo(2);
        assertThat(delta(before, after, "inExecutiveApproval")).isEqualTo(1);
        assertThat(delta(before, after, "inFinance")).isEqualTo(1);
        assertThat(delta(before, after, "inOperations")).isEqualTo(1);
        // Fixtures have nothing allocated, so productionRequired = quantity; terminal requests don't count.
        assertThat(delta(before, after, "unitsToProduce")).isEqualTo(7 + 3 + 5 + 4 + 2);
        assertThat(delta(before, after, "completed")).isEqualTo(1);
        assertThat(delta(before, after, "cancelled")).isEqualTo(1);

        String json = get(mh, "/api/fleet-requests/summary").andReturn().getResponse().getContentAsString();
        Map<String, Integer> byStatus = JsonPath.read(json, "$.byStatus");
        assertThat(byStatus).hasSize(FleetRequestStatus.values().length);
        long buckets = ((Number) after.get("inMarketing")).longValue() + ((Number) after.get("inExecutiveApproval")).longValue()
                + ((Number) after.get("inFinance")).longValue() + ((Number) after.get("inOperations")).longValue();
        assertThat(buckets).isEqualTo(((Number) after.get("activeRequests")).longValue());
    }

    @Test
    void summaryNeedsFleetReadPermission() throws Exception {
        get(admin, "/api/fleet-requests/summary").andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/fleet-requests/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productionOrdersAndDispatchesArePagedPerRequest() throws Exception {
        FleetRequest request = data.request(world, 10, FleetRequestStatus.PENDING_MARKETING_REVIEW, manager, false);
        for (String path : new String[] {"production-orders", "dispatches"}) {
            get(mm, "/api/fleet-requests/" + request.getId() + "/" + path)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
            get(mm, "/api/fleet-requests/999999999/" + path).andExpect(status().isNotFound());
            get(admin, "/api/fleet-requests/" + request.getId() + "/" + path).andExpect(status().isForbidden());
        }
    }

    @Test
    void regionsAndPlantsAreListedForFleetReaders() throws Exception {
        get(mm, "/api/regions").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].name").exists());
        get(admin, "/api/regions").andExpect(status().isOk());
        get(mh, "/api/plants").andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        get(admin, "/api/plants").andExpect(status().isForbidden());
    }

    @Test
    void listSortsByTheTableColumnsAndRejectsUnknownSorts() throws Exception {
        for (String sort : new String[] {"requestNumber,asc", "dealer.name,desc", "quantity,desc", "productionRequired,asc",
                "requestedDeliveryDate,asc", "status,asc", "createdAt,desc", "updatedAt,desc"}) {
            get(mm, "/api/fleet-requests?size=5&sort=" + sort).andExpect(status().isOk());
        }
        get(mm, "/api/fleet-requests?sort=nope,asc").andExpect(status().isBadRequest());
    }

    private Map<String, Object> summary(Session session) throws Exception {
        String json = get(session, "/api/fleet-requests/summary").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$");
    }

    private static long delta(Map<String, Object> before, Map<String, Object> after, String key) {
        return ((Number) after.get(key)).longValue() - ((Number) before.get(key)).longValue();
    }

    private ResultActions get(Session session, String url) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }
}
