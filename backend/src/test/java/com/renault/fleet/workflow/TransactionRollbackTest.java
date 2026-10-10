package com.renault.fleet.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.inventory.dto.MapStockRequest;
import com.renault.fleet.inventory.dto.MapStockRequest.Line;
import com.renault.fleet.inventory.entity.AllocationStatus;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import com.renault.fleet.inventory.repository.StockAllocationRepository;
import com.renault.fleet.inventory.service.StockAllocationService;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.workflow.repository.WorkflowHistoryRepository;
import com.renault.fleet.workflow.service.WorkflowService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** A failure anywhere in a business transaction leaves no partial stock, allocation, history or audit change. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionRollbackTest {

    @MockitoSpyBean private AuditService auditService;
    @Autowired private StockAllocationService stockAllocations;
    @Autowired private WorkflowService workflow;
    @Autowired private TestFleetData data;
    @Autowired private FleetRequestRepository requests;
    @Autowired private InventoryStockRepository stockRepository;
    @Autowired private StockAllocationRepository allocationRepository;
    @Autowired private WorkflowHistoryRepository historyRepository;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User manager;
    private AuthenticatedUser mm;

    @BeforeAll
    void setUp() {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        manager = users.create(RoleCode.MARKETING_MANAGER);
        mm = new AuthenticatedUser(manager.getId(), manager.getUsername(), "MM", null, RoleCode.MARKETING_MANAGER, Set.of());
    }

    @Test
    void aFailingSecondLineRollsBackTheFirstLine() {
        World world = data.world();
        InventoryStock plenty = data.stock(world.variant(), 5);
        InventoryStock scarce = data.stock(world.variant(), 1);
        FleetRequest request = data.request(world, 10, manager);

        assertThatThrownBy(() -> stockAllocations.mapStock(request.getId(), new MapStockRequest(null,
                List.of(new Line(plenty.getId(), 3), new Line(scarce.getId(), 2))), mm))
                .isInstanceOf(ConflictException.class).hasMessageStartingWith("Insufficient stock");

        assertThat(stockRepository.findById(plenty.getId()).orElseThrow().getQuantityAvailable()).isEqualTo(5);
        assertThat(stockRepository.findById(plenty.getId()).orElseThrow().getQuantityAllocated()).isZero();
        assertThat(allocationRepository.findByFleetRequestIdAndStatusOrderById(request.getId(), AllocationStatus.ACTIVE)).isEmpty();
        FleetRequest reloaded = requests.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getAllocatedQty()).isZero();
        assertThat(reloaded.getStockMappedAt()).isNull();
        assertThat(historyRepository.countByFleetRequestId(request.getId())).isZero();
        assertThat(auditLogs.findByEntityTypeAndEntityIdOrderById("FLEET_REQUEST", request.getId().toString())).isEmpty();
    }

    @Test
    void aFailedRemapKeepsTheOriginalAllocation() {
        World world = data.world();
        InventoryStock a = data.stock(world.variant(), 5);
        InventoryStock b = data.stock(world.variant(), 1);
        FleetRequest request = data.request(world, 10, manager);
        stockAllocations.mapStock(request.getId(), new MapStockRequest(null, List.of(new Line(a.getId(), 3))), mm);

        assertThatThrownBy(() -> stockAllocations.mapStock(request.getId(), new MapStockRequest(null,
                List.of(new Line(a.getId(), 2), new Line(b.getId(), 5))), mm))
                .isInstanceOf(ConflictException.class);

        assertThat(stockRepository.findById(a.getId()).orElseThrow().getQuantityAvailable()).isEqualTo(2);
        assertThat(stockRepository.findById(b.getId()).orElseThrow().getQuantityAvailable()).isEqualTo(1);
        var active = allocationRepository.findByFleetRequestIdAndStatusOrderById(request.getId(), AllocationStatus.ACTIVE);
        assertThat(active).singleElement().satisfies(x -> assertThat(x.getQuantity()).isEqualTo(3));
        assertThat(requests.findById(request.getId()).orElseThrow().getProductionRequired()).isEqualTo(7);
    }

    @Test
    void anAuditFailureRollsBackTheTransition() {
        World world = data.world();
        FleetRequest request = data.request(world, 10, FleetRequestStatus.PENDING_MARKETING_REVIEW, manager, true);
        doThrow(new IllegalStateException("audit store unavailable"))
                .when(auditService).record(argThat(e -> e != null && e.action().startsWith("WORKFLOW_")));

        assertThatThrownBy(() -> workflow.transition(request.getId(), WorkflowAction.QUALIFY, null, mm))
                .isInstanceOf(IllegalStateException.class);

        FleetRequest reloaded = requests.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FleetRequestStatus.PENDING_MARKETING_REVIEW);
        assertThat(reloaded.getVersion()).isEqualTo(request.getVersion());
        assertThat(historyRepository.countByFleetRequestId(request.getId())).isZero();
    }
}
