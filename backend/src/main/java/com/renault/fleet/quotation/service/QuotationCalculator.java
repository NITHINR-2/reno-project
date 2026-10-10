package com.renault.fleet.quotation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The quotation money rules (CLAUDE.md), with {@link BigDecimal} only:
 * per line {@code base = qty × unitPrice}, {@code gst = base × rate}, each rounded HALF_UP to 2 places, then summed;
 * {@code total = base + gst}; {@code depositShortfall = max(0, requiredDeposit − eligibleDeposits)}.
 */
public final class QuotationCalculator {

    /** NUMERIC(15,2): at most 13 digits before the decimal point. */
    private static final int MAX_INTEGER_DIGITS = 13;

    public record LineInput(int quantity, BigDecimal unitPrice) {
    }

    public record LineResult(int quantity, BigDecimal unitPrice, BigDecimal baseAmount, BigDecimal gstAmount,
            BigDecimal lineTotal) {
    }

    public record Breakdown(List<LineResult> lines, BigDecimal gstRate, BigDecimal baseAmount, BigDecimal gstAmount,
            BigDecimal totalAmount, BigDecimal requiredDeposit, BigDecimal depositReceived, BigDecimal depositShortfall) {
    }

    private QuotationCalculator() {
    }

    public static Breakdown calculate(List<LineInput> lines, BigDecimal gstRate, BigDecimal requiredDeposit,
            BigDecimal eligibleDeposits) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A quotation needs at least one line.");
        }
        if (gstRate.signum() < 0 || gstRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("GST rate must be in [0, 1).");
        }
        if (requiredDeposit.signum() < 0 || eligibleDeposits.signum() < 0) {
            throw new IllegalArgumentException("Deposits cannot be negative.");
        }
        List<LineResult> results = new ArrayList<>();
        BigDecimal base = money(BigDecimal.ZERO);
        BigDecimal gst = money(BigDecimal.ZERO);
        for (LineInput line : lines) {
            if (line.quantity() <= 0 || line.unitPrice().signum() < 0) {
                throw new IllegalArgumentException("Quantity must be positive and unit price non-negative.");
            }
            BigDecimal unitPrice = money(line.unitPrice());
            BigDecimal lineBase = money(unitPrice.multiply(BigDecimal.valueOf(line.quantity())));
            BigDecimal lineGst = money(lineBase.multiply(gstRate));
            results.add(new LineResult(line.quantity(), unitPrice, lineBase, lineGst, lineBase.add(lineGst)));
            base = base.add(lineBase);
            gst = gst.add(lineGst);
        }
        BigDecimal total = base.add(gst);
        BigDecimal required = money(requiredDeposit);
        BigDecimal received = money(eligibleDeposits);
        BigDecimal shortfall = required.subtract(received).max(BigDecimal.ZERO).setScale(2, RoundingMode.UNNECESSARY);
        for (BigDecimal amount : List.of(total, required, received)) {
            fits(amount);
        }
        return new Breakdown(List.copyOf(results), gstRate, base, gst, total, required, received, shortfall);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void fits(BigDecimal amount) {
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException("Amount " + amount.toPlainString() + " exceeds NUMERIC(15,2).");
        }
    }
}
