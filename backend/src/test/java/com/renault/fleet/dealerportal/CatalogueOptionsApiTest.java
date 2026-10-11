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
import com.renault.fleet.catalog.entity.FuelType;
import com.renault.fleet.catalog.entity.Transmission;
import com.renault.fleet.catalog.entity.VehicleColour;
import com.renault.fleet.catalog.entity.VehicleModel;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.catalog.repository.VehicleColourRepository;
import com.renault.fleet.catalog.repository.VehicleModelRepository;
import com.renault.fleet.catalog.repository.VehicleVariantRepository;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.math.BigDecimal;
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

/** V14 catalogue options: transmission and price filters, model colours, colour on order lines, colour admin. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueOptionsApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private VehicleModelRepository models;
    @Autowired private VehicleVariantRepository variants;
    @Autowired private VehicleColourRepository colours;
    @Autowired private FleetRequestRepository requests;

    private World world;
    private Session dealer;
    private Session admin;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        world = data.world();
        User user = users.create(RoleCode.DEALER);
        user.setDealer(world.dealer());
        dealer = loginOk(mockMvc, userRepository.save(user).getUsername());
        admin = loginOk(mockMvc, users.create(RoleCode.ADMIN).getUsername());
    }

    @Test
    void filtersSelectMatchingVariantsAndColoursAreListed() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        VehicleModel model = models.save(new VehicleModel("OPT-" + s, "Options " + s, "SUV"));
        variants.save(new VehicleVariant(model, "OPT-" + s + "-M", "Manual petrol", FuelType.PETROL, new BigDecimal("800000.00")));
        VehicleVariant auto = new VehicleVariant(model, "OPT-" + s + "-A", "Auto diesel", FuelType.DIESEL, new BigDecimal("1500000.00"));
        auto.setTransmission(Transmission.AUTOMATIC);
        variants.save(auto);
        colours.save(new VehicleColour(model, "Pearl White", "#F2F2EE"));
        VehicleColour retired = new VehicleColour(model, "Old Green", "#335533");
        retired.setActive(false);
        colours.save(retired);

        catalogue("q", "OPT-" + s).andExpect(jsonPath("$.content[0].variants.length()").value(2))
                .andExpect(jsonPath("$.content[0].colours.length()").value(1))
                .andExpect(jsonPath("$.content[0].colours[0].hexCode").value("#F2F2EE"));
        catalogue("q", "OPT-" + s, "transmission", "AUTOMATIC")
                .andExpect(jsonPath("$.content[0].variants.length()").value(1))
                .andExpect(jsonPath("$.content[0].variants[0].transmission").value("AUTOMATIC"));
        catalogue("q", "OPT-" + s, "fuelType", "PETROL", "maxPrice", "900000")
                .andExpect(jsonPath("$.content[0].variants[0].code").value("OPT-" + s + "-M"));
        catalogue("q", "OPT-" + s, "minPrice", "2000000").andExpect(jsonPath("$.totalElements").value(0));
        catalogue("q", "OPT-" + s, "fuelType", "ELECTRIC").andExpect(jsonPath("$.totalElements").value(0));
        as(get("/api/dealer/catalogue").param("minPrice", "10").param("maxPrice", "5"), dealer)
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOrderLineKeepsTheChosenColourAndRejectsForeignOrRetiredOnes() throws Exception {
        VehicleColour red = colours.save(new VehicleColour(world.model(), "Red " + UUID.randomUUID().toString().substring(0, 4), "#B3202A"));
        VehicleModel other = models.save(new VehicleModel("OTH-" + UUID.randomUUID().toString().substring(0, 6), "Other", "SUV"));
        VehicleColour foreign = colours.save(new VehicleColour(other, "Blue", "#1F4E79"));
        VehicleColour retired = new VehicleColour(world.model(), "Gone " + UUID.randomUUID().toString().substring(0, 4), "#000000");
        retired.setActive(false);
        colours.save(retired);

        String json = order(red.getId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].colour").value(red.getName()))
                .andReturn().getResponse().getContentAsString();
        int requestId = JsonPath.read(json, "$.lines[0].id");
        assertThat(requests.findById((long) requestId).orElseThrow().getColour()).isEqualTo(red.getName());
        order(foreign.getId()).andExpect(status().isBadRequest());
        order(retired.getId()).andExpect(status().isConflict());
        order(null).andExpect(status().isCreated()).andExpect(jsonPath("$.lines[0].colour").doesNotExist());
    }

    @Test
    void theSameVariantMayBeOrderedInTwoColoursButNotTwiceInOne() throws Exception {
        VehicleColour white = colours.save(new VehicleColour(world.model(), "W " + UUID.randomUUID().toString().substring(0, 4), "#FFFFFF"));
        VehicleColour black = colours.save(new VehicleColour(world.model(), "B " + UUID.randomUUID().toString().substring(0, 4), "#000000"));
        String line = "{\"variantId\":%d,\"quantity\":1,\"colourId\":%d}";
        String two = "{\"lines\":[" + line.formatted(world.variant().getId(), white.getId()) + ","
                + line.formatted(world.variant().getId(), black.getId()) + "]}";
        as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(two), dealer)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lines.length()").value(2));
        String same = "{\"lines\":[" + line.formatted(world.variant().getId(), white.getId()) + ","
                + line.formatted(world.variant().getId(), white.getId()) + "]}";
        as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(same), dealer)
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminsManageColoursAndTransmissions() throws Exception {
        String json = as(post("/api/admin/catalogue/models/" + world.model().getId() + "/colours")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ice White\",\"hexCode\":\"#f4f6f7\"}"), admin)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.hexCode").value("#F4F6F7"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.id");
        as(post("/api/admin/catalogue/models/" + world.model().getId() + "/colours")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ICE WHITE\",\"hexCode\":\"#FFFFFF\"}"), admin)
                .andExpect(status().isConflict());
        as(post("/api/admin/catalogue/models/" + world.model().getId() + "/colours")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bad\",\"hexCode\":\"white\"}"), admin)
                .andExpect(status().isBadRequest());
        as(patch("/api/admin/catalogue/colours/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"active\":false}"), admin).andExpect(jsonPath("$.active").value(false));
        as(patch("/api/admin/catalogue/variants/" + world.variant().getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":%d,\"transmission\":\"DCT\"}".formatted(world.variant().getVersion())), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.transmission").value("DCT"));
        as(get("/api/admin/catalogue/models/" + world.model().getId()), admin)
                .andExpect(jsonPath("$.colours[?(@.id == %d)].active".formatted(id)).value(false));
        as(post("/api/admin/catalogue/models/" + world.model().getId() + "/colours")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"hexCode\":\"#000000\"}"), dealer)
                .andExpect(status().isForbidden());
    }

    private ResultActions catalogue(String... params) throws Exception {
        MockHttpServletRequestBuilder builder = get("/api/dealer/catalogue");
        for (int i = 0; i < params.length; i += 2) {
            builder.param(params[i], params[i + 1]);
        }
        return as(builder, dealer).andExpect(status().isOk());
    }

    private ResultActions order(Long colourId) throws Exception {
        return as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(
                "{\"lines\":[{\"variantId\":%d,\"quantity\":2,\"colourId\":%s}]}".formatted(world.variant().getId(), colourId)), dealer);
    }

    private ResultActions as(MockHttpServletRequestBuilder builder, Session session) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }
}
