package com.renault.fleet.analytics.service;

import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.DealerValue;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.Demand;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.Kpi;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.ModelSplit;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.MonthValue;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.StageCount;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.StageDuration;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.StockVsProduction;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.Compliance;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.Group;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.MonthRow;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.Split;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.StageRow;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.Totals;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.Window;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executive dashboard. Periods are whole IST days, {@code from} and {@code to} inclusive; the previous period is the
 * same number of days immediately before {@code from}. Defaults to the last 30 days.
 */
@Service
public class ExecutiveAnalyticsService {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 3 * 366;
    static final int TOP_DEALERS = 10;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ExecutiveAnalyticsRepository repository;
    private final Clock clock;

    public ExecutiveAnalyticsService(ExecutiveAnalyticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExecutiveAnalyticsDto executive(LocalDate from, LocalDate to, Long regionId, Long plantId) {
        Instant now = Instant.now(clock);
        LocalDate end = to != null ? to : LocalDate.ofInstant(now, IST);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must be on or before 'to'.");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_DAYS) {
            throw new BadRequestException("The period may cover at most " + MAX_DAYS + " days.");
        }
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(days - 1);

        Window current = window(start, end, now, regionId, plantId);
        Window previous = window(prevStart, prevEnd, now, regionId, plantId);
        YearMonth lastMonth = YearMonth.from(end);
        Window twelveMonths = window(lastMonth.minusMonths(11).atDay(1), end, now, regionId, plantId);

        return new ExecutiveAnalyticsDto(
                new ExecutiveAnalyticsDto.Period(start, end, prevStart, prevEnd, regionId, plantId),
                kpis(current, previous),
                monthly(repository.monthly(twelveMonths), lastMonth),
                pipeline(repository.pipeline(current)),
                durations(repository.stageDurations(current)),
                repository.topDealers(current, TOP_DEALERS).stream()
                        .map(g -> new DealerValue(g.id(), g.name(), g.extra(), g.orderValue(), g.requests())).toList(),
                demand(repository.demandByRegion(current)),
                demand(repository.demandByModel(current)),
                stock(repository.stockVsProduction(current)));
    }

    private ExecutiveAnalyticsDto.Kpis kpis(Window current, Window previous) {
        Totals now = repository.totals(current);
        Totals before = repository.totals(previous);
        return new ExecutiveAnalyticsDto.Kpis(
                kpi(now.orderValue(), before.orderValue()),
                kpi(BigDecimal.valueOf(now.requests()), BigDecimal.valueOf(before.requests())),
                kpi(repository.avgHoursToClearance(current), repository.avgHoursToClearance(previous)),
                kpi(percent(repository.slaCompliance(current)), percent(repository.slaCompliance(previous))),
                kpi(BigDecimal.valueOf(repository.overdueRequests(current)),
                        BigDecimal.valueOf(repository.overdueRequests(previous))));
    }

    static Kpi kpi(BigDecimal value, BigDecimal previous) {
        BigDecimal change = value == null || previous == null || previous.signum() == 0 ? null
                : value.subtract(previous).multiply(HUNDRED).divide(previous.abs(), 1, RoundingMode.HALF_UP);
        return new Kpi(value, previous, change);
    }

    static BigDecimal percent(Compliance c) {
        return c.decided() == 0 ? null
                : BigDecimal.valueOf(c.withinSla()).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(c.decided()), 1, RoundingMode.HALF_UP);
    }

    /** Twelve months ending with {@code last}; months without requests are zero. */
    static List<MonthValue> monthly(List<MonthRow> rows, YearMonth last) {
        Map<String, MonthRow> byMonth = rows.stream().collect(Collectors.toMap(MonthRow::month, Function.identity()));
        List<MonthValue> out = new ArrayList<>(12);
        for (YearMonth m = last.minusMonths(11); !m.isAfter(last); m = m.plusMonths(1)) {
            MonthRow row = byMonth.get(m.toString());
            out.add(row == null ? new MonthValue(m.toString(), BigDecimal.ZERO, 0)
                    : new MonthValue(m.toString(), row.orderValue(), row.requests()));
        }
        return out;
    }

    /** Every status in workflow order, including zero counts, so the funnel keeps its shape. */
    static List<StageCount> pipeline(List<StageRow> rows) {
        Map<FleetRequestStatus, Long> counts = new EnumMap<>(FleetRequestStatus.class);
        rows.forEach(r -> counts.put(FleetRequestStatus.valueOf(r.status()), r.samples()));
        return Arrays.stream(FleetRequestStatus.values())
                .map(s -> new StageCount(s, counts.getOrDefault(s, 0L))).toList();
    }

    /** Stages in workflow order; the one with the highest average is flagged {@code slowest}. */
    static List<StageDuration> durations(List<StageRow> rows) {
        BigDecimal max = rows.stream().map(StageRow::avgHours).max(Comparator.naturalOrder()).orElse(null);
        return rows.stream()
                .map(r -> new StageDuration(FleetRequestStatus.valueOf(r.status()), r.avgHours(), r.samples(),
                        max != null && r.avgHours().compareTo(max) == 0))
                .sorted(Comparator.comparing(StageDuration::status))
                .toList();
    }

    private static List<Demand> demand(List<Group> rows) {
        return rows.stream().map(g -> new Demand(g.id(), g.name(), g.units(), g.orderValue(), g.requests())).toList();
    }

    private static StockVsProduction stock(List<Split> rows) {
        return new StockVsProduction(rows.stream().mapToLong(Split::fromStock).sum(),
                rows.stream().mapToLong(Split::toProduce).sum(),
                rows.stream().map(s -> new ModelSplit(s.id(), s.name(), s.fromStock(), s.toProduce())).toList());
    }

    private static Window window(LocalDate from, LocalDate to, Instant now, Long regionId, Long plantId) {
        return new Window(from.atStartOfDay(IST).toInstant(), to.plusDays(1).atStartOfDay(IST).toInstant(), now,
                regionId, plantId);
    }
}
