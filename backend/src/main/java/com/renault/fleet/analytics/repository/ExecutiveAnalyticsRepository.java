package com.renault.fleet.analytics.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregate SQL for the executive dashboard. Every figure is computed by PostgreSQL; the service only fills
 * gaps (empty months, stages without rows) and derives period-over-period changes.
 */
@Repository
public class ExecutiveAnalyticsRepository {

    /** Requests matching the region/plant filter, with their order value (pre-GST base amount). */
    private static final String REQ = """
            with req as (
                select r.id, r.created_at, r.status, r.quantity, r.allocated_qty, r.production_required,
                       d.id as dealer_id, d.name as dealer_name, rg.id as region_id, rg.name as region_name,
                       m.id as model_id, m.name as model_name,
                       coalesce(q.base_amount, r.quantity * v.unit_price) as order_value
                from fleet_requests r
                join dealers d on d.id = r.dealer_id
                join regions rg on rg.id = d.region_id
                join vehicle_variants v on v.id = r.variant_id
                join vehicle_models m on m.id = v.model_id
                left join quotations q on q.fleet_request_id = r.id and q.status = 'ISSUED'
                where (cast(:regionId as bigint) is null or d.region_id = :regionId)
                  and (cast(:plantId as bigint) is null or r.plant_id = :plantId)
            )
            """;

    /** Stage intervals from history: each status change opens a stage, the next change closes it. */
    private static final String STAGES = """
            , stages as (
                select h.fleet_request_id, h.to_status as stage, h.created_at as entered_at,
                       lead(h.created_at) over (partition by h.fleet_request_id order by h.created_at, h.id) as left_at
                from workflow_history h
                join req on req.id = h.fleet_request_id
                where h.to_status is not null and (h.from_status is null or h.from_status <> h.to_status)
            )
            """;

    private static final String IN_PERIOD = " created_at >= :from and created_at < :to ";
    private static final String OPEN_VALUE = "coalesce(sum(order_value) filter (where status <> 'CANCELLED'), 0)";

    private final NamedParameterJdbcTemplate jdbc;

    public ExecutiveAnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Filter + time window; {@code to} is exclusive. */
    public record Window(Instant from, Instant to, Instant now, Long regionId, Long plantId) {

        MapSqlParameterSource params() {
            return new MapSqlParameterSource()
                    .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                    .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                    .addValue("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                    .addValue("regionId", regionId)
                    .addValue("plantId", plantId);
        }
    }

    public record Totals(BigDecimal orderValue, long requests) {
    }

    public record Compliance(long decided, long withinSla) {
    }

    public record Group(Long id, String name, String extra, long units, BigDecimal orderValue, long requests) {
    }

    public record StageRow(String status, BigDecimal avgHours, long samples) {
    }

    public record MonthRow(String month, BigDecimal orderValue, long requests) {
    }

    public record Split(Long id, String name, long fromStock, long toProduce) {
    }

    public Totals totals(Window w) {
        return jdbc.queryForObject(REQ + "select " + OPEN_VALUE + " as v, count(*) as n from req where" + IN_PERIOD,
                w.params(), (rs, i) -> new Totals(rs.getBigDecimal("v"), rs.getLong("n")));
    }

    /** Mean hours from creation to first reaching FINANCIALLY_CLEARED, for requests cleared within the window. */
    public BigDecimal avgHoursToClearance(Window w) {
        return jdbc.queryForObject(REQ + """
                select round(avg(extract(epoch from (c.cleared_at - req.created_at)) / 3600)::numeric, 1)
                from req
                join (select fleet_request_id, min(created_at) as cleared_at from workflow_history
                      where to_status = 'FINANCIALLY_CLEARED' and from_status is distinct from to_status
                      group by fleet_request_id) c on c.fleet_request_id = req.id
                where c.cleared_at >= :from and c.cleared_at < :to
                """, w.params(), BigDecimal.class);
    }

    /**
     * Timed stages entered within the window whose outcome is decided: finished, or still open and already past the
     * limit. Within SLA = left (or now) no later than entry + the stage's current hours allowed.
     */
    public Compliance slaCompliance(Window w) {
        return jdbc.queryForObject(REQ + STAGES + """
                select count(*) as decided,
                       count(*) filter (where coalesce(s.left_at, :now) <= s.entered_at + make_interval(hours => p.hours_allowed)) as ok
                from stages s
                join sla_policies p on p.stage = s.stage and p.active
                where s.entered_at >= :from and s.entered_at < :to
                  and (s.left_at is not null or :now > s.entered_at + make_interval(hours => p.hours_allowed))
                """, w.params(), (rs, i) -> new Compliance(rs.getLong("decided"), rs.getLong("ok")));
    }

    /** Distinct requests with an SLA_BREACHED event recorded within the window. */
    public long overdueRequests(Window w) {
        Long n = jdbc.queryForObject(REQ + """
                select count(distinct h.fleet_request_id) from workflow_history h join req on req.id = h.fleet_request_id
                where h.action = 'SLA_BREACHED' and h.created_at >= :from and h.created_at < :to
                """, w.params(), Long.class);
        return n == null ? 0 : n;
    }

    public List<MonthRow> monthly(Window w) {
        return jdbc.query(REQ + """
                select to_char(date_trunc('month', created_at at time zone 'Asia/Kolkata'), 'YYYY-MM') as month,
                """ + OPEN_VALUE + " as v, count(*) as n from req where" + IN_PERIOD + "group by 1 order by 1",
                w.params(), (rs, i) -> new MonthRow(rs.getString("month"), rs.getBigDecimal("v"), rs.getLong("n")));
    }

    public List<StageRow> pipeline(Window w) {
        return jdbc.query(REQ + "select status, count(*) as n from req where" + IN_PERIOD + "group by status",
                w.params(), (rs, i) -> new StageRow(rs.getString("status"), null, rs.getLong("n")));
    }

    /** Average hours spent in each non-terminal stage, over finished stage visits entered within the window. */
    public List<StageRow> stageDurations(Window w) {
        return jdbc.query(REQ + STAGES + """
                select stage, round(avg(extract(epoch from (left_at - entered_at)) / 3600)::numeric, 1) as h, count(*) as n
                from stages
                where left_at is not null and entered_at >= :from and entered_at < :to
                  and stage not in ('COMPLETED', 'CANCELLED')
                group by stage
                """, w.params(), (rs, i) -> new StageRow(rs.getString("stage"), rs.getBigDecimal("h"), rs.getLong("n")));
    }

    public List<Group> topDealers(Window w, int limit) {
        return jdbc.query(REQ + """
                select dealer_id as id, dealer_name as name, region_name as extra, sum(quantity) as units,
                       sum(order_value) as v, count(*) as n
                from req where status <> 'CANCELLED' and""" + IN_PERIOD + """
                group by dealer_id, dealer_name, region_name
                order by v desc, name
                limit :limit
                """, w.params().addValue("limit", limit), (rs, i) -> group(rs));
    }

    public List<Group> demandByRegion(Window w) {
        return demand(w, "region_id", "region_name");
    }

    public List<Group> demandByModel(Window w) {
        return demand(w, "model_id", "model_name");
    }

    public List<Split> stockVsProduction(Window w) {
        return jdbc.query(REQ + """
                select model_id as id, model_name as name, sum(allocated_qty) as s, sum(production_required) as p
                from req where status <> 'CANCELLED' and""" + IN_PERIOD + """
                group by model_id, model_name order by name
                """, w.params(), (rs, i) -> new Split(rs.getLong("id"), rs.getString("name"), rs.getLong("s"), rs.getLong("p")));
    }

    private List<Group> demand(Window w, String idColumn, String nameColumn) {
        return jdbc.query(REQ + "select " + idColumn + " as id, " + nameColumn + " as name, null as extra, "
                + "sum(quantity) as units, sum(order_value) as v, count(*) as n from req where status <> 'CANCELLED' and"
                + IN_PERIOD + "group by " + idColumn + ", " + nameColumn + " order by units desc, name",
                w.params(), (rs, i) -> group(rs));
    }

    private static Group group(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Group(rs.getLong("id"), rs.getString("name"), rs.getString("extra"), rs.getLong("units"),
                rs.getBigDecimal("v"), rs.getLong("n"));
    }
}
