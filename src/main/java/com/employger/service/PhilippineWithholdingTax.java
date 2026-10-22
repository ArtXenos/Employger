package com.employger.service;

import com.employger.entity.PayCycle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Withholding tax on compensation, BIR Revenue Regulations No. 11-2018,
 * Annex "E" (Revised Withholding Tax Table, effective January 1, 2023 and
 * onwards), verified 2026-10-22 against
 * https://bir-cdn.bir.gov.ph/local/pdf/Annex%20E%20RR%2011-2018.pdf
 *
 * Each bracket reads "fixed tax + rate x (compensation - over)" and applies
 * when compensation exceeds "over". Compensation at or below the first
 * "over" is not taxed. If the BIR issues a new table, replace the brackets
 * here and update docs/STATUTORY_DEDUCTIONS.md.
 */
final class PhilippineWithholdingTax {

    record Bracket(BigDecimal over, BigDecimal fixedTax, BigDecimal rate) {

        static Bracket of(String over, String fixedTax, String rate) {
            return new Bracket(new BigDecimal(over), new BigDecimal(fixedTax), new BigDecimal(rate));
        }
    }

    static final List<Bracket> WEEKLY = List.of(
            Bracket.of("4808", "0.00", "0.15"),
            Bracket.of("7692", "432.60", "0.20"),
            Bracket.of("15385", "1971.20", "0.25"),
            Bracket.of("38462", "7740.45", "0.30"),
            Bracket.of("153846", "42355.65", "0.35"));

    static final List<Bracket> SEMI_MONTHLY = List.of(
            Bracket.of("10417", "0.00", "0.15"),
            Bracket.of("16667", "937.50", "0.20"),
            Bracket.of("33333", "4270.70", "0.25"),
            Bracket.of("83333", "16770.70", "0.30"),
            Bracket.of("333333", "91770.70", "0.35"));

    static final List<Bracket> MONTHLY = List.of(
            Bracket.of("20833", "0.00", "0.15"),
            Bracket.of("33333", "1875.00", "0.20"),
            Bracket.of("66667", "8541.80", "0.25"),
            Bracket.of("166667", "33541.80", "0.30"),
            Bracket.of("666667", "183541.80", "0.35"));

    private PhilippineWithholdingTax() {
    }

    /**
     * Tax to withhold for one pay period. Annex E has no bi-weekly table;
     * a bi-weekly period is treated as two weekly periods (half the
     * compensation through the weekly table, doubled).
     */
    static BigDecimal forPeriod(PayCycle.CycleType cycleType, BigDecimal taxableCompensation) {

        if (taxableCompensation == null || taxableCompensation.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        BigDecimal tax = switch (cycleType) {
            case WEEKLY -> apply(WEEKLY, taxableCompensation);
            case BI_WEEKLY -> apply(WEEKLY, taxableCompensation.divide(BigDecimal.valueOf(2)))
                    .multiply(BigDecimal.valueOf(2));
            case SEMI_MONTHLY -> apply(SEMI_MONTHLY, taxableCompensation);
            case MONTHLY -> apply(MONTHLY, taxableCompensation);
        };

        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal apply(List<Bracket> table, BigDecimal compensation) {

        Bracket applicable = null;

        for (Bracket bracket : table) {
            if (compensation.compareTo(bracket.over()) > 0) {
                applicable = bracket;
            }
        }

        if (applicable == null) {
            return BigDecimal.ZERO;
        }

        return applicable.fixedTax().add(
                applicable.rate().multiply(compensation.subtract(applicable.over())));
    }
}
