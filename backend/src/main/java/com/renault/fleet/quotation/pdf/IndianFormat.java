package com.renault.fleet.quotation.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Indian presentation of amounts: lakh/crore digit grouping (₹12,00,000.00) and amounts in words (Indian Rupees
 * Seven Crore Eight Lakh Only). Formatting only; it never changes a figure beyond showing it to 2 decimals.
 */
public final class IndianFormat {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty",
            "Ninety"};

    private IndianFormat() {
    }

    /** ₹12,00,000.00 */
    public static String rupees(BigDecimal amount) {
        return "₹" + grouped(amount);
    }

    /** 12,00,000.00 (lakh/crore grouping, always 2 decimals). */
    public static String grouped(BigDecimal amount) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        String plain = value.abs().toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        StringBuilder out = new StringBuilder();
        if (whole.length() > 3) {
            String head = whole.substring(0, whole.length() - 3);
            int first = head.length() % 2 == 0 ? 2 : 1;
            out.append(head, 0, first);
            for (int i = first; i < head.length(); i += 2) {
                out.append(',').append(head, i, i + 2);
            }
            out.append(',').append(whole.substring(whole.length() - 3));
        } else {
            out.append(whole);
        }
        return (value.signum() < 0 ? "-" : "") + out + plain.substring(dot);
    }

    /** 0.1800 → 18%, 0.0525 → 5.25% */
    public static String percent(BigDecimal rate) {
        return rate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }

    public static String date(LocalDate date) {
        return date == null ? null : DATE.format(date);
    }

    /** "Indian Rupees Seven Crore Eight Lakh Only", "… and Fifty Paise Only". Non-negative amounts only. */
    public static String inWords(BigDecimal amount) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Amount in words needs a non-negative amount.");
        }
        long rupees = value.setScale(0, RoundingMode.DOWN).longValueExact();
        int paise = value.subtract(BigDecimal.valueOf(rupees)).movePointRight(2).intValueExact();
        StringBuilder out = new StringBuilder("Indian Rupees ").append(rupees == 0 ? "Zero" : words(rupees));
        if (paise > 0) {
            out.append(" and ").append(belowHundred(paise)).append(" Paise");
        }
        return out.append(" Only").toString();
    }

    private static String words(long n) {
        StringBuilder out = new StringBuilder();
        long crore = n / 1_00_00_000;
        long rest = n % 1_00_00_000;
        if (crore > 0) {
            out.append(words(crore)).append(" Crore");
        }
        append(out, (int) (rest / 1_00_000), "Lakh");
        rest %= 1_00_000;
        append(out, (int) (rest / 1_000), "Thousand");
        rest %= 1_000;
        append(out, (int) (rest / 100), "Hundred");
        int last = (int) (rest % 100);
        if (last > 0) {
            out.append(out.isEmpty() ? "" : " ").append(belowHundred(last));
        }
        return out.toString();
    }

    private static void append(StringBuilder out, int count, String unit) {
        if (count > 0) {
            out.append(out.isEmpty() ? "" : " ").append(belowHundred(count)).append(' ').append(unit);
        }
    }

    private static String belowHundred(int n) {
        if (n < 20) {
            return ONES[n];
        }
        return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
    }
}
