package com.renault.fleet.workflow;

import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.*;
import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.entity.AuditLog;
import com.renault.fleet.audit.repository.AuditLogRepository;
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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

/** Drives every Phase 2 workflow endpoint through HTTP with real JWTs, for every role and every source state. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FleetRequestWorkflowApiTest {

    /** Phase 2 endpoints: path, source state, target state, history action, roles allowed. */
    enum Endpoint {
        SUBMIT_FOR_QUALIFICATION("submit-for-qualification", PENDING_MARKETING_REVIEW, MARKETING_QUALIFIED, "QUALIFY",
                RoleCode.MARKETING_MANAGER),
        FORWARD_TO_MARKETING_HEAD("forward-to-marketing-head", MARKETING_QUALIFIED, PENDING_EXECUTIVE_APPROVAL,
                "FORWARD_TO_EXECUTIVE", RoleCode.MARKETING_MANAGER),
        RESUBMIT("resubmit", RETURNED_TO_MARKETING, PENDING_MARKETING_REVIEW, "RESUBMIT", RoleCode.MARKETING_MANAGER),
        APPROVE("approve", PENDING_EXECUTIVE_APPROVAL, EXECUTIVE_APPROVED, "APPROVE", RoleCode.MARKETING_HEAD),
        REJECT("reject", PENDING_EXECUTIVE_APPROVAL, RETURNED_TO_MARKETING, "REJECT", RoleCode.MARKETING_HEAD),
        FORWARD_TO_FINANCE("forward-to-finance", EXECUTIVE_APPROVED, PENDING_FINANCE_CLEARANCE, "FORWARD_TO_FINANCE",
                RoleCode.MARKETING_HEAD);

        final String path;
        final FleetRequestStatus source;
        final FleetRequestStatus target;
        final String action;
        final Set<RoleCode> roles;

        Endpoint(String path, FleetRequestStatus source, FleetRequestStatus target, String action, RoleCode role) {
            this.path = path;
            this.source = source;
            this.target = target;
            this.action = action;
            this.roles = Set.of(role, RoleCode.SUPERVISOR);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private FleetRequestRepository requests;
    @Autowired private WorkflowHistoryRepository historyRepository;
    @Autowired private AuditLogRepository auditLogs;

    private final Map<RoleCode, Session> sessions = new EnumMap<>(RoleCode.class);
    private User creator;
    private World world;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        for (RoleCode role : RoleCode.values()) {
            User user = users.create(role);
            sessions.put(role, loginOk(mockMvc, user.getUsername()));
            if (role == RoleCode.MARKETING_MANAGER) {
                creator = user;
            }
        }
        world = data.world();
    }

    static Stream<Arguments> endpointsAndRoles() {
        return Arrays.stream(Endpoint.values())
                .flatMap(e -> Arrays.stream(RoleCode.values()).map(r -> Arguments.of(e, r)));
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("endpointsAndRoles")
    void eachEndpointSucceedsForItsRolesAndIsForbiddenForOthers(Endpoint endpoint, RoleCode role) throws Exception {
        FleetRequest request = data.request(world, 10, endpoint.source, creator, true);
        long historyBefore = historyRepository.countByFleetRequestId(request.getId());

        ResultActions result = call(endpoint, request.getId(), role);

        if (endpoint.roles.contains(role)) {
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(endpoint.target.name()))
                    .andExpect(jsonPath("$.version").value(request.getVersion() + 1));
            assertThat(historyRepository.countByFleetRequestId(request.getId())).isEqualTo(historyBefore + 1);
            List<AuditLog> audit = auditLogs.findByEntityTypeAndEntityIdOrderById("FLEET_REQUEST", request.getId().toString());
            assertThat(audit).hasSize(1);
            AuditLog row = audit.getFirst();
            assertThat(row.getAction()).isEqualTo("WORKFLOW_" + endpoint.action);
            assertThat(row.getActorRole()).isEqualTo(role.name());
            assertThat((String) JsonPath.read(row.getBeforeJson(), "$.status")).isEqualTo(endpoint.source.name());
            assertThat((String) JsonPath.read(row.getAfterJson(), "$.status")).isEqualTo(endpoint.target.name());
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(endpoint.source);
            assertThat(historyRepository.countByFleetRequestId(request.getId())).isEqualTo(historyBefore);
        }
    }

    static Stream<Arguments> endpointsAllowedRolesAndWrongStates() {
        return Arrays.stream(Endpoint.values()).flatMap(e -> e.roles.stream().flatMap(r ->
                Arrays.stream(FleetRequestStatus.values()).filter(s -> s != e.source).map(s -> Arguments.of(e, r, s))));
    }

    @ParameterizedTest(name = "{0} as {1} from {2} -> 409")
    @MethodSource("endpointsAllowedRolesAndWrongStates")
    void eachEndpointIsAConflictFromEveryOtherState(Endpoint endpoint, RoleCode role, FleetRequestStatus from) throws Exception {
        FleetRequest request = data.request(world, 10, from, creator, true);

        call(endpoint, request.getId(), role)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Cannot " + endpoint.action + " a request in status " + from + "."));
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(from);
        assertThat(historyRepository.countByFleetRequestId(request.getId())).isZero();
    }

    @Test
    void qualifyingWithoutStockMappingIsAConflict() throws Exception {
        FleetRequest request = data.request(world, 10, PENDING_MARKETING_REVIEW, creator, false);
        call(Endpoint.SUBMIT_FOR_QUALIFICATION, request.getId(), RoleCode.MARKETING_MANAGER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Stock must be mapped before the request can be qualified."));
    }

    @Test
    void rejectingWithoutAReasonIsABadRequest() throws Exception {
        FleetRequest request = data.request(world, 10, PENDING_EXECUTIVE_APPROVAL, creator, true);
        for (String body : List.of("{}", "{\"reason\":\"   \"}")) {
            mockMvc.perform(post("/api/fleet-requests/{id}/reject", request.getId())
                            .header(HttpHeaders.AUTHORIZATION, sessions.get(RoleCode.MARKETING_HEAD).bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.reason").exists());
        }
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(PENDING_EXECUTIVE_APPROVAL);
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        FleetRequest request = data.request(world, 10, PENDING_EXECUTIVE_APPROVAL, creator, true);
        mockMvc.perform(post("/api/fleet-requests/{id}/approve", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, sessions.get(RoleCode.MARKETING_HEAD).bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + (request.getVersion() + 5) + "}"))
                .andExpect(status().isConflict());
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(PENDING_EXECUTIVE_APPROVAL);
    }

    @Test
    void anUnknownRequestIsNotFound() throws Exception {
        call(Endpoint.APPROVE, Long.MAX_VALUE, RoleCode.MARKETING_HEAD).andExpect(status().isNotFound());
    }

    @Test
    void rejectThenResubmitThenRequalifyLoop() throws Exception {
        FleetRequest request = data.request(world, 10, PENDING_EXECUTIVE_APPROVAL, creator, true);
        Long id = request.getId();

        call(Endpoint.REJECT, id, RoleCode.MARKETING_HEAD)
                .andExpect(jsonPath("$.status").value("RETURNED_TO_MARKETING"))
                .andExpect(jsonPath("$.returnReason").value("Needs a smaller allocation"))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
        call(Endpoint.RESUBMIT, id, RoleCode.MARKETING_MANAGER)
                .andExpect(jsonPath("$.status").value("PENDING_MARKETING_REVIEW"))
                .andExpect(jsonPath("$.allowedActions[0]").value("QUALIFY"));
        call(Endpoint.SUBMIT_FOR_QUALIFICATION, id, RoleCode.MARKETING_MANAGER)
                .andExpect(jsonPath("$.status").value("MARKETING_QUALIFIED"));
        call(Endpoint.FORWARD_TO_MARKETING_HEAD, id, RoleCode.MARKETING_MANAGER)
                .andExpect(jsonPath("$.status").value("PENDING_EXECUTIVE_APPROVAL"));

        assertThat(historyRepository.countByFleetRequestId(id)).isEqualTo(4);
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("FLEET_REQUEST", id.toString()))
                .extracting(AuditLog::getAction)
                .containsExactly("WORKFLOW_REJECT", "WORKFLOW_RESUBMIT", "WORKFLOW_QUALIFY", "WORKFLOW_FORWARD_TO_EXECUTIVE");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/fleet-requests/{id}/history", id)
                        .header(HttpHeaders.AUTHORIZATION, sessions.get(RoleCode.FINANCE_TEAM).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("REJECT"))
                .andExpect(jsonPath("$.content[0].reason").value("Needs a smaller allocation"))
                .andExpect(jsonPath("$.content[0].actorRole").value("MARKETING_HEAD"))
                .andExpect(jsonPath("$.content[3].toStatus").value("PENDING_EXECUTIVE_APPROVAL"));
    }

    private ResultActions call(Endpoint endpoint, Long id, RoleCode role) throws Exception {
        var builder = post("/api/fleet-requests/{id}/" + endpoint.path, id)
                .header(HttpHeaders.AUTHORIZATION, sessions.get(role).bearer());
        if (endpoint == Endpoint.REJECT) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Needs a smaller allocation\"}");
        }
        return mockMvc.perform(builder);
    }
}
