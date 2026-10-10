package com.renault.fleet.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.inventory.dto.MapStockRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Real concurrency against PostgreSQL: several threads, each in its own transaction, race for the last units of
 * one stock row. The row lock serialises them, so losers see the committed stock and get a clean 409
 * ("Insufficient stock"), never an optimistic-lock failure or a negative balance. Not @Transactional on purpose.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockAllocationConcurrencyTest {

    @Autowired private StockAllocationService service;
    @Autowired private TestFleetData data;
    @Autowired private InventoryStockRepository stockRepository;
    @Autowired private StockAllocationRepository allocationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User manager;
    private AuthenticatedUser actor;

    @BeforeAll
    void setUp() {
        manager = new TestUsers(userRepository, roleRepository, passwordEncoder).create(RoleCode.MARKETING_MANAGER);
        actor = new AuthenticatedUser(manager.getId(), manager.getUsername(), manager.getFullName(), manager.getEmail(),
                RoleCode.MARKETING_MANAGER, Set.of("STOCK_MAP"));
    }

    @Test
    void twoThreadsRaceForTheLastUnitAndExactlyOneWins() throws Exception {
        Outcome outcome = race(2, 1);

        assertThat(outcome.winners()).isEqualTo(1);
        assertThat(outcome.conflicts()).isEqualTo(1);
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.stock().getQuantityAvailable()).isZero();
        assertThat(outcome.stock().getQuantityAllocated()).isEqualTo(1);
        assertThat(outcome.activeAllocations()).isEqualTo(1);
    }

    @Test
    void eightThreadsRaceForFiveUnitsAndExactlyFiveWin() throws Exception {
        Outcome outcome = race(8, 5);

        assertThat(outcome.winners()).isEqualTo(5);
        assertThat(outcome.conflicts()).isEqualTo(3);
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.stock().getQuantityAvailable()).isZero();
        assertThat(outcome.stock().getQuantityAllocated()).isEqualTo(5);
        assertThat(outcome.activeAllocations()).isEqualTo(5);
    }

    private record Outcome(int winners, int conflicts, List<Throwable> unexpected, InventoryStock stock, long activeAllocations) {
    }

    /** {@code threads} different requests each try to take 1 unit from a stock row holding {@code units}. */
    private Outcome race(int threads, int units) throws Exception {
        World world = data.world();
        InventoryStock stock = data.stock(world.variant(), units);
        List<FleetRequest> requests = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            requests.add(data.request(world, 10, manager));
        }
        MapStockRequest oneUnit = new MapStockRequest(null, List.of(new MapStockRequest.Line(stock.getId(), 1)));

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (FleetRequest request : requests) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return service.mapStock(request.getId(), oneUnit, actor);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int winners = 0;
            int conflicts = 0;
            List<Throwable> unexpected = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    winners++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    if (ex.getCause() instanceof ConflictException conflict
                            && conflict.getMessage().startsWith("Insufficient stock")) {
                        conflicts++;
                    } else {
                        unexpected.add(ex.getCause());
                    }
                }
            }
            long active = requests.stream()
                    .mapToLong(r -> allocationRepository.countByFleetRequestIdAndStatus(r.getId(), AllocationStatus.ACTIVE))
                    .sum();
            return new Outcome(winners, conflicts, unexpected, stockRepository.findById(stock.getId()).orElseThrow(), active);
        } finally {
            pool.shutdownNow();
        }
    }
}
