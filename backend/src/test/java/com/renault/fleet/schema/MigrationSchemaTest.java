package com.renault.fleet.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renault.fleet.TestcontainersConfiguration;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migrations apply to an empty Testcontainers database, Hibernate validates against them (the context loads), and
 * the key constraints actually reject bad data. Each test runs in a rolled-back transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class MigrationSchemaTest {

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;

    private long userId;
    private long variantId;
    private long requestId;
    private long taxRateId;

    @BeforeEach
    void fixtures() {
        long regionId = id("insert into regions (code, name) values ('T-REG', 'Test Region') returning id");
        long dealerId = id("insert into dealers (dealer_code, name, region_id, city, state) values ('T-DLR', 'Test Dealer', ?, 'Chennai', 'Tamil Nadu') returning id", regionId);
        long modelId = id("insert into vehicle_models (code, name, vehicle_category) values ('T-MDL', 'Test Model', 'T_SUV') returning id");
        variantId = id("insert into vehicle_variants (model_id, code, name, fuel_type, unit_price) values (?, 'T-VAR', 'Test Variant', 'PETROL', 1200000.00) returning id", modelId);
        long roleId = id("select id from roles where code = 'FINANCE_TEAM'");
        userId = id("insert into users (username, email, full_name, password_hash, role_id) values ('t-schema', 't-schema@test.local', 'T', 'x', ?) returning id", roleId);
        requestId = id("insert into fleet_requests (request_number, dealer_id, variant_id, quantity, production_required, created_by_id) values ('FLT-2099-0001', ?, ?, 50, 50, ?) returning id", dealerId, variantId, userId);
        taxRateId = id("insert into tax_rates (vehicle_category, rate, effective_from) values ('T_SUV', 0.1800, DATE '2020-01-01') returning id");
    }

    @Test
    void allMigrationsAppliedAndNothingPending() {
        assertThat(flyway.info().pending()).isEmpty();
        MigrationInfo current = flyway.info().current();
        assertThat(current.getVersion().getVersion()).isEqualTo("14");

        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE' and table_name <> 'flyway_schema_history'
                """, String.class);
        assertThat(tables).hasSize(27).contains("vehicle_model_colours", "dealer_orders", "set_password_tokens", "payment_proofs", "tax_rates", "refresh_tokens", "fleet_requests", "quotations", "audit_logs", "sla_policies");
    }

    @Test
    void rolePermissionsAreSeeded() {
        assertThat(jdbc.queryForObject("select count(*) from roles", Long.class)).isEqualTo(6L);
        assertThat(jdbc.queryForObject("""
                select count(*) from role_permissions rp join roles r on r.id = rp.role_id
                join permissions p on p.id = rp.permission_id where r.code = 'ADMIN' and p.code like 'FLEET%'
                """, Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from role_permissions rp join roles r on r.id = rp.role_id where r.code = 'SUPERVISOR'", Long.class))
                .isEqualTo(jdbc.queryForObject("select count(*) from permissions where code <> 'DEALER_PORTAL'", Long.class));
        // DEALER holds only the portal permission; no HQ role holds it.
        assertThat(jdbc.queryForList("""
                select p.code from role_permissions rp join roles r on r.id = rp.role_id
                join permissions p on p.id = rp.permission_id where r.code = 'DEALER'
                """, String.class)).containsExactly("DEALER_PORTAL");
        assertThat(jdbc.queryForObject("""
                select count(*) from role_permissions rp join roles r on r.id = rp.role_id
                join permissions p on p.id = rp.permission_id where p.code = 'DEALER_PORTAL' and r.code <> 'DEALER'
                """, Long.class)).isZero();
    }

    @Test
    void onlyOneIssuedQuotationPerRequest() {
        insertQuotation("QT-T-1", "DRAFT");
        insertQuotation("QT-T-2", "ISSUED");
        insertQuotation("QT-T-3", "DRAFT"); // drafts alongside an issued one are fine
        assertThatThrownBy(() -> insertQuotation("QT-T-4", "ISSUED")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void quotationTotalMustEqualBasePlusGst() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into quotations (quotation_number, fleet_request_id, tax_rate_id, gst_rate, base_amount, gst_amount, total_amount, created_by_id)
                values ('QT-T-BAD', ?, ?, 0.18, 100.00, 18.00, 120.00, ?)
                """, requestId, taxRateId, userId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stockCannotGoNegative() {
        long locationId = id("insert into inventory_locations (code, name, location_type) values ('T-YARD', 'Yard', 'YARD') returning id");
        long stockId = id("insert into inventory_stock (location_id, variant_id, quantity_available) values (?, ?, 30) returning id", locationId, variantId);
        assertThatThrownBy(() -> jdbc.update("update inventory_stock set quantity_available = quantity_available - 31 where id = ?", stockId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transactionReferenceIsUniqueIgnoringCaseAndSpaces() {
        insertReceipt("RCPT-T-1", "UTR-ABC-123");
        assertThatThrownBy(() -> insertReceipt("RCPT-T-2", "  utr-abc-123 ")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void taxRatePeriodsCannotOverlapForACategory() {
        jdbc.update("update tax_rates set effective_to = DATE '2025-01-01' where id = ?", taxRateId);
        jdbc.update("insert into tax_rates (vehicle_category, rate, effective_from) values ('T_SUV', 0.2800, DATE '2025-01-01')");
        assertThatThrownBy(() -> jdbc.update("insert into tax_rates (vehicle_category, rate, effective_from) values ('T_SUV', 0.1200, DATE '2024-06-01')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditLogsAreAppendOnly() {
        long auditId = id("insert into audit_logs (action, entity_type, entity_id) values ('TEST', 'TEST', '1') returning id");
        assertThatThrownBy(() -> jdbc.update("update audit_logs set action = 'TAMPERED' where id = ?", auditId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("append-only");
    }

    private void insertQuotation(String number, String status) {
        boolean issued = "ISSUED".equals(status);
        jdbc.update("""
                insert into quotations (quotation_number, fleet_request_id, status, tax_rate_id, gst_rate, base_amount, gst_amount,
                    total_amount, required_deposit, deposit_received, deposit_shortfall, issue_date, valid_until, issued_at, issued_by_id, created_by_id)
                values (?, ?, ?, ?, 0.1800, 60000000.00, 10800000.00, 70800000.00, 5000000.00, 1000000.00, 4000000.00,
                    ?::date, ?::date, ?::timestamptz, ?, ?)
                """, number, requestId, status, taxRateId,
                issued ? "2026-10-10" : null, issued ? "2026-11-09" : null, issued ? "2026-10-10T10:00:00Z" : null,
                issued ? userId : null, userId);
    }

    private void insertReceipt(String number, String transactionReference) {
        jdbc.update("""
                insert into payment_receipts (receipt_number, fleet_request_id, receipt_type, payment_method, transaction_reference,
                    amount, received_date, recorded_by_id)
                values (?, ?, 'DEPOSIT', 'NEFT', ?, 1000000.00, DATE '2026-10-01', ?)
                """, number, requestId, transactionReference, userId);
    }

    private long id(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        assertThat(value).isNotNull();
        return value;
    }
}
