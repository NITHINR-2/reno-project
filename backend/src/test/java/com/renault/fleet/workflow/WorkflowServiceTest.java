package com.renault.fleet.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.fleetrequest.dto.TransitionRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
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

/** Service-level workflow behaviour not reachable over HTTP in Phase 2 (cancel is SUPERVISOR-only). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowServiceTest {

    @Autowired private StockAllocationService stockAllocations;
    @Autowired private WorkflowService workflow;
    @Autowired private TestFleetData data;
    @Autowired private InventoryStockRepository stockRepository;
    @Autowired private StockAllocationRepository allocationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User manager;
    private AuthenticatedUser mm;
    private AuthenticatedUser supervisor;

    @BeforeAll
    void setUp() {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        manager = users.create(RoleCode.MARKETING_MANAGER);
        mm = new AuthenticatedUser(manager.getId(), manager.getUsername(), "MM", null, RoleCode.MARKETING_MANAGER, Set.of());
        User sup = users.create(RoleCode.SUPERVISOR);
        supervisor = new AuthenticatedUser(sup.getId(), sup.getUsername(), "SUP", null, RoleCode.SUPERVISOR, Set.of());
    }

    @Test
    void cancelRequiresAReasonAndReleasesStock() {
        World world = data.world();
        InventoryStock yard = data.stock(world.variant(), 30);
        FleetRequest request = data.request(world, 50, manager);
        stockAllocations.mapStock(request.getId(), new MapStockRequest(null, List.of(new Line(yard.getId(), 30))), mm);
        workflow.transition(request.getId(), WorkflowAction.QUALIFY, null, mm);

        assertThatThrownBy(() -> workflow.transition(request.getId(), WorkflowAction.CANCEL, new TransitionRequest(" ", null), supervisor))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> workflow.transition(request.getId(), WorkflowAction.CANCEL, new TransitionRequest("dealer withdrew", null), mm))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        var result = workflow.transition(request.getId(), WorkflowAction.CANCEL, new TransitionRequest("dealer withdrew", null), supervisor);

        assertThat(result.status()).isEqualTo(FleetRequestStatus.CANCELLED);
        assertThat(result.cancelReason()).isEqualTo("dealer withdrew");
        assertThat(result.allocatedQty()).isZero();
        assertThat(result.productionRequired()).isEqualTo(50);
        assertThat(stockRepository.findById(yard.getId()).orElseThrow().getQuantityAvailable()).isEqualTo(30);
        assertThat(allocationRepository.countByFleetRequestIdAndStatus(request.getId(), AllocationStatus.RELEASED)).isEqualTo(1);
        assertThatThrownBy(() -> workflow.transition(request.getId(), WorkflowAction.CANCEL, new TransitionRequest("again", null), supervisor))
                .isInstanceOf(ConflictException.class);
    }
}
