package com.renault.fleet.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.MonthValue;
import com.renault.fleet.analytics.dto.ExecutiveAnalyticsDto.StageDuration;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.Compliance;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.MonthRow;
import com.renault.fleet.analytics.repository.ExecutiveAnalyticsRepository.StageRow;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutiveAnalyticsServiceTest {

    @Test
    void changeIsAPercentageOfThePreviousValueAndNullWithoutABaseline() {
        assertThat(ExecutiveAnalyticsService.kpi(new BigDecimal("150"), new BigDecimal("100")).changePct())
                .isEqualByComparingTo("50.0");
        assertThat(ExecutiveAnalyticsService.kpi(new BigDecimal("2"), new BigDecimal("3")).changePct())
                .isEqualByComparingTo("-33.3");
        assertThat(ExecutiveAnalyticsService.kpi(BigDecimal.ONE, BigDecimal.ZERO).changePct()).isNull();
        assertThat(ExecutiveAnalyticsService.kpi(null, BigDecimal.TEN).changePct()).isNull();
        assertThat(ExecutiveAnalyticsService.kpi(BigDecimal.TEN, null).changePct()).isNull();
    }

    @Test
    void complianceIsNullWhenNothingIsDecided() {
        assertThat(ExecutiveAnalyticsService.percent(new Compliance(0, 0))).isNull();
        assertThat(ExecutiveAnalyticsService.percent(new Compliance(3, 2))).isEqualByComparingTo("66.7");
        assertThat(ExecutiveAnalyticsService.percent(new Compliance(8, 8))).isEqualByComparingTo("100.0");
    }

    @Test
    void monthlySeriesAlwaysHasTwelveMonthsAcrossTheYearBoundary() {
        List<MonthValue> months = ExecutiveAnalyticsService.monthly(
                List.of(new MonthRow("2026-01", new BigDecimal("5.00"), 2)), YearMonth.of(2026, 3));
        assertThat(months).extracting(MonthValue::month).first().isEqualTo("2025-04");
        assertThat(months).hasSize(12).extracting(MonthValue::month).last().isEqualTo("2026-03");
        assertThat(months.get(9).orderValue()).isEqualByComparingTo("5.00");
        assertThat(months.get(9).requests()).isEqualTo(2);
        assertThat(months.get(10).orderValue()).isZero();
    }

    @Test
    void pipelineListsEveryStatusInWorkflowOrder() {
        var pipeline = ExecutiveAnalyticsService.pipeline(List.of(new StageRow("CANCELLED", null, 2)));
        assertThat(pipeline).hasSize(FleetRequestStatus.values().length);
        assertThat(pipeline.getFirst().status()).isEqualTo(FleetRequestStatus.PENDING_MARKETING_REVIEW);
        assertThat(pipeline.getLast().count()).isEqualTo(2);
    }

    @Test
    void slowestStageIsFlaggedAndStagesKeepWorkflowOrder() {
        List<StageDuration> rows = ExecutiveAnalyticsService.durations(List.of(
                new StageRow("PAYMENT_PENDING", new BigDecimal("100.5"), 3),
                new StageRow("PENDING_MARKETING_REVIEW", new BigDecimal("20.0"), 5)));
        assertThat(rows).extracting(StageDuration::status)
                .containsExactly(FleetRequestStatus.PENDING_MARKETING_REVIEW, FleetRequestStatus.PAYMENT_PENDING);
        assertThat(rows).extracting(StageDuration::slowest).containsExactly(false, true);
        assertThat(ExecutiveAnalyticsService.durations(List.of())).isEmpty();
    }
}
