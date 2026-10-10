package com.renault.fleet.fleetrequest;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FleetRequestAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private TestUsers users;

    @BeforeAll
    void setUp() {
        users = new TestUsers(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void unauthenticatedCallIs401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/fleet-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void adminIsForbiddenFromFleetEndpoints() throws Exception {
        Session admin = loginOk(mockMvc, users.create(RoleCode.ADMIN).getUsername());
        mockMvc.perform(get("/api/fleet-requests").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"SUPERVISOR", "MARKETING_MANAGER", "MARKETING_HEAD", "FINANCE_TEAM"})
    void fleetRolesCanListRequests(RoleCode role) throws Exception {
        Session session = loginOk(mockMvc, users.create(role).getUsername());
        mockMvc.perform(get("/api/fleet-requests").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void deactivatingAUserInvalidatesTheirAccessTokenImmediately() throws Exception {
        User user = users.create(RoleCode.MARKETING_MANAGER);
        Session session = loginOk(mockMvc, user.getUsername());
        mockMvc.perform(get("/api/fleet-requests").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        reloaded.setActive(false);
        userRepository.save(reloaded);

        mockMvc.perform(get("/api/fleet-requests").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownSortPropertyIs400NotA500() throws Exception {
        Session session = loginOk(mockMvc, users.create(RoleCode.SUPERVISOR).getUsername());
        mockMvc.perform(get("/api/fleet-requests").param("sort", "noSuchField").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
