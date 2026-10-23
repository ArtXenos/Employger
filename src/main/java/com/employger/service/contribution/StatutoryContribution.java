package com.employger.service.contribution;

import com.employger.service.SystemSettingService;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One Philippine statutory contribution: the monthly employee and employer
 * shares for a monthly basic salary. Rates come from system settings
 * (migrations V3 and V5; docs/STATUTORY_DEDUCTIONS.md).
 *
 * StatutoryDeductionService works only with this type. It asks every
 * contribution for its shares the same way, and each subclass answers
 * with its own formula.
 */
public abstract class StatutoryContribution {

    public enum Type {
        SSS,
        PHILHEALTH,
        PAG_IBIG
    }

    /** One contribution's shares for a pay period, rounded to centavos. */
    public record PeriodShares(
            BigDecimal employee,
            BigDecimal employer,
            BigDecimal employeesCompensation) {
    }

    private final SystemSettingService settings;

    protected StatutoryContribution(SystemSettingService settings) {
        this.settings = settings;
    }

    public abstract Type type();

    /** The employee's monthly share, deducted from pay. */
    public abstract BigDecimal monthlyEmployeeShare(BigDecimal salary);

    /** The employer's monthly share: a payroll cost, never deducted. */
    public abstract BigDecimal monthlyEmployerShare(BigDecimal salary);

    /**
     * Employees' Compensation (EC) premium, paid by the employer on top of
     * its share. Only SSS collects EC, so the default is zero.
     */
    public BigDecimal monthlyEmployeesCompensation(BigDecimal salary) {
        return BigDecimal.ZERO;
    }

    /**
     * The shares for one pay period: each monthly amount times the period's
     * share of a month (half for semi-monthly), rounded to centavos. Final,
     * so every contribution is spread and rounded the same way.
     */
    public final PeriodShares forPeriod(BigDecimal salary, BigDecimal periodShare) {
        return new PeriodShares(
                money(monthlyEmployeeShare(salary).multiply(periodShare)),
                money(monthlyEmployerShare(salary).multiply(periodShare)),
                money(monthlyEmployeesCompensation(salary).multiply(periodShare)));
    }

    /** A numeric rate or limit from system settings. */
    protected final BigDecimal setting(String settingName) {
        try {
            return new BigDecimal(settings.getSettingValue(settingName).trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid " + settingName + " setting; expected a number");
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
