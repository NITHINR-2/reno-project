package com.renault.fleet.admin;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static com.renault.fleet.support.AuthFlow.refresh;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.renault.fleet.support.AuthFlow;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
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

/** Admin Console APIs (users, audit log) and the rule that ADMIN has no operational access. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminApiTest {

    private static final String PASSWORD = "Long-Enough-Passw0rd";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogRepository auditLogs;

    private TestUsers users;
    private User adminUser;
    private Session admin;
    private Session supervisor;
    private Session manager;

    @BeforeAll
    void setUp() throws Exception {
        users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        adminUser = users.create(RoleCode.ADMIN);
        admin = loginOk(mockMvc, adminUser.getUsername());
        supervisor = loginOk(mockMvc, users.create(RoleCode.SUPERVISOR).getUsername());
        manager = loginOk(mockMvc, users.create(RoleCode.MARKETING_MANAGER).getUsername());
    }

    @Test
    void adminGets403OnEveryOperationalApi() throws Exception {
        List<MockHttpServletRequestBuilder> operational = List.of(
                get("/api/fleet-requests"), get("/api/fleet-requests/1"), get("/api/fleet-requests/1/history"),
                post("/api/fleet-requests"), patch("/api/fleet-requests/1"),
                get("/api/fleet-requests/1/stock-options"), put("/api/fleet-requests/1/stock-allocations"),
                post("/api/fleet-requests/1/submit-for-qualification"), post("/api/fleet-requests/1/forward-to-marketing-head"),
                post("/api/fleet-requests/1/resubmit"), post("/api/fleet-requests/1/approve"),
                post("/api/fleet-requests/1/reject"), post("/api/fleet-requests/1/forward-to-finance"),
                post("/api/fleet-requests/1/quotations/calculate"), get("/api/fleet-requests/1/quotations"),
                get("/api/quotations/1"), post("/api/quotations/1/issue"),
                post("/api/fleet-requests/1/payment-receipts"), get("/api/fleet-requests/1/payment-receipts"),
                get("/api/payment-receipts/1"), post("/api/payment-receipts/1/approve"),
                post("/api/fleet-requests/1/grant-clearance"), post("/api/fleet-requests/1/schedule-production"),
                post("/api/fleet-requests/1/schedule-dispatch"), post("/api/fleet-requests/1/complete"),
                post("/api/fleet-requests/1/override"), post("/api/fleet-requests/1/reassign"),
                post("/api/fleet-requests/1/cancel"),
                get("/api/vehicle-models"), get("/api/inventory/stock"), get("/api/inventory/locations"));
        for (MockHttpServletRequestBuilder request : operational) {
            // A well-formed empty JSON body, so the answer is the permission check (403), not body parsing.
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(validBodyFor(request)))
                    .andExpect(status().isForbidden());
        }
        // ...while the Admin Console is open to ADMIN.
        call(get("/api/admin/users"), admin).andExpect(status().isOk());
        call(get("/api/admin/audit-logs"), admin).andExpect(status().isOk());
        call(get("/api/dealers"), admin).andExpect(status().isOk());
    }

    @Test
    void adminManagesUsersWithoutLeakingPasswords() throws Exception {
        String username = "new.user-" + suffix();
        String json = call(post("/api/admin/users"), admin, userJson(username, "MARKETING_HEAD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MARKETING_HEAD"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(json, "$.id");
        assertThat(passwordEncoder.matches(PASSWORD, userRepository.findById(id.longValue()).orElseThrow().getPasswordHash())).isTrue();
        loginOk(mockMvc, username, PASSWORD);

        call(post("/api/admin/users"), admin, userJson(username.toUpperCase(), "MARKETING_HEAD")).andExpect(status().isBadRequest());
        call(post("/api/admin/users"), admin, userJson(username, "FINANCE_TEAM")).andExpect(status().isConflict());
        call(post("/api/admin/users"), admin, """
                {"username":"weak-%s","email":"w%s@test.local","fullName":"W","role":"FINANCE_TEAM","password":"short"}
                """.formatted(suffix(), suffix())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists());

        call(patch("/api/admin/users/" + id), admin, "{\"version\":0,\"fullName\":\"Renamed\",\"role\":\"FINANCE_TEAM\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed"))
                .andExpect(jsonPath("$.role").value("FINANCE_TEAM"));
        call(patch("/api/admin/users/" + id), admin, "{\"version\":0,\"fullName\":\"Stale\"}").andExpect(status().isConflict());

        List<AuditLog> audit = adminActions(id.toString());
        assertThat(audit).extracting(AuditLog::getAction).containsExactly("CREATE", "UPDATE");
        assertThat(audit).allSatisfy(a -> {
            assertThat(a.getAfterJson()).doesNotContain(PASSWORD).doesNotContainIgnoringCase("passwordHash").doesNotContain("$2");
            assertThat(a.getActorUsername()).isEqualTo(adminUser.getUsername());
        });
    }

    @Test
    void deactivationLocksTheUserOutImmediately() throws Exception {
        User target = users.create(RoleCode.FINANCE_TEAM);
        Session session = loginOk(mockMvc, target.getUsername());
        call(get("/api/fleet-requests"), session).andExpect(status().isOk());

        call(post("/api/admin/users/" + target.getId() + "/deactivate"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        call(get("/api/fleet-requests"), session).andExpect(status().isUnauthorized());
        refresh(mockMvc, session.refreshToken()).andExpect(status().isUnauthorized());
        AuthFlow.login(mockMvc, target.getUsername(), TestUsers.PASSWORD).andExpect(status().isUnauthorized());

        call(post("/api/admin/users/" + target.getId() + "/activate"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        loginOk(mockMvc, target.getUsername());
        assertThat(adminActions(target.getId().toString())).extracting(AuditLog::getAction).containsExactly("DEACTIVATE", "ACTIVATE");
    }

    @Test
    void adminCannotEscalateIntoSupervisorOrLockThemselvesOut() throws Exception {
        call(post("/api/admin/users"), admin, userJson("sup-" + suffix(), "SUPERVISOR")).andExpect(status().isForbidden());
        User sup = users.create(RoleCode.SUPERVISOR);
        call(post("/api/admin/users/" + sup.getId() + "/deactivate"), admin, null).andExpect(status().isForbidden());
        User fin = users.create(RoleCode.FINANCE_TEAM);
        call(patch("/api/admin/users/" + fin.getId()), admin, "{\"version\":0,\"role\":\"SUPERVISOR\"}")
                .andExpect(status().isForbidden());
        call(post("/api/admin/users/" + adminUser.getId() + "/deactivate"), admin, null).andExpect(status().isConflict());
        call(patch("/api/admin/users/" + adminUser.getId()), admin, "{\"version\":" + adminUser.getVersion() + ",\"role\":\"FINANCE_TEAM\"}")
                .andExpect(status().isConflict());

        // A SUPERVISOR may manage SUPERVISOR accounts.
        call(post("/api/admin/users"), supervisor, userJson("sup-" + suffix(), "SUPERVISOR")).andExpect(status().isCreated());
        // Fleet roles have no Admin Console access.
        call(get("/api/admin/users"), manager).andExpect(status().isForbidden());
        call(get("/api/admin/audit-logs"), manager).andExpect(status().isForbidden());
    }

    @Test
    void rolesAreListedWithTheirPermissionsForUserAdmins() throws Exception {
        call(get("/api/admin/roles"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[?(@.code == 'ADMIN')].permissions[*].code",
                        org.hamcrest.Matchers.containsInAnyOrder("ADMIN_DEALERS_MANAGE", "ADMIN_USERS_MANAGE", "AUDIT_LOG_READ")))
                .andExpect(jsonPath("$.content[?(@.code == 'SUPERVISOR')].permissions.length()").value(21));
        call(get("/api/admin/roles"), supervisor).andExpect(status().isOk());
        call(get("/api/admin/roles"), manager).andExpect(status().isForbidden());
    }

    @Test
    void auditLogIsSearchable() throws Exception {
        String username = "audited-" + suffix();
        String json = call(post("/api/admin/users"), admin, userJson(username, "MARKETING_MANAGER"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id").toString();

        call(get("/api/admin/audit-logs?entityType=user&entityId=" + id), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("CREATE"))
                .andExpect(jsonPath("$.content[0].actorUsername").value(adminUser.getUsername()))
                .andExpect(jsonPath("$.content[0].after.username").value(username));
        call(get("/api/admin/audit-logs?actorUsername=" + adminUser.getUsername().toUpperCase() + "&action=create&size=200"), admin)
                .andExpect(jsonPath("$.content[0].actorId").value(adminUser.getId()));
        call(get("/api/admin/audit-logs?entityId=" + id + "&from=" + LocalDate.now().plusDays(2)), admin)
                .andExpect(jsonPath("$.totalElements").value(0));
        call(get("/api/admin/audit-logs?action=LOGIN_SUCCESS&size=1"), supervisor)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].action").value("LOGIN_SUCCESS"));
    }

    /** USER audit rows written by the Admin Console (login events are audited against the user too). */
    private List<AuditLog> adminActions(String userId) {
        return auditLogs.findByEntityTypeAndEntityIdOrderById("USER", userId).stream()
                .filter(a -> !a.getAction().startsWith("LOGIN")).toList();
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, Session session) throws Exception {
        return call(builder, session, null);
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, Session session, String body) throws Exception {
        builder.header(HttpHeaders.AUTHORIZATION, session.bearer());
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    private static String validBodyFor(MockHttpServletRequestBuilder request) {
        return "{\"reason\":\"x\",\"version\":0,\"assigneeId\":1,\"changes\":{\"notes\":\"x\"},\"lines\":[],"
                + "\"scheduledDate\":\"2099-01-01\",\"scheduledStart\":\"2099-01-01\",\"estimatedCompletion\":\"2099-02-01\","
                + "\"dealerId\":1,\"variantId\":1,\"quantity\":1,\"requiredDeposit\":0,"
                + "\"receiptType\":\"DEPOSIT\",\"paymentMethod\":\"NEFT\",\"transactionReference\":\"X1\",\"amount\":1,"
                + "\"receivedDate\":\"2020-01-01\"}";
    }

    private static String userJson(String username, String role) {
        return """
                {"username":"%s","email":"%s@test.local","fullName":"Test User","role":"%s","password":"%s"}
                """.formatted(username, username, role, PASSWORD);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toLowerCase();
    }
}
