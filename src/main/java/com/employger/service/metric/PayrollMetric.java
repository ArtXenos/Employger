package com.employger.service.metric;

import com.employger.entity.Payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * One amount the company overview totals over payroll records, such as
 * gross pay or labor cost. ExecutiveDashboardService totals every metric
 * the same way (per month, per department) without knowing which one it
 * is; each subclass only says what one payroll contributes.
 */
public abstract class PayrollMetric {

    /** Stable JSON key, e.g. "laborCost". */
    public abstract String key();

    /** Name shown on the dashboard. */
    public abstract String label();

    /** What one payroll contributes. Never null. */
    public abstract BigDecimal amountOf(Payroll payroll);

    /** The sum over these payrolls, rounded to centavos. Final, so every metric totals alike. */
    public final BigDecimal total(Collection<Payroll> payrolls) {
        return payrolls.stream()
                .map(this::amountOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Payroll saved before a column existed holds NULL there. */
    protected static BigDecimal orZero(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
