package com.renault.fleet.analytics.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Executive dashboard figures, all computed in SQL by the backend. Order value is the pre-GST base amount (the issued
 * quotation's base when there is one, else quantity × unit price) of non-cancelled requests. Requests belong to the
 * period by their creation date (IST); see DECISIONS.md for each widget's time basis.
 */
public record ExecutiveAnalyticsDto(
        Period period,
        Kpis kpis,
        List<MonthValue> monthlyOrderValue,
        List<StageCount> pipeline,
        List<StageDuration> stageDurations,
        List<DealerValue> topDealers,
        List<Demand> demandByRegion,
        List<Demand> demandByModel,
        StockVsProduction stockVsProduction) {

    public record Period(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, Long regionId,
            Long plantId) {
    }

    public record Kpis(Kpi totalOrderValue, Kpi requestCount, Kpi avgHoursToClearance, Kpi slaCompliancePct,
            Kpi overdueRequests) {
    }

    /** {@code changePct} is null when the previous value is missing or zero. */
    public record Kpi(BigDecimal value, BigDecimal previous, BigDecimal changePct) {
    }

    public record MonthValue(String month, BigDecimal orderValue, long requests) {
    }

    public record StageCount(FleetRequestStatus status, long count) {
    }

    public record StageDuration(FleetRequestStatus status, BigDecimal avgHours, long samples, boolean slowest) {
    }

    public record DealerValue(Long dealerId, String dealerName, String regionName, BigDecimal orderValue,
            long requests) {
    }

    public record Demand(Long id, String name, long units, BigDecimal orderValue, long requests) {
    }

    public record StockVsProduction(long fromStock, long toProduce, List<ModelSplit> byModel) {
    }

    public record ModelSplit(Long modelId, String modelName, long fromStock, long toProduce) {
    }
}
