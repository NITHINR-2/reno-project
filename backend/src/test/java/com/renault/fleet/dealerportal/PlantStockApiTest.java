package com.renault.fleet.dealerportal;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.catalog.entity.VehicleColour;
import com.renault.fleet.catalog.repository.VehicleColourRepository;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.inventory.entity.InventoryLocation;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.entity.LocationType;
import com.renault.fleet.inventory.repository.InventoryLocationRepository;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.repository.WorkflowHistoryRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Phase D3: plant stock (on_hand/reserved), reservation on order, stale-availability 409, release and moves. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlantStockApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private PlantRepository plants;
    @Autowired private InventoryLocationRepository locations;
    @Autowired private InventoryStockRepository stock;
    @Autowired private VehicleColourRepository colours;
    @Autowired private FleetRequestRepository requests;
    @Autowired private WorkflowHistoryRepository history;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private JdbcTemplate jdbc;

    private final Map<RoleCode, Session> hq = new EnumMap<>(RoleCode.class);
    private World world;
    private Session dealerA;
    private Session dealerB;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        for (RoleCode role : List.of(RoleCode.MARKETING_MANAGER, RoleCode.MARKETING_HEAD, RoleCode.SUPERVISOR)) {
            hq.put(role, loginOk(mockMvc, users.create(role).getUsername()));
        }
        world = data.world();
        dealerA = loginOk(mockMvc, dealerUser(users, world).getUsername());
        dealerB = loginOk(mockMvc, dealerUser(users, data.world()).getUsername());
    }

    @Test
    void twoDealersRacingForTheLastUnitsExactlyOneGetsThem() throws Exception {
        Fixture f = fixture(3);
        String body = order(f, 3, 3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (Session session : List.of(dealerA, dealerB)) {
                Callable<MvcResult> call = () -> {
                    start.await();
                    return as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(body), session).andReturn();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            MvcResult loser = null;
            for (Future<MvcResult> result : results) {
                MvcResult r = result.get();
                statuses.add(r.getResponse().getStatus());
                if (r.getResponse().getStatus() == 409) loser = r;
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat((Integer) JsonPath.read(loser.getResponse().getContentAsString(), "$.lines[0].available")).isZero();
        } finally {
            pool.shutdown();
        }
        InventoryStock row = stock.findById(f.stock().getId()).orElseThrow();
        assertThat(row.getReserved()).isEqualTo(3);
        assertThat(row.getOnHand()).isEqualTo(3);
        assertReservedNeverExceedsOnHand();
    }

    @Test
    void anOrderReservesWhatIsFreeAndProducesTheRest() throws Exception {
        Fixture f = fixture(4);
        String json = as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(order(f, 10, null)), dealerA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].plantName").value(f.plant().getName()))
                .andExpect(jsonPath("$.lines[0].fromStock").value(4))
                .andExpect(jsonPath("$.lines[0].toProduce").value(6))
                .andReturn().getResponse().getContentAsString();
        FleetRequest request = requests.findDetailedById(((Integer) JsonPath.read(json, "$.lines[0].id")).longValue()).orElseThrow();
        assertThat(request.getPlant().getId()).isEqualTo(f.plant().getId());
        assertThat(request.getAllocatedQty()).isEqualTo(4);
        assertThat(request.getProductionRequired()).isEqualTo(6);
        assertThat(request.getStockMappedAt()).isNotNull();
        assertThat(stock.findById(f.stock().getId()).orElseThrow().getQuantityAvailable()).isZero();
        assertThat(history.countByFleetRequestId(request.getId())).isEqualTo(2); // CREATED + STOCK_RESERVED
    }

    @Test
    void staleAvailabilityIs409WithTheCurrentCountAndTheDealerCanAcceptIt() throws Exception {
        Fixture f = fixture(2);
        as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(order(f, 5, 5)), dealerA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Stock Changed"))
                .andExpect(jsonPath("$.plantId").value(f.plant().getId()))
                .andExpect(jsonPath("$.lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.lines[0].expectedFromStock").value(5))
                .andExpect(jsonPath("$.lines[0].available").value(2));
        assertThat(stock.findById(f.stock().getId()).orElseThrow().getReserved()).isZero(); // nothing was held
        as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(order(f, 5, 2)), dealerA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].fromStock").value(2))
                .andExpect(jsonPath("$.lines[0].toProduce").value(3));
        as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(order(f, 2, 3)), dealerA)
                .andExpect(status().isBadRequest()); // can't expect more from stock than the line quantity
    }

    @Test
    void availabilityByPlantCountsOnlyFreeUnitsOfThatPlant() throws Exception {
        Fixture f = fixture(6);
        as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(order(f, 2, 2)), dealerA)
                .andExpect(status().isCreated());
        as(get("/api/dealer/catalogue/availability").param("plantId", String.valueOf(f.plant().getId())), dealerB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.variantId == %d && @.colourId == %d)].available"
                        .formatted(world.variant().getId(), f.colour().getId())).value(4));
        as(get("/api/dealer/plants"), dealerB).andExpect(jsonPath("$[?(@.id == %d)].name".formatted(f.plant().getId()))
                .value(f.plant().getName()));
        as(get("/api/dealer/catalogue/availability").param("plantId", String.valueOf(f.plant().getId())), hq.get(RoleCode.SUPERVISOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void reservationsAreReleasedOnDealerCancelExecutiveRejectAndSupervisorCancel() throws Exception {
        Fixture f = fixture(9);
        long dealerCancel = placeOne(f, 3);
        long rejected = placeOne(f, 3);
        long supervisorCancel = placeOne(f, 3);
        assertThat(stock.findById(f.stock().getId()).orElseThrow().getReserved()).isEqualTo(9);

        as(post("/api/dealer/requests/" + dealerCancel + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Changed our mind\"}"), dealerA).andExpect(status().isOk());
        assertThat(stock.findById(f.stock().getId()).orElseThrow().getReserved()).isEqualTo(6);

        jdbc.update("update fleet_requests set status = 'PENDING_EXECUTIVE_APPROVAL' where id = ?", rejected);
        as(post("/api/fleet-requests/" + rejected + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Price too low\"}"), hq.get(RoleCode.MARKETING_HEAD)).andExpect(status().isOk());
        FleetRequest returned = requests.findById(rejected).orElseThrow();
        assertThat(returned.getAllocatedQty()).isZero();
        assertThat(returned.getStockMappedAt()).isNull(); // Marketing must re-map before qualifying again
        assertThat(stock.findById(f.stock().getId()).orElseThrow().getReserved()).isEqualTo(3);

        as(post("/api/fleet-requests/" + supervisorCancel + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Duplicate\"}"), hq.get(RoleCode.SUPERVISOR)).andExpect(status().isOk());
        InventoryStock row = stock.findById(f.stock().getId()).orElseThrow();
        assertThat(row.getReserved()).isZero();
        assertThat(row.getOnHand()).isEqualTo(9);
        assertReservedNeverExceedsOnHand();
    }

    @Test
    void marketingCanMoveAReservationToAnotherPlant() throws Exception {
        Fixture from = fixture(5);
        Fixture to = fixtureAt(newPlant(), from.colour(), 2);
        long id = placeOne(from, 4);
        long version = requests.findById(id).orElseThrow().getVersion();
        as(post("/api/fleet-requests/" + id + "/move-reservation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plantId\":%d,\"version\":%d,\"reason\":\"Closer to the dealer\"}".formatted(to.plant().getId(), version)),
                hq.get(RoleCode.MARKETING_MANAGER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocatedQty").value(2))
                .andExpect(jsonPath("$.productionRequired").value(2))
                .andExpect(jsonPath("$.plant.id").value(to.plant().getId()));
        assertThat(stock.findById(from.stock().getId()).orElseThrow().getReserved()).isZero();
        assertThat(stock.findById(to.stock().getId()).orElseThrow().getReserved()).isEqualTo(2);
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("FLEET_REQUEST", String.valueOf(id)))
                .anySatisfy(a -> {
                    assertThat(a.getAction()).isEqualTo("RESERVATION_MOVED");
                    assertThat(a.getReason()).isEqualTo("Closer to the dealer");
                });
        as(post("/api/fleet-requests/" + id + "/move-reservation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plantId\":%d,\"version\":%d}".formatted(from.plant().getId(), version + 5)), dealerA)
                .andExpect(status().isForbidden());
        jdbc.update("update fleet_requests set status = 'MARKETING_QUALIFIED' where id = ?", id);
        long current = requests.findById(id).orElseThrow().getVersion();
        as(post("/api/fleet-requests/" + id + "/move-reservation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plantId\":%d,\"version\":%d}".formatted(from.plant().getId(), current)), hq.get(RoleCode.MARKETING_MANAGER))
                .andExpect(status().isConflict());
    }

    // --- helpers ---

    private record Fixture(Plant plant, VehicleColour colour, InventoryStock stock) {
    }

    private Fixture fixture(int onHand) {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return fixtureAt(newPlant(), colours.save(new VehicleColour(world.model(), "Colour " + s, "#123456")), onHand);
    }

    private Fixture fixtureAt(Plant plant, VehicleColour colour, int onHand) {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        InventoryLocation store = new InventoryLocation("PS-" + s, "Plant store " + s, LocationType.PLANT);
        store.setPlant(plant);
        locations.save(store);
        return new Fixture(plant, colour, stock.save(new InventoryStock(store, world.variant(), colour, onHand)));
    }

    private Plant newPlant() {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return plants.save(new Plant("P-" + s, "Plant " + s, "Pune", "Maharashtra"));
    }

    private String order(Fixture f, int quantity, Integer expectedFromStock) {
        return "{\"plantId\":%d,\"lines\":[{\"variantId\":%d,\"colourId\":%d,\"quantity\":%d%s}]}".formatted(
                f.plant().getId(), world.variant().getId(), f.colour().getId(), quantity,
                expectedFromStock == null ? "" : ",\"expectedFromStock\":" + expectedFromStock);
    }

    private long placeOne(Fixture f, int quantity) throws Exception {
        String json = as(post("/api/dealer/orders").contentType(MediaType.APPLICATION_JSON).content(order(f, quantity, quantity)), dealerA)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Integer) JsonPath.read(json, "$.lines[0].id")).longValue();
    }

    private void assertReservedNeverExceedsOnHand() {
        assertThat(jdbc.queryForObject("select count(*) from inventory_stock where reserved > on_hand or reserved < 0", Long.class)).isZero();
        // Reserved always equals the active allocations against the row.
        assertThat(jdbc.queryForObject("""
                select count(*) from inventory_stock s where s.reserved <> coalesce(
                    (select sum(a.quantity) from stock_allocations a where a.stock_id = s.id and a.status = 'ACTIVE'), 0)
                """, Long.class)).isZero();
    }

    private User dealerUser(TestUsers users, World w) {
        User user = users.create(RoleCode.DEALER);
        user.setDealer(w.dealer());
        return userRepository.save(user);
    }

    private ResultActions as(MockHttpServletRequestBuilder builder, Session session) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }
}
