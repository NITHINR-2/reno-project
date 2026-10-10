package com.renault.fleet.quotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renault.fleet.quotation.service.QuotationCalculator;
import com.renault.fleet.quotation.service.QuotationCalculator.Breakdown;
import com.renault.fleet.quotation.service.QuotationCalculator.LineInput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuotationCalculatorTest {

    private static Breakdown calc(int qty, String price, String rate, String required, String deposits) {
        return QuotationCalculator.calculate(List.of(new LineInput(qty, new BigDecimal(price))), new BigDecimal(rate),
                new BigDecimal(required), new BigDecimal(deposits));
    }

    @Test
    void flt0891Figures() {
        Breakdown b = calc(50, "1200000.00", "0.18", "5000000.00", "1000000.00");
        assertThat(b.baseAmount()).isEqualByComparingTo("60000000.00");
        assertThat(b.gstAmount()).isEqualByComparingTo("10800000.00");
        assertThat(b.totalAmount()).isEqualByComparingTo("70800000.00");
        assertThat(b.depositShortfall()).isEqualByComparingTo("4000000.00");
        assertThat(b.totalAmount().scale()).isEqualTo(2);
        assertThat(b.depositShortfall().scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0} x {1} @ {2} -> base {3}, gst {4}, total {5}")
    @CsvSource({
            "1, 100.00, 0.18, 100.00, 18.00, 118.00",
            "1, 0.03, 0.18, 0.03, 0.01, 0.04",         // 0.0054 rounds up to 0.01
            "1, 0.025, 0.18, 0.03, 0.01, 0.04",        // unit price 0.025 rounds HALF_UP to 0.03 first
            "1, 10.25, 0.12, 10.25, 1.23, 11.48",      // 1.2300 exact
            "1, 0.25, 0.18, 0.25, 0.05, 0.30",         // 0.045 -> 0.05 (HALF_UP, not banker's 0.04)
            "3, 33.33, 0.18, 99.99, 18.00, 117.99",    // 17.9982 -> 18.00
            "7, 1.00, 0.0, 7.00, 0.00, 7.00",          // zero-rated
            "1, 0.00, 0.18, 0.00, 0.00, 0.00",         // free line
            "1000, 999999999.99, 0.28, 999999999990.00, 279999999997.20, 1279999999987.20"}) // near NUMERIC(15,2) max
    void gstIsRoundedHalfUpPerLineAndTotalIsBasePlusGst(int qty, String price, String rate, String base, String gst,
            String total) {
        Breakdown b = calc(qty, price, rate, "0", "0");
        assertThat(b.baseAmount()).isEqualByComparingTo(base);
        assertThat(b.gstAmount()).isEqualByComparingTo(gst);
        assertThat(b.totalAmount()).isEqualByComparingTo(total);
        assertThat(b.totalAmount()).isEqualByComparingTo(b.baseAmount().add(b.gstAmount()));
    }

    @Test
    void linesAreRoundedIndividuallyThenSummed() {
        // Each line: 0.03 * 0.18 = 0.0054 -> 0.01. Summing first would give 0.0108 -> 0.01, not 0.02.
        Breakdown b = QuotationCalculator.calculate(List.of(new LineInput(1, new BigDecimal("0.03")),
                new LineInput(1, new BigDecimal("0.03"))), new BigDecimal("0.18"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(b.gstAmount()).isEqualByComparingTo("0.02");
        assertThat(b.lines()).hasSize(2).allSatisfy(l -> assertThat(l.gstAmount()).isEqualByComparingTo("0.01"));
    }

    @ParameterizedTest(name = "required {0}, received {1} -> shortfall {2}")
    @CsvSource({
            "5000000.00, 1000000.00, 4000000.00",
            "5000000.00, 5000000.00, 0.00",
            "5000000.00, 6000000.00, 0.00",   // overpayment never gives a negative shortfall
            "0.00, 0.00, 0.00",
            "0.00, 100.00, 0.00",
            "100.00, 99.99, 0.01"})
    void shortfallIsNeverNegative(String required, String received, String shortfall) {
        Breakdown b = calc(1, "100.00", "0.18", required, received);
        assertThat(b.depositShortfall()).isEqualByComparingTo(shortfall);
        assertThat(b.depositShortfall().signum()).isNotNegative();
    }

    @Test
    void invalidInputsAreRejected() {
        assertThatThrownBy(() -> calc(0, "1.00", "0.18", "0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc(1, "-1.00", "0.18", "0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc(1, "1.00", "1.00", "0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc(1, "1.00", "-0.01", "0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc(1, "1.00", "0.18", "-1", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuotationCalculator.calculate(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalsThatOverflowNumeric15x2AreRejected() {
        assertThatThrownBy(() -> calc(10000, "9999999999.99", "0.28", "0", "0"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NUMERIC(15,2)");
    }
}
