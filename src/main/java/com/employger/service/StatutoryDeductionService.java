package com.employger.service;

import com.employger.entity.PayCycle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Employee shares of Philippine statutory contributions and the withholding
 * tax for one pay period. Rates come from system settings (seeded by
 * migration V3 with the values verified 2026-10-22; see
 * docs/STATUTORY_DEDUCTIONS.md); the tax table is PhilippineWithholdingTax.
 *
 * Simplifications, documented for HR:
 *  - All three contributions use the employee's monthly basic salary as the
 *    basis (PhilHealth's prescribed basis; used for SSS and Pag-IBIG too).
 *  - Monthly contributions are spread evenly over the cycles of a year:
 *    per cycle = monthly x 12 / periods per year (half per semi-monthly
 *    cycle).
 *  - Taxable compensation = gross pay - employee contributions.
 *  - Minimum wage earners (RR 11-2018, RR 2-98 Sec. 2.78.1(B)(13) and
 *    2.79(A)): the pay passed in as exempt (regular and overtime pay) is not
 *    taxed; only the rest of gross pay, i.e. other compensation such as
 *    bonuses, is run through the withholding table. Contributions still
 *    apply and are not subtracted again from that other compensation.
 *  - Non-taxable benefits (13th month and others up to 90,000) are not
 *    modelled.
 *  - Employer shares are not part of employee pay and are not computed.
 */
@Service
public class StatutoryDeductionService {

    public record StatutoryDeductions(
            BigDecimal sss,
            BigDecimal philHealth,
            BigDecimal pagIbig,
            BigDecimal totalContributions,
            BigDecimal taxableCompensation,
            BigDecimal withholdingTax,
            List<String> notes) {

        static StatutoryDeductions none(BigDecimal grossPay) {
            BigDecimal zero = BigDecimal.ZERO.setScale(2);
            return new StatutoryDeductions(zero, zero, zero, zero, grossPay, zero, List.of());
        }
    }

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private final SystemSettingService systemSettingService;
    private final PayrollCalculationService payrollCalculationService;

    public StatutoryDeductionService(
            SystemSettingService systemSettingService,
            PayrollCalculationService payrollCalculationService) {

        this.systemSettingService = systemSettingService;
        this.payrollCalculationService = payrollCalculationService;
    }

    /** Off unless the statutory_deductions_enabled setting is "true". */
    public boolean isEnabled() {
        return systemSettingService
                .findSettingValue("statutory_deductions_enabled")
                .map(value -> value.trim().equalsIgnoreCase("true"))
                .orElse(false);
    }

    public StatutoryDeductions calculate(
            BigDecimal monthlyBasicSalary,
            PayCycle.CycleType cycleType,
            BigDecimal grossPay) {

        return calculate(monthlyBasicSalary, cycleType, grossPay, false, BigDecimal.ZERO);
    }

    /**
     * @param minimumWageEarner whether the employee is flagged as an MWE
     * @param mweExemptPay      for an MWE, the part of gross pay that is
     *                          exempt (statutory minimum wage and overtime
     *                          pay); ignored otherwise
     */
    public StatutoryDeductions calculate(
            BigDecimal monthlyBasicSalary,
            PayCycle.CycleType cycleType,
            BigDecimal grossPay,
            boolean minimumWageEarner,
            BigDecimal mweExemptPay) {

        if (!isEnabled()) {
            return StatutoryDeductions.none(grossPay);
        }

        BigDecimal salary = monthlyBasicSalary != null ? monthlyBasicSalary : BigDecimal.ZERO;

        BigDecimal periodShare = MONTHS_PER_YEAR.divide(
                BigDecimal.valueOf(payrollCalculationService.periodsPerYear(cycleType)),
                10,
                RoundingMode.HALF_UP);

        BigDecimal sss = money(monthlySss(salary).multiply(periodShare));
        BigDecimal philHealth = money(monthlyPhilHealth(salary).multiply(periodShare));
        BigDecimal pagIbig = money(monthlyPagIbig(salary).multiply(periodShare));

        BigDecimal contributions = sss.add(philHealth).add(pagIbig);

        List<String> notes = new ArrayList<>();

        BigDecimal taxable;

        if (minimumWageEarner) {
            BigDecimal exempt = mweExemptPay != null ? mweExemptPay : BigDecimal.ZERO;
            taxable = grossPay.subtract(exempt).max(BigDecimal.ZERO);
            notes.add("Minimum wage earner: regular and overtime pay (" + money(exempt.min(grossPay))
                    + ") exempt from withholding tax under RR 11-2018; "
                    + "only other compensation (" + money(taxable) + ") is taxed.");
        } else {
            taxable = grossPay.subtract(contributions).max(BigDecimal.ZERO);
        }

        BigDecimal tax = PhilippineWithholdingTax.forPeriod(cycleType, taxable);

        if (contributions.add(tax).compareTo(grossPay) > 0) {
            notes.add("Statutory deductions (" + contributions.add(tax)
                    + ") exceed gross pay (" + grossPay + "); net pay is set to zero. Review this payroll.");
        }

        return new StatutoryDeductions(sss, philHealth, pagIbig, contributions, money(taxable), tax, notes);
    }

    /**
     * SSS Circular 2024-006: employee share = rate x Monthly Salary Credit.
     * MSC brackets are `step` wide and centred on each credit (e.g. 5,250 to
     * 5,749.99 -> 5,500), clamped to [minimum, maximum].
     */
    BigDecimal monthlySss(BigDecimal salary) {

        BigDecimal minimum = decimal("sss_msc_minimum");
        BigDecimal maximum = decimal("sss_msc_maximum");
        BigDecimal step = decimal("sss_msc_step");

        BigDecimal steps = salary
                .subtract(minimum.subtract(step.divide(BigDecimal.valueOf(2))))
                .divide(step, 0, RoundingMode.FLOOR);

        BigDecimal credit = minimum.add(step.multiply(steps)).max(minimum).min(maximum);

        return credit.multiply(decimal("sss_employee_rate"));
    }

    /** PhilHealth: rate x monthly basic salary within [floor, ceiling]; employee pays its share. */
    BigDecimal monthlyPhilHealth(BigDecimal salary) {

        BigDecimal basis = salary
                .max(decimal("philhealth_salary_floor"))
                .min(decimal("philhealth_salary_ceiling"));

        return basis
                .multiply(decimal("philhealth_premium_rate"))
                .multiply(decimal("philhealth_employee_share"));
    }

    /** Pag-IBIG: lower rate at or below the threshold; basis capped at the maximum fund salary. */
    BigDecimal monthlyPagIbig(BigDecimal salary) {

        BigDecimal rate = salary.compareTo(decimal("pagibig_low_salary_threshold")) <= 0
                ? decimal("pagibig_low_salary_rate")
                : decimal("pagibig_employee_rate");

        return salary.min(decimal("pagibig_max_fund_salary")).multiply(rate);
    }

    private BigDecimal decimal(String settingName) {
        try {
            return new BigDecimal(systemSettingService.getSettingValue(settingName).trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid " + settingName + " setting; expected a number");
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
