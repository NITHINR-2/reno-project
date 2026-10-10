package com.renault.fleet.inventory;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogInventoryApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;

    private Session finance;
    private Session admin;
    private World world;
    private InventoryStock stock;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        finance = loginOk(mockMvc, users.create(RoleCode.FINANCE_TEAM).getUsername());
        admin = loginOk(mockMvc, users.create(RoleCode.ADMIN).getUsername());
        world = data.world();
        stock = data.stock(world.variant(), 12);
        data.stock(world.variant(), 0);
    }

    @Test
    void vehicleModelsComeWithTheirVariants() throws Exception {
        get("/api/vehicle-models?q=" + world.model().getCode(), finance)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].vehicleCategory").value("T_SUV"))
                .andExpect(jsonPath("$.content[0].variants[0].code").value(world.variant().getCode()))
                .andExpect(jsonPath("$.content[0].variants[0].unitPrice").value(1200000.00));
        get("/api/vehicle-models/" + world.model().getId(), finance)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(1));
        get("/api/vehicle-models/" + Long.MAX_VALUE, finance).andExpect(status().isNotFound());
    }

    @Test
    void stockAndLocationsAreFilterable() throws Exception {
        get("/api/inventory/stock?variantId=" + world.variant().getId(), finance)
                .andExpect(jsonPath("$.totalElements").value(2));
        get("/api/inventory/stock?variantId=" + world.variant().getId() + "&availableOnly=true", finance)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(stock.getId()))
                .andExpect(jsonPath("$.content[0].quantityAvailable").value(12))
                .andExpect(jsonPath("$.content[0].locationType").value("YARD"));
        get("/api/inventory/locations?type=YARD&active=true&size=1", finance)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void adminHasNoCatalogueOrInventoryAccess() throws Exception {
        get("/api/vehicle-models", admin).andExpect(status().isForbidden());
        get("/api/inventory/stock", admin).andExpect(status().isForbidden());
        get("/api/inventory/locations", admin).andExpect(status().isForbidden());
    }

    private ResultActions get(String url, Session session) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }
}
