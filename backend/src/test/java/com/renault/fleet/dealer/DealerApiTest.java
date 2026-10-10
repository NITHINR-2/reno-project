package com.renault.fleet.dealer;

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
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DealerApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private AuditLogRepository auditLogs;

    private Session admin;
    private Session mm;
    private World world;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        admin = loginOk(mockMvc, users.create(RoleCode.ADMIN).getUsername());
        mm = loginOk(mockMvc, users.create(RoleCode.MARKETING_MANAGER).getUsername());
        world = data.world();
    }

    @Test
    void adminOnboardsAndActivatesADealer() throws Exception {
        String code = "DLR-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String json = send(post("/api/dealers"), """
                {"dealerCode":"%s","name":"Coimbatore Motors","regionId":%d,"gstin":"33ABCDE1234F1Z5",
                 "contactEmail":"fleet@cbe.example","city":"Coimbatore","state":"Tamil Nadu","postalCode":"641001"}
                """.formatted(code, world.region().getId()), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_ONBOARDING"))
                .andExpect(jsonPath("$.onboardedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(json, "$.id");

        send(patch("/api/dealers/{id}", id), "{\"version\":0,\"status\":\"ACTIVE\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.onboardedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
        send(patch("/api/dealers/{id}", id), "{\"version\":1,\"status\":\"PENDING_ONBOARDING\"}", admin)
                .andExpect(status().isConflict());
        send(patch("/api/dealers/{id}", id), "{\"version\":0,\"name\":\"Stale\"}", admin)
                .andExpect(status().isConflict());
        send(post("/api/dealers"), """
                {"dealerCode":"%s","name":"Dup","regionId":%d,"city":"X","state":"Y"}
                """.formatted(code, world.region().getId()), admin)
                .andExpect(status().isConflict());

        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("DEALER", id.toString()))
                .extracting(a -> a.getAction()).containsExactly("CREATE", "UPDATE");
    }

    @Test
    void validationAndPermissions() throws Exception {
        send(post("/api/dealers"), "{\"dealerCode\":\"bad code\",\"gstin\":\"NOTAGSTIN\",\"contactEmail\":\"x\"}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dealerCode").exists())
                .andExpect(jsonPath("$.errors.gstin").exists())
                .andExpect(jsonPath("$.errors.contactEmail").exists())
                .andExpect(jsonPath("$.errors.regionId").exists())
                .andExpect(jsonPath("$.errors.city").exists());
        // A well-formed body without the permission is 403 (body validation runs before method security).
        send(post("/api/dealers"), """
                {"dealerCode":"NOPE","name":"No","regionId":%d,"city":"X","state":"Y"}
                """.formatted(world.region().getId()), mm).andExpect(status().isForbidden());
    }

    @Test
    void listIsFilterableForFleetRolesAndAdmins() throws Exception {
        World world = data.world(); // its own region: other tests add dealers to the shared one
        for (Session session : new Session[] {admin, mm}) {
            mockMvc.perform(get("/api/dealers").param("regionId", world.region().getId().toString())
                            .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].regionName").value(world.region().getName()));
        }
        mockMvc.perform(get("/api/dealers").param("q", world.dealer().getDealerCode().toLowerCase()).param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, mm.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/dealers/{id}", world.dealer().getId()).header(HttpHeaders.AUTHORIZATION, mm.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealerCode").value(world.dealer().getDealerCode()));
        mockMvc.perform(get("/api/dealers/{id}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, mm.bearer()))
                .andExpect(status().isNotFound());
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String body, Session session) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
