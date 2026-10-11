package com.renault.fleet.auth;

import static com.renault.fleet.support.AuthFlow.login;
import static com.renault.fleet.support.AuthFlow.loginOk;
import static com.renault.fleet.support.AuthFlow.refresh;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.entity.AuditLog;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Dealer accounts and one-time set-password links: single use, expiry, replacement, sign-out everywhere. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SetPasswordApiTest {

    private static final String NEW_PASSWORD = "Dealer-Portal-2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private JdbcTemplate jdbc;

    private Session admin;
    private Long dealerId;
    private int clientNo;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        admin = loginOk(mockMvc, users.create(RoleCode.ADMIN).getUsername());
        dealerId = data.world().dealer().getId();
    }

    @Test
    void anInvitedDealerSetsAPasswordOnceAndCanThenSignIn() throws Exception {
        String username = newDealerAccount();
        login(mockMvc, username, NEW_PASSWORD).andExpect(status().isUnauthorized());

        String token = issueLink(username);
        check(token).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username));
        setPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
        Session session = loginOk(mockMvc, username, NEW_PASSWORD);
        assertThat(session.accessToken()).isNotBlank();

        // Single use: the same link can neither be checked nor used again.
        check(token).andExpect(status().isGone());
        setPassword(token, "Another-Passw0rd-1").andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("This set-password link has already been used."));
        loginOk(mockMvc, username, NEW_PASSWORD);

        User user = userRepository.findWithAuthoritiesByUsername(username).orElseThrow();
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("USER", String.valueOf(user.getId())))
                .extracting(AuditLog::getAction)
                .contains("CREATE", "SET_PASSWORD_LINK_ISSUED", "PASSWORD_SET_VIA_LINK");
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("USER", String.valueOf(user.getId())))
                .allSatisfy(a -> assertThat(String.valueOf(a.getReason()) + a.getAfterJson()).doesNotContain(token));
        assertThat(jdbc.queryForObject("select count(*) from set_password_tokens where token_hash = ?", Long.class, token))
                .isZero(); // only the hash is stored
    }

    @Test
    void anExpiredLinkIsGone() throws Exception {
        String username = newDealerAccount();
        String token = issueLink(username);
        jdbc.update("""
                update set_password_tokens set created_at = now() - interval '4 days', expires_at = now() - interval '1 second'
                where user_id = (select id from users where username = ?)""", username);
        check(token).andExpect(status().isGone());
        setPassword(token, NEW_PASSWORD).andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("expired")));
        login(mockMvc, username, NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void aNewLinkReplacesTheOldOneAndUnknownTokensAreNotFound() throws Exception {
        String username = newDealerAccount();
        String first = issueLink(username);
        String second = issueLink(username);
        setPassword(first, NEW_PASSWORD).andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("This set-password link was replaced by a newer one."));
        check("x".repeat(43)).andExpect(status().isNotFound());
        setPassword(second, "short").andExpect(status().isBadRequest());
        setPassword(second, "My-" + username + "-pass").andExpect(status().isBadRequest());
        setPassword(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void settingAPasswordSignsTheUserOutEverywhere() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        User finance = users.create(RoleCode.FINANCE_TEAM);
        Session session = loginOk(mockMvc, finance.getUsername());
        setPassword(issueLink(finance.getUsername()), NEW_PASSWORD).andExpect(status().isNoContent());
        refresh(mockMvc, session.refreshToken()).andExpect(status().isUnauthorized());
        login(mockMvc, finance.getUsername(), TestUsers.PASSWORD).andExpect(status().isUnauthorized());
        loginOk(mockMvc, finance.getUsername(), NEW_PASSWORD);
    }

    @Test
    void dealerAccountsNeedADealerAndHqAccountsNeedAPassword() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        createUser("""
                {"username":"d-%s","email":"d-%1$s@x.test","fullName":"No Dealer","role":"DEALER"}""".formatted(s))
                .andExpect(status().isBadRequest());
        createUser("""
                {"username":"h-%s","email":"h-%1$s@x.test","fullName":"No Password","role":"FINANCE_TEAM"}""".formatted(s))
                .andExpect(status().isBadRequest());
        createUser("""
                {"username":"h2-%s","email":"h2-%1$s@x.test","fullName":"HQ","role":"FINANCE_TEAM",
                 "password":"Long-Enough-Pass-1","dealerId":%d}""".formatted(s, dealerId))
                .andExpect(status().isBadRequest());
        String json = createUser("""
                {"username":"d2-%s","email":"d2-%1$s@x.test","fullName":"Dealer","role":"DEALER","dealerId":%d}"""
                .formatted(s, dealerId)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.dealerId").value(dealerId)).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.id");
        as(patch("/api/admin/users/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"role\":\"FINANCE_TEAM\"}"), admin).andExpect(status().isConflict());
        as(post("/api/admin/users/" + id + "/deactivate"), admin).andExpect(status().isOk());
        as(post("/api/admin/users/" + id + "/set-password-link"), admin).andExpect(status().isConflict());
    }

    // --- helpers ---

    private String newDealerAccount() throws Exception {
        String username = "dealer-" + UUID.randomUUID().toString().substring(0, 8);
        createUser("""
                {"username":"%s","email":"%1$s@dealer.test","fullName":"Invited Dealer","role":"DEALER","dealerId":%d}"""
                .formatted(username, dealerId)).andExpect(status().isCreated());
        return username;
    }

    private String issueLink(String username) throws Exception {
        Long id = userRepository.findWithAuthoritiesByUsername(username).orElseThrow().getId();
        String json = as(post("/api/admin/users/" + id + "/set-password-link"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("http://portal.test/set-password#token=")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(json, "$.url");
        return url.substring(url.indexOf("#token=") + "#token=".length());
    }

    private ResultActions createUser(String body) throws Exception {
        return as(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(body), admin);
    }

    private ResultActions check(String token) throws Exception {
        return publicPost("/api/public/set-password/check", "{\"token\":\"" + token + "\"}");
    }

    private ResultActions setPassword(String token, String password) throws Exception {
        return publicPost("/api/public/set-password", "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password));
    }

    /** Each call from its own client address, so the public rate limit (5/min in tests) never interferes. */
    private ResultActions publicPost(String path, String body) throws Exception {
        String client = "10.88.0." + (++clientNo);
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body).with(r -> {
            r.setRemoteAddr(client);
            return r;
        }));
    }

    private ResultActions as(MockHttpServletRequestBuilder builder, Session session) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }
}
