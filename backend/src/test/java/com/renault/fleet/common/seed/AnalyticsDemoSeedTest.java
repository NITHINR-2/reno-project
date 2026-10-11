package com.renault.fleet.common.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto;
import com.renault.fleet.analytics.service.ExecutiveAnalyticsService;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.support.TestFleetData.World;
import com.renault.fleet.support.TestUsers;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dev dashboard seed writes ~40 consistent, backdated requests. Runs in a rolled-back transaction so the demo
 * rows never reach other tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class AnalyticsDemoSeedTest {

    @Autowired private AnalyticsDemoSeed seed;
    @Autowired private ExecutiveAnalyticsService analytics;
    @Autowired private TestFleetData data;
    @Autowired private PlantRepository plants;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void seedsConsistentBackdatedRequestsThatFillTheDashboard() {
        TestUsers users = new TestUsers(userRepository, roleRepository, passwordEncoder);
        World quotable = data.worldWithGst("0.1800");
        World other = data.world();
        World third = data.world();
        InventoryStock s1 = data.stock(quotable.variant(), 1000);
        InventoryStock s2 = data.stock(other.variant(), 1000);
        InventoryStock s3 = data.stock(third.variant(), 1000);
        String sfx = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Plant plant = plants.save(new Plant("P-" + sfx, "Plant " + sfx, "Pune", "Maharashtra"));
        Long taxRateId = jdbc.queryForObject("select id from tax_rates where vehicle_category = ?", Long.class,
                quotable.model().getVehicleCategory());
        AnalyticsDemoSeed.Catalog catalog = new AnalyticsDemoSeed.Catalog(
                List.of(quotable.dealer().getId(), other.dealer().getId(), third.dealer().getId()),
                List.of(variant(quotable, taxRateId, new BigDecimal("0.1800"), s1), variant(other, null, null, s2),
                        variant(third, null, null, s3)),
                List.of(plant.getId()), s1.getLocation().getId(),
                new AnalyticsDemoSeed.Actors(users.create(RoleCode.MARKETING_MANAGER).getId(),
                        users.create(RoleCode.MARKETING_HEAD).getId(), users.create(RoleCode.FINANCE_TEAM).getId(),
                        users.create(RoleCode.SUPERVISOR).getId()));
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        assertThat(seed.alreadySeeded()).isFalse();
        List<Long> ids = seed.seed(catalog, 40, now, 6003L);
        assertThat(ids).hasSize(40);
        assertThat(seed.alreadySeeded()).isTrue();
        String in = "(" + String.join(",", ids.stream().map(String::valueOf).toList()) + ")";

        // Spread over the last 12 months, many stages, terminal and open.
        Map<String, Object> span = jdbc.queryForMap("select min(created_at) as oldest, max(created_at) as newest, "
                + "count(distinct date_trunc('month', created_at)) as months, count(distinct status) as statuses "
                + "from fleet_requests where id in " + in);
        assertThat(((Number) span.get("months")).intValue()).isGreaterThanOrEqualTo(11);
        assertThat(((Number) span.get("statuses")).intValue()).isGreaterThanOrEqualTo(5);
        List<String> statuses = jdbc.queryForList("select distinct status from fleet_requests where id in " + in, String.class);
        assertThat(statuses).contains("COMPLETED", "CANCELLED", "PENDING_MARKETING_REVIEW");

        // Never beyond finance without a GST rate; history never in the future and starts with CREATED.
        assertThat(jdbc.queryForObject("select count(*) from fleet_requests where id in " + in + " and variant_id <> ? "
                + "and status in ('QUOTATION_ISSUED','PAYMENT_PENDING','FUNDS_VERIFIED','FINANCIALLY_CLEARED',"
                + "'PRODUCTION_SCHEDULED','DISPATCH_SCHEDULED','COMPLETED')", Integer.class, quotable.variant().getId()))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from workflow_history where fleet_request_id in " + in
                + " and created_at > ?", Integer.class, java.sql.Timestamp.from(now))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from fleet_requests r where id in " + in + " and not exists "
                + "(select 1 from workflow_history h where h.fleet_request_id = r.id and h.action = 'CREATED' "
                + "and h.created_at = r.created_at)", Integer.class)).isZero();
        // Every request's status is the last status change in its history.
        assertThat(jdbc.queryForObject("""
                select count(*) from fleet_requests r where r.id in %s and r.status <> (
                    select h.to_status from workflow_history h where h.fleet_request_id = r.id
                    and (h.from_status is null or h.from_status <> h.to_status) order by h.created_at desc, h.id desc limit 1)
                """.formatted(in), Integer.class)).isZero();

        // Paid-for stages have an issued quotation and an approved deposit; open timed stages have a running clock.
        assertThat(jdbc.queryForObject("select count(*) from fleet_requests r where id in " + in
                + " and status in ('FUNDS_VERIFIED','FINANCIALLY_CLEARED','PRODUCTION_SCHEDULED','DISPATCH_SCHEDULED','COMPLETED')"
                + " and (not exists (select 1 from quotations q where q.fleet_request_id = r.id and q.status = 'ISSUED')"
                + " or not exists (select 1 from payment_receipts p where p.fleet_request_id = r.id and p.status = 'APPROVED'))",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from fleet_requests r join sla_policies p on p.stage = r.status "
                + "where r.id in " + in + " and r.sla_due_at is null", Integer.class)).isZero();

        // Stock: active allocations match each request's allocated quantity and the stock rows' allocated counts.
        assertThat(jdbc.queryForObject("""
                select count(*) from fleet_requests r where r.id in %s and r.status not in ('COMPLETED','CANCELLED')
                and r.allocated_qty <> coalesce((select sum(quantity) from stock_allocations a
                    where a.fleet_request_id = r.id and a.status = 'ACTIVE'), 0)
                """.formatted(in), Integer.class)).isZero();
        for (InventoryStock s : List.of(s1, s2, s3)) {
            Map<String, Object> row = jdbc.queryForMap("select quantity_allocated, quantity_available from inventory_stock where id = ?", s.getId());
            Integer active = jdbc.queryForObject("select coalesce(sum(quantity), 0) from stock_allocations where stock_id = ? "
                    + "and status = 'ACTIVE'", Integer.class, s.getId());
            Integer used = jdbc.queryForObject("select coalesce(sum(quantity), 0) from stock_allocations where stock_id = ? "
                    + "and status in ('ACTIVE','CONSUMED')", Integer.class, s.getId());
            assertThat(((Number) row.get("quantity_allocated")).intValue()).isEqualTo(active);
            assertThat(((Number) row.get("quantity_available")).intValue()).isEqualTo(1000 - used);
        }

        // The dashboard has something to show for the last year.
        LocalDate today = LocalDate.now(ExecutiveAnalyticsService.IST);
        ExecutiveAnalyticsDto dto = analytics.executive(today.minusDays(364), today, null, plant.getId());
        assertThat(dto.kpis().requestCount().value()).isEqualByComparingTo("40");
        assertThat(dto.monthlyOrderValue().stream().filter(m -> m.requests() > 0).count()).isGreaterThanOrEqualTo(11);
        assertThat(dto.pipeline().stream().mapToLong(ExecutiveAnalyticsDto.StageCount::count).sum()).isEqualTo(40);
        assertThat(dto.stageDurations()).hasSizeGreaterThanOrEqualTo(6).anyMatch(ExecutiveAnalyticsDto.StageDuration::slowest);
        assertThat(dto.topDealers()).hasSize(3);
        assertThat(dto.kpis().slaCompliancePct().value()).isBetween(BigDecimal.ONE, new BigDecimal("99.9"));
        assertThat(dto.kpis().avgHoursToClearance().value()).isNotNull();
        assertThat(dto.kpis().overdueRequests().value()).isPositive();
        assertThat(dto.stockVsProduction().fromStock()).isPositive();
        assertThat(dto.stockVsProduction().toProduce()).isPositive();
    }

    private static AnalyticsDemoSeed.Variant variant(World w, Long taxRateId, BigDecimal rate, InventoryStock stock) {
        return new AnalyticsDemoSeed.Variant(w.variant().getId(), w.model().getName() + " " + w.variant().getName(),
                w.variant().getUnitPrice(), taxRateId, rate, stock.getId());
    }
}
