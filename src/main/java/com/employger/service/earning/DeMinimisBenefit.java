package com.employger.service.earning;

import com.employger.entity.Earning;
import com.employger.service.PayrollCalculationService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A de minimis benefit (RR 11-2018, Sec. 2.78.1(A)(3)): exempt from tax up
 * to a limit per period. Each kind has its own period (month, half-year or
 * year) and its own limit; EarningsCalculator applies them the same way to
 * every kind.
 */
public abstract class DeMinimisBenefit {

    /** The days whose entries share one limit. */
    public record ExemptionPeriod(LocalDate start, LocalDate end) {
    }

    private final PayrollCalculationService rates;

    protected DeMinimisBenefit(PayrollCalculationService rates) {
        this.rates = rates;
    }

    /** The earning type this benefit handles. */
    public abstract Earning.Type type();

    /** The period that contains an entry given on this date. */
    public abstract ExemptionPeriod periodContaining(LocalDate date);

    /**
     * The exempt amount per period.
     *
     * @param dailyRate the employee's daily rate (only monetized leave uses it)
     */
    public abstract BigDecimal limit(BigDecimal dailyRate);

    protected final BigDecimal setting(String settingName, String defaultValue) {
        return rates.decimalSetting(settingName, defaultValue);
    }

    protected static ExemptionPeriod calendarYear(LocalDate date) {
        return new ExemptionPeriod(date.withDayOfYear(1), date.withMonth(12).withDayOfMonth(31));
    }
}
