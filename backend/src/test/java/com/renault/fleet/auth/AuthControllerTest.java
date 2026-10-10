package com.renault.fleet.auth;

import static com.renault.fleet.support.AuthFlow.login;
import static com.renault.fleet.support.AuthFlow.loginOk;
import static com.renault.fleet.support.AuthFlow.refresh;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.support.AuthFlow;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthControllerTest {

    private static final String INVALID_CREDENTIALS = "Invalid username or password.";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private User financeUser;
    private User inactiveUser;

    @BeforeAll
    void createUsers() {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        financeUser = users.create(RoleCode.FINANCE_TEAM);
        inactiveUser = users.create(RoleCode.MARKETING_MANAGER, false);
    }

    @Test
    void loginSucceedsAndSetsHardenedRefreshCookie() throws Exception {
        MockHttpServletResponse response = login(mockMvc, financeUser.getUsername(), TestUsers.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.username").value(financeUser.getUsername()))
                .andExpect(jsonPath("$.user.role").value("FINANCE_TEAM"))
                .andExpect(jsonPath("$.user.permissions", containsInAnyOrder("FLEET_REQUEST_READ", "QUOTATION_READ",
                        "QUOTATION_ISSUE", "PAYMENT_RECORD", "PAYMENT_APPROVE", "FINANCE_CLEARANCE")))
                .andReturn().getResponse();

        String cookie = AuthFlow.refreshSetCookie(response);
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?", Long.class, financeUser.getId()))
                .isPositive();
        assertThat(jdbc.queryForObject("select last_login_at is not null from users where id = ?", Boolean.class, financeUser.getId()))
                .isTrue();
    }

    @Test
    void loginUsernameIsCaseInsensitive() throws Exception {
        login(mockMvc, financeUser.getUsername().toUpperCase(), TestUsers.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        expectInvalidCredentials(financeUser.getUsername(), "wrong-password");
    }

    @Test
    void loginFailsForUnknownUser() throws Exception {
        expectInvalidCredentials("nobody-" + System.nanoTime(), TestUsers.PASSWORD);
    }

    @Test
    void loginFailsForInactiveUserEvenWithCorrectPassword() throws Exception {
        expectInvalidCredentials(inactiveUser.getUsername(), TestUsers.PASSWORD);
    }

    @Test
    void loginFailureIsAudited() throws Exception {
        String username = "audit-probe-" + System.nanoTime();
        expectInvalidCredentials(username, "x");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'LOGIN_FAILED' and actor_username = ?", Long.class, username))
                .isEqualTo(1L);
    }

    @Test
    void loginRejectsBlankFieldsWithValidationProblem() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void meReturnsTheAuthenticatedUser() throws Exception {
        Session session = loginOk(mockMvc, financeUser.getUsername());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(financeUser.getId()))
                .andExpect(jsonPath("$.role").value("FINANCE_TEAM"))
                .andExpect(jsonPath("$.email").value(financeUser.getEmail()));
    }

    @Test
    void meWithoutTokenIs401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tamperedAccessTokenIsRejected() throws Exception {
        Session session = loginOk(mockMvc, financeUser.getUsername());
        String token = session.accessToken();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refreshRotatesTheTokenAndIssuesAWorkingAccessToken() throws Exception {
        Session first = loginOk(mockMvc, financeUser.getUsername());

        Session second = AuthFlow.session(refresh(mockMvc, first.refreshToken())
                .andExpect(status().isOk())
                .andReturn().getResponse());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.setCookieHeader()).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, second.bearer()))
                .andExpect(status().isOk());

        // The old token is marked rotated and points at its successor, in the same family.
        Long rotatedWithSuccessor = jdbc.queryForObject("""
                select count(*) from refresh_tokens old join refresh_tokens nxt on nxt.id = old.replaced_by_id
                where old.user_id = ? and old.rotated_at is not null and nxt.family_id = old.family_id and nxt.rotated_at is null
                """, Long.class, financeUser.getId());
        assertThat(rotatedWithSuccessor).isPositive();
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        Session original = loginOk(mockMvc, financeUser.getUsername());
        Session rotated = AuthFlow.session(refresh(mockMvc, original.refreshToken()).andReturn().getResponse());

        // Replay of the old token: rejected, cookie cleared, family revoked.
        MockHttpServletResponse replay = refresh(mockMvc, original.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse();
        assertThat(AuthFlow.refreshSetCookie(replay)).contains("Max-Age=0");

        // The legitimate latest token no longer works either.
        refresh(mockMvc, rotated.refreshToken()).andExpect(status().isUnauthorized());

        String familyId = jdbc.queryForObject("select family_id::text from refresh_tokens where token_hash = encode(sha256(?::bytea), 'hex')",
                String.class, rotated.refreshToken());
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where family_id = ?::uuid and revoked_at is null",
                Long.class, familyId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where family_id = ?::uuid and revoke_reason = 'REUSE_DETECTED'",
                Long.class, familyId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'REFRESH_TOKEN_REUSE_DETECTED' and entity_id = ?",
                Long.class, familyId)).isEqualTo(1L);
    }

    @Test
    void refreshWithoutXRequestedWithIsForbidden() throws Exception {
        Session session = loginOk(mockMvc, financeUser.getUsername());
        mockMvc.perform(post("/api/auth/refresh").cookie(session.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("X-Requested-With")));
        // The token was not consumed by the rejected call.
        refresh(mockMvc, session.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieIs401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        Session session = loginOk(mockMvc, financeUser.getUsername());

        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/logout")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(session.cookie()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(AuthFlow.refreshSetCookie(response)).contains("Max-Age=0", "Path=/api/auth");

        refresh(mockMvc, session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutXRequestedWithIsForbidden() throws Exception {
        Session session = loginOk(mockMvc, financeUser.getUsername());
        mockMvc.perform(post("/api/auth/logout").cookie(session.cookie()))
                .andExpect(status().isForbidden());
    }

    private void expectInvalidCredentials(String username, String password) throws Exception {
        MockHttpServletResponse response = login(mockMvc, username, password)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS))
                .andReturn().getResponse();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }
}
