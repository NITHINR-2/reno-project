package com.renault.fleet.inventory;

import static com.renault.fleet.support.AuthFlow.loginOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.inventory.entity.AllocationStatus;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import com.renault.fleet.inventory.repository.StockAllocationRepository;
import com.renault.fleet.support.AuthFlow.Session;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockMappingApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestFleetData data;
    @Autowired private InventoryStockRepository stockRepository;
    @Autowired private StockAllocationRepository allocationRepository;

    private User manager;
    private Session mm;
    private Session mh;
    private Session finance;

    @BeforeAll
    void setUp() throws Exception {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        manager = users.create(RoleCode.MARKETING_MANAGER);
        mm = loginOk(mockMvc, manager.getUsername());
        mh = loginOk(mockMvc, users.create(RoleCode.MARKETING_HEAD).getUsername());
        finance = loginOk(mockMvc, users.create(RoleCode.FINANCE_TEAM).getUsername());
    }

    @Test
    void mappingThirtyOfFiftyLeavesTwentyToProduceAndMovesStock() throws Exception {
        World world = data.world();
        InventoryStock yard = data.stock(world.variant(), 30);
        data.stock(world.variant(), 5, false); // inactive location: never offered
        FleetRequest request = data.request(world, 50, manager);

        mockMvc.perform(get("/api/fleet-requests/{id}/stock-options", request.getId()).header(HttpHeaders.AUTHORIZATION, mm.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(yard.getId()))
                .andExpect(jsonPath("$.content[0].quantityAvailable").value(30));

        map(request.getId(), "[{\"stockId\":" + yard.getId() + ",\"quantity\":30}]", mm)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocatedQty").value(30))
                .andExpect(jsonPath("$.productionRequired").value(20))
                .andExpect(jsonPath("$.stockMappedAt").isNotEmpty())
                .andExpect(jsonPath("$.allocations.length()").value(1))
                .andExpect(jsonPath("$.allowedActions[0]").value("QUALIFY"));
        InventoryStock after = stockRepository.findById(yard.getId()).orElseThrow();
        assertThat(after.getQuantityAvailable()).isZero();
        assertThat(after.getQuantityAllocated()).isEqualTo(30);

        // Re-mapping replaces the allocation: 20 units go back to the yard.
        map(request.getId(), "[{\"stockId\":" + yard.getId() + ",\"quantity\":10}]", mm)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocatedQty").value(10))
                .andExpect(jsonPath("$.productionRequired").value(40));
        after = stockRepository.findById(yard.getId()).orElseThrow();
        assertThat(after.getQuantityAvailable()).isEqualTo(20);
        assertThat(after.getQuantityAllocated()).isEqualTo(10);
        assertThat(allocationRepository.countByFleetRequestIdAndStatus(request.getId(), AllocationStatus.ACTIVE)).isEqualTo(1);
        assertThat(allocationRepository.countByFleetRequestIdAndStatus(request.getId(), AllocationStatus.RELEASED)).isEqualTo(1);

        // An empty mapping is valid: produce everything.
        map(request.getId(), "[]", mm)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocatedQty").value(0))
                .andExpect(jsonPath("$.productionRequired").value(50));
        assertThat(stockRepository.findById(yard.getId()).orElseThrow().getQuantityAvailable()).isEqualTo(30);
    }

    @Test
    void invalidMappingsAreRejected() throws Exception {
        World world = data.world();
        InventoryStock yard = data.stock(world.variant(), 30);
        InventoryStock otherVariant = data.stock(data.world().variant(), 30);
        InventoryStock closed = data.stock(world.variant(), 30, false);
        FleetRequest request = data.request(world, 20, manager);
        Long id = request.getId();

        map(id, "[{\"stockId\":" + yard.getId() + ",\"quantity\":21}]", mm).andExpect(status().isConflict());
        map(id, "[{\"stockId\":" + yard.getId() + ",\"quantity\":1},{\"stockId\":" + yard.getId() + ",\"quantity\":1}]", mm)
                .andExpect(status().isBadRequest());
        map(id, "[{\"stockId\":" + otherVariant.getId() + ",\"quantity\":1}]", mm).andExpect(status().isConflict());
        map(id, "[{\"stockId\":" + closed.getId() + ",\"quantity\":1}]", mm).andExpect(status().isConflict());
        map(id, "[{\"stockId\":" + yard.getId() + ",\"quantity\":0}]", mm)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").exists());
        map(id, "[{\"stockId\":" + Long.MAX_VALUE + ",\"quantity\":1}]", mm).andExpect(status().isNotFound());
        assertThat(stockRepository.findById(yard.getId()).orElseThrow().getQuantityAvailable()).isEqualTo(30);
    }

    @Test
    void onlyMarketingReviewRequestsCanBeMappedAndOnlyByStockMappers() throws Exception {
        World world = data.world();
        InventoryStock yard = data.stock(world.variant(), 30);
        FleetRequest qualified = data.request(world, 10, FleetRequestStatus.MARKETING_QUALIFIED, manager, true);
        FleetRequest pending = data.request(world, 10, manager);
        String lines = "[{\"stockId\":" + yard.getId() + ",\"quantity\":5}]";

        map(qualified.getId(), lines, mm).andExpect(status().isConflict());
        map(pending.getId(), lines, mh).andExpect(status().isForbidden());
        map(pending.getId(), lines, finance).andExpect(status().isForbidden());
        assertThat(stockRepository.findById(yard.getId()).orElseThrow().getQuantityAvailable()).isEqualTo(30);
    }

    private ResultActions map(Long requestId, String linesJson, Session session) throws Exception {
        return mockMvc.perform(put("/api/fleet-requests/{id}/stock-allocations", requestId)
                .header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":" + linesJson + "}"));
    }
}
