package com.renault.fleet.quotation.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IndianFormatTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0|₹0.00",
            "5|₹5.00",
            "999.5|₹999.50",
            "1000|₹1,000.00",
            "99999.99|₹99,999.99",
            "100000|₹1,00,000.00",
            "1200000|₹12,00,000.00",
            "1000000.00|₹10,00,000.00",
            "60000000.00|₹6,00,00,000.00",
            "70800000.00|₹7,08,00,000.00",
            "9999999999999.99|₹99,99,99,99,99,999.99",
            "0.005|₹0.01",
            "-1234567.8|₹-12,34,567.80"})
    void rupeesUseLakhCroreGrouping(String amount, String expected) {
        assertThat(IndianFormat.rupees(new BigDecimal(amount))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0|Indian Rupees Zero Only",
            "1|Indian Rupees One Only",
            "0.50|Indian Rupees Zero and Fifty Paise Only",
            "19|Indian Rupees Nineteen Only",
            "20|Indian Rupees Twenty Only",
            "101|Indian Rupees One Hundred One Only",
            "1200000|Indian Rupees Twelve Lakh Only",
            "1000000|Indian Rupees Ten Lakh Only",
            "4000000.00|Indian Rupees Forty Lakh Only",
            "70800000.00|Indian Rupees Seven Crore Eight Lakh Only",
            "123456789.05|Indian Rupees Twelve Crore Thirty Four Lakh Fifty Six Thousand Seven Hundred Eighty Nine and Five Paise Only",
            "9999999999999.99|Indian Rupees Nine Lakh Ninety Nine Thousand Nine Hundred Ninety Nine Crore Ninety Nine Lakh Ninety Nine Thousand Nine Hundred Ninety Nine and Ninety Nine Paise Only"})
    void amountsInIndianWords(String amount, String expected) {
        assertThat(IndianFormat.inWords(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    void negativeAmountsHaveNoWords() {
        assertThatThrownBy(() -> IndianFormat.inWords(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void percentAndDate() {
        assertThat(IndianFormat.percent(new BigDecimal("0.1800"))).isEqualTo("18%");
        assertThat(IndianFormat.percent(new BigDecimal("0.0525"))).isEqualTo("5.25%");
        assertThat(IndianFormat.percent(new BigDecimal("0.0000"))).isEqualTo("0%");
        assertThat(IndianFormat.date(LocalDate.of(2026, 10, 11))).isEqualTo("11 Oct 2026");
    }
}
