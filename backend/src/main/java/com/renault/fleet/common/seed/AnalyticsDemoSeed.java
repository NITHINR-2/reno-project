package com.renault.fleet.common.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates backdated demo requests for the executive dashboard: each request walks the real workflow path with
 * realistic dwell times (some past their SLA), so history, stage clocks, breach events, allocations, quotations,
 * receipts, production orders and dispatches are all consistent. Deterministic for a given seed and clock.
 * Plain SQL, because services stamp "now" and the point here is history spread over the last 12 months.
 */
@Component
public class AnalyticsDemoSeed {

    public static final String MARKER = "Analytics demo seed";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public record Actors(long marketingManager, long marketingHead, long finance, long supervisor) {
    }

    /** {@code taxRateId} null = no confirmed GST rate for the category, so the request can't pass finance. */
    public record Variant(long id, String description, BigDecimal unitPrice, Long taxRateId, BigDecimal gstRate,
            long stockId) {
    }

    public record Catalog(List<Long> dealerIds, List<Variant> variants, List<Long> plantIds, long locationId,
            Actors actors) {
    }

    private record Step(String action, String to, String role) {
    }

    private record Visit(String stage, Instant entered, Instant left) {
    }

    private record Policy(int hours, int warningPct) {
    }

    /** The main path; QUOTATION_ISSUED → PAYMENT_PENDING is the system's automatic step (no actor). */
    private static final Map<String, Step> NEXT = Map.ofEntries(
            Map.entry("PENDING_MARKETING_REVIEW", new Step("QUALIFY", "MARKETING_QUALIFIED", "MARKETING_MANAGER")),
            Map.entry("MARKETING_QUALIFIED", new Step("FORWARD_TO_EXECUTIVE", "PENDING_EXECUTIVE_APPROVAL", "MARKETING_MANAGER")),
            Map.entry("PENDING_EXECUTIVE_APPROVAL", new Step("APPROVE", "EXECUTIVE_APPROVED", "MARKETING_HEAD")),
            Map.entry("EXECUTIVE_APPROVED", new Step("FORWARD_TO_FINANCE", "PENDING_FINANCE_CLEARANCE", "MARKETING_HEAD")),
            Map.entry("PENDING_FINANCE_CLEARANCE", new Step("ISSUE_QUOTATION", "QUOTATION_ISSUED", "FINANCE_TEAM")),
            Map.entry("QUOTATION_ISSUED", new Step("AWAIT_PAYMENT", "PAYMENT_PENDING", null)),
            Map.entry("PAYMENT_PENDING", new Step("VERIFY_FUNDS", "FUNDS_VERIFIED", "FINANCE_TEAM")),
            Map.entry("FUNDS_VERIFIED", new Step("GRANT_CLEARANCE", "FINANCIALLY_CLEARED", "FINANCE_TEAM")),
            Map.entry("FINANCIALLY_CLEARED", new Step("SCHEDULE_PRODUCTION", "PRODUCTION_SCHEDULED", "SUPERVISOR")),
            Map.entry("PRODUCTION_SCHEDULED", new Step("SCHEDULE_DISPATCH", "DISPATCH_SCHEDULED", "SUPERVISOR")),
            Map.entry("DISPATCH_SCHEDULED", new Step("COMPLETE", "COMPLETED", "SUPERVISOR")));

    /** Typical hours in stages without an SLA policy. */
    private static final Map<String, Integer> UNTIMED_HOURS = Map.of(
            "EXECUTIVE_APPROVED", 8, "RETURNED_TO_MARKETING", 30, "PRODUCTION_SCHEDULED", 24 * 7 * 4,
            "DISPATCH_SCHEDULED", 24 * 4);

    /** Open stages a recent request may still be sitting in (in path order; finance onwards needs a GST rate). */
    private static final List<String> OPEN_STOPS = List.of("PENDING_MARKETING_REVIEW", "MARKETING_QUALIFIED",
            "PENDING_EXECUTIVE_APPROVAL", "PENDING_FINANCE_CLEARANCE", "PAYMENT_PENDING", "FUNDS_VERIFIED",
            "FINANCIALLY_CLEARED", "PRODUCTION_SCHEDULED", "DISPATCH_SCHEDULED");

    private final JdbcTemplate jdbc;

    public AnalyticsDemoSeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean alreadySeeded() {
        Integer n = jdbc.queryForObject("select count(*) from fleet_requests where notes = ?", Integer.class, MARKER);
        return n != null && n > 0;
    }

    /** Creates {@code count} requests spread over the 12 months before {@code now}; returns their ids. */
    @Transactional
    public List<Long> seed(Catalog catalog, int count, Instant now, long randomSeed) {
        Random rnd = new Random(randomSeed);
        Map<String, Policy> policies = new HashMap<>();
        jdbc.query("select stage, hours_allowed, warning_threshold_pct from sla_policies where active",
                rs -> { policies.put(rs.getString(1), new Policy(rs.getInt(2), rs.getInt(3))); });
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Instant created = now.minus(Duration.ofDays(i * 360L / count + rnd.nextInt(8)))
                    .minus(Duration.ofMinutes(120 + rnd.nextInt(600)));
            ids.add(request(catalog, i, count, created, now, rnd, policies));
        }
        return ids;
    }

    private long request(Catalog c, int i, int count, Instant created, Instant now, Random rnd, Map<String, Policy> policies) {
        // Only the first variant (the Duster in dev) has a GST rate, so others can't pass finance; older requests are
        // mostly Dusters so the history isn't dominated by withdrawals, newer ones mix models in the early stages.
        double firstShare = i < count / 3 ? 0.45 : 0.85;
        Variant variant = c.variants().get(rnd.nextDouble() < firstShare ? 0 : 1 + rnd.nextInt(c.variants().size() - 1));
        long dealerId = c.dealerIds().get(rnd.nextInt(c.dealerIds().size()));
        long plantId = c.plantIds().get(rnd.nextInt(c.plantIds().size()));
        int qty = 5 + rnd.nextInt(56);
        int allocatable = rnd.nextDouble() < 0.25 ? 0 : rnd.nextDouble() < 0.3 ? qty : rnd.nextInt(qty + 1);
        BigDecimal deposit = variant.unitPrice().multiply(BigDecimal.valueOf(qty)).multiply(new BigDecimal("0.05"))
                .setScale(-3, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
        int weeks = 3 + rnd.nextInt(5);
        String number = "FLT-%d-%04d".formatted(LocalDate.ofInstant(created, IST).getYear(), 100 + i);
        boolean cancel = rnd.nextDouble() < 0.08;
        int cancelAfter = rnd.nextInt(6);
        boolean returnOnce = rnd.nextDouble() < 0.15;
        // A full run takes ~7 weeks, so without this nearly everything would be finished: about half of the newest
        // third stays in an open stage (some long enough to be overdue).
        String stopAt = i < count / 3 && rnd.nextDouble() < 0.6
                ? OPEN_STOPS.get(rnd.nextInt(variant.taxRateId() == null ? 4 : OPEN_STOPS.size())) : null;
        if (i < 2) {
            stopAt = "PENDING_MARKETING_REVIEW"; // the newest requests are always waiting for review
        }

        long id = jdbc.queryForObject("""
                insert into fleet_requests (request_number, dealer_id, variant_id, plant_id, quantity, allocated_qty,
                    production_required, required_deposit, production_estimate_weeks, requested_delivery_date, status,
                    notes, created_by_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, 'PENDING_MARKETING_REVIEW', ?, ?, ?, ?) returning id
                """, Long.class, number, dealerId, variant.id(), plantId, qty, qty, deposit, weeks,
                LocalDate.ofInstant(created, IST).plusDays(90), MARKER, c.actors().marketingManager(), ts(created),
                ts(created));
        history(id, null, "PENDING_MARKETING_REVIEW", "CREATED", c.actors().marketingManager(), "MARKETING_MANAGER",
                null, created);

        List<Visit> visits = new ArrayList<>();
        String stage = "PENDING_MARKETING_REVIEW";
        Instant t = created;
        int allocated = 0;
        Long quotationId = null;
        boolean returned = false;
        Instant completedAt = null;
        while (!stage.equals("COMPLETED") && !stage.equals("CANCELLED") && !stage.equals(stopAt)) {
            Instant next = t.plus(dwell(stage, policies, rnd));
            if (next.isAfter(now)) {
                break; // still in this stage today
            }
            Step step = NEXT.get(stage);
            String reason = null;
            if (cancel && visits.size() >= cancelAfter && !stage.equals("QUOTATION_ISSUED")) {
                step = new Step("CANCEL", "CANCELLED", "SUPERVISOR");
                reason = "Dealer withdrew the fleet order.";
            } else if (stage.equals("PENDING_EXECUTIVE_APPROVAL") && returnOnce && !returned) {
                step = new Step("REJECT", "RETURNED_TO_MARKETING", "MARKETING_HEAD");
                reason = "Volume discount needs a revised stock plan.";
                returned = true;
            } else if (stage.equals("RETURNED_TO_MARKETING")) {
                step = new Step("RESUBMIT", "PENDING_MARKETING_REVIEW", "MARKETING_MANAGER");
            } else if (stage.equals("PENDING_FINANCE_CLEARANCE") && variant.taxRateId() == null) {
                step = new Step("CANCEL", "CANCELLED", "SUPERVISOR");
                reason = "No confirmed GST rate for this vehicle category; order withdrawn.";
            } else if (stage.equals("FINANCIALLY_CLEARED") && allocated == qty) {
                step = new Step("SCHEDULE_DISPATCH", "DISPATCH_SCHEDULED", "SUPERVISOR"); // nothing to produce
            }
            long actor = actor(c.actors(), step.role());

            switch (step.action()) {
                case "QUALIFY" -> {
                    if (allocated == 0 && allocatable > 0) {
                        allocated = allocatable;
                        allocate(c, id, variant, allocated, next);
                    }
                }
                case "ISSUE_QUOTATION" -> quotationId = quotation(c, id, variant, qty, deposit, next);
                case "VERIFY_FUNDS" -> receipt(c, id, quotationId, number, deposit, next);
                case "SCHEDULE_PRODUCTION" -> production(c, id, variant, plantId, qty - allocated, weeks, next);
                case "SCHEDULE_DISPATCH" -> dispatch(c, id, qty, next);
                case "REJECT" -> jdbc.update("update fleet_requests set return_reason = ? where id = ?", reason, id);
                case "CANCEL" -> {
                    jdbc.update("update fleet_requests set cancel_reason = ? where id = ?", reason, id);
                    jdbc.update("update production_orders set status = 'CANCELLED' where fleet_request_id = ?", id);
                    jdbc.update("update dispatches set status = 'CANCELLED' where fleet_request_id = ?", id);
                }
                case "COMPLETE" -> completedAt = next;
                default -> { }
            }
            history(id, stage, step.to(), step.action(), step.role() == null ? null : actor, step.role(), reason, next);
            visits.add(new Visit(stage, t, next));
            stage = step.to();
            t = next;
        }

        if (stage.equals("CANCELLED") && allocated > 0) {
            jdbc.update("update stock_allocations set status = 'RELEASED', released_at = ? where fleet_request_id = ?",
                    ts(t), id);
            jdbc.update("update inventory_stock set quantity_available = quantity_available + ?, "
                    + "quantity_allocated = quantity_allocated - ? where id = ?", allocated, allocated, variant.stockId());
            allocated = 0;
        }
        if (completedAt != null) {
            finish(c, id, variant, allocated, completedAt);
        }
        breaches(id, visits, stage, t, now, policies);
        Policy open = policies.get(stage);
        Instant due = open == null ? null : t.plus(Duration.ofHours(open.hours()));
        Instant warning = open == null ? null
                : t.plus(Duration.ofMinutes(open.hours() * 60L * open.warningPct() / 100));
        jdbc.update("""
                update fleet_requests set status = ?, allocated_qty = ?, production_required = ?, updated_at = ?,
                    stage_entered_at = ?, sla_warning_at = ?, sla_due_at = ?, sla_breached_at = ?
                where id = ?
                """, stage, allocated, qty - allocated, ts(t), ts(t), ts(warning), ts(due),
                due != null && !now.isBefore(due) ? ts(due) : null, id);
        return id;
    }

    private Duration dwell(String stage, Map<String, Policy> policies, Random rnd) {
        if (stage.equals("QUOTATION_ISSUED")) {
            return Duration.ofSeconds(1); // the system moves it on immediately
        }
        Policy p = policies.get(stage);
        if (p == null) {
            return Duration.ofMinutes((long) (UNTIMED_HOURS.getOrDefault(stage, 24) * 60 * (0.2 + rnd.nextDouble() * 1.1)));
        }
        // Most timed stages finish well inside their SLA; about 1 in 14 runs over (by up to 65%).
        double factor = rnd.nextDouble() < 0.07 ? 1.05 + rnd.nextDouble() * 0.6 : 0.15 + rnd.nextDouble() * 0.75;
        return Duration.ofMinutes((long) (p.hours() * 60 * factor));
    }

    /** SLA_BREACHED for every timed stage visit that ran past its limit (and the open one, if overdue now). */
    private void breaches(long id, List<Visit> visits, String openStage, Instant openSince, Instant now,
            Map<String, Policy> policies) {
        List<Visit> all = new ArrayList<>(visits);
        all.add(new Visit(openStage, openSince, null));
        for (Visit v : all) {
            Policy p = policies.get(v.stage());
            if (p == null) {
                continue;
            }
            Instant due = v.entered().plus(Duration.ofHours(p.hours()));
            if ((v.left() != null && v.left().isAfter(due)) || (v.left() == null && !now.isBefore(due))) {
                Instant detected = due.plus(Duration.ofMinutes(3));
                jdbc.update("""
                        insert into workflow_history (fleet_request_id, from_status, to_status, action, metadata, created_at)
                        values (?, ?, ?, 'SLA_BREACHED', cast(? as jsonb), ?)
                        """, id, v.stage(), v.stage(), "{\"stageEnteredAt\":\"%s\",\"dueAt\":\"%s\",\"detectedAt\":\"%s\"}"
                                .formatted(v.entered(), due, detected), ts(detected));
            }
        }
    }

    private void allocate(Catalog c, long id, Variant v, int qty, Instant at) {
        jdbc.update("insert into stock_allocations (fleet_request_id, stock_id, quantity, allocated_by_id, created_at, "
                + "updated_at) values (?, ?, ?, ?, ?, ?)", id, v.stockId(), qty, c.actors().marketingManager(), ts(at), ts(at));
        jdbc.update("update inventory_stock set quantity_available = quantity_available - ?, "
                + "quantity_allocated = quantity_allocated + ? where id = ?", qty, qty, v.stockId());
        jdbc.update("update fleet_requests set stock_mapped_at = ?, stock_mapped_by_id = ? where id = ?", ts(at),
                c.actors().marketingManager(), id);
    }

    private long quotation(Catalog c, long requestId, Variant v, int qty, BigDecimal deposit, Instant at) {
        BigDecimal base = v.unitPrice().multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gst = base.multiply(v.gstRate()).setScale(2, RoundingMode.HALF_UP);
        LocalDate day = LocalDate.ofInstant(at, IST);
        long qid = jdbc.queryForObject("""
                insert into quotations (quotation_number, fleet_request_id, status, payment_status, tax_rate_id, gst_rate,
                    base_amount, gst_amount, total_amount, required_deposit, deposit_received, deposit_shortfall,
                    issue_date, valid_until, issued_at, issued_by_id, created_by_id, verification_token, created_at, updated_at)
                values (?, ?, 'ISSUED', 'UNPAID', ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                """, Long.class, "QT-%d-%05d".formatted(day.getYear(), nextval("quotation_number_seq")), requestId,
                v.taxRateId(), v.gstRate(), base, gst, base.add(gst), deposit, deposit, day, day.plusDays(30), ts(at),
                c.actors().finance(), c.actors().finance(), token(), ts(at), ts(at));
        jdbc.update("""
                insert into quotation_line_items (quotation_id, line_no, variant_id, description, quantity, unit_price,
                    base_amount, gst_rate, gst_amount, line_total, created_at, updated_at)
                values (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, qid, v.id(), v.description(), qty, v.unitPrice(), base, v.gstRate(), gst, base.add(gst), ts(at), ts(at));
        return qid;
    }

    private void receipt(Catalog c, long requestId, Long quotationId, String number, BigDecimal amount, Instant at) {
        LocalDate day = LocalDate.ofInstant(at, IST);
        jdbc.update("""
                insert into payment_receipts (receipt_number, fleet_request_id, quotation_id, receipt_type, payment_method,
                    transaction_reference, amount, received_date, status, recorded_by_id, reviewed_by_id, reviewed_at,
                    created_at, updated_at)
                values (?, ?, ?, 'DEPOSIT', 'NEFT', ?, ?, ?, 'APPROVED', ?, ?, ?, ?, ?)
                """, "RCPT-%d-%05d".formatted(day.getYear(), nextval("receipt_number_seq")), requestId, quotationId,
                "DEMO-" + number, amount, day.minusDays(1), c.actors().finance(), c.actors().finance(), ts(at),
                ts(at.minus(Duration.ofHours(6))), ts(at));
        jdbc.update("update quotations set payment_status = case when ? >= total_amount then 'PAID' else 'PARTIALLY_PAID' end "
                + "where id = ?", amount, quotationId);
    }

    private void production(Catalog c, long requestId, Variant v, long plantId, int qty, int weeks, Instant at) {
        LocalDate start = LocalDate.ofInstant(at, IST).plusDays(3);
        jdbc.update("""
                insert into production_orders (production_number, fleet_request_id, plant_id, variant_id, quantity,
                    scheduled_start, estimated_completion, scheduled_by_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "PRD-%d-%05d".formatted(start.getYear(), nextval("production_number_seq")), requestId, plantId,
                v.id(), qty, start, start.plusWeeks(weeks), c.actors().supervisor(), ts(at), ts(at));
    }

    private void dispatch(Catalog c, long requestId, int qty, Instant at) {
        LocalDate day = LocalDate.ofInstant(at, IST).plusDays(2);
        jdbc.update("""
                insert into dispatches (dispatch_number, fleet_request_id, from_location_id, quantity, scheduled_date,
                    scheduled_by_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, "DSP-%d-%05d".formatted(day.getYear(), nextval("dispatch_number_seq")), requestId, c.locationId(),
                qty, day, c.actors().supervisor(), ts(at), ts(at));
    }

    /** Completion: vehicles left the yard (allocations consumed), production finished and the dispatch delivered. */
    private void finish(Catalog c, long id, Variant v, int allocated, Instant at) {
        if (allocated > 0) {
            jdbc.update("update stock_allocations set status = 'CONSUMED' where fleet_request_id = ? and status = 'ACTIVE'", id);
            jdbc.update("update inventory_stock set quantity_allocated = quantity_allocated - ? where id = ?",
                    allocated, v.stockId());
        }
        jdbc.update("update production_orders set status = 'COMPLETED', completed_at = ? where fleet_request_id = ?",
                ts(at.minus(Duration.ofDays(3))), id);
        jdbc.update("update dispatches set status = 'DELIVERED', dispatched_at = ?, delivered_at = ? where fleet_request_id = ?",
                ts(at.minus(Duration.ofDays(1))), ts(at), id);
    }

    private void history(long id, String from, String to, String action, Long actorId, String role, String reason,
            Instant at) {
        jdbc.update("""
                insert into workflow_history (fleet_request_id, from_status, to_status, action, actor_id, actor_role,
                    reason, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, from, to, action, actorId, actorId == null ? null : role, reason, ts(at));
    }

    private static long actor(Actors a, String role) {
        if (role == null) {
            return 0;
        }
        return switch (role) {
            case "MARKETING_MANAGER" -> a.marketingManager();
            case "MARKETING_HEAD" -> a.marketingHead();
            case "FINANCE_TEAM" -> a.finance();
            default -> a.supervisor();
        };
    }

    private long nextval(String sequence) {
        return jdbc.queryForObject("select nextval('" + sequence + "')", Long.class);
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
