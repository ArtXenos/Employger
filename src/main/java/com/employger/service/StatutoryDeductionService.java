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
 *  - Employer shares (SSS with EC, PhilHealth, Pag-IBIG) use the same
 *    basis and the same per-cycle spreading. They are recorded on the
 *    payroll as a cost and never deducted from the employee's pay.
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
            EmployerShares employer,
            List<String> notes) {

        static StatutoryDeductions none(BigDecimal grossPay) {
            BigDecimal zero = BigDecimal.ZERO.setScale(2);
            return new StatutoryDeductions(zero, zero, zero, zero, grossPay, zero, EmployerShares.none(), List.of());
        }
    }

    /**
     * What the employer pays on top of the employee's pay for one cycle.
     * Never deducted from the employee: it is a payroll cost.
     */
    public record EmployerShares(
            BigDecimal sss,
            BigDecimal ec,
            BigDecimal philHealth,
            BigDecimal pagIbig) {

        static EmployerShares none() {
            BigDecimal zero = BigDecimal.ZERO.setScale(2);
            return new EmployerShares(zero, zero, zero, zero);
        }

        public BigDecimal total() {
            return sss.add(ec).add(philHealth).add(pagIbig);
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

        return calculate(monthlyBasicSalary, cycleType, grossPay, BigDecimal.ZERO, minimumWageEarner, mweExemptPay);
    }

    /**
     * @param nonTaxableEarnings part of gross pay exempt for everyone: de
     *                           minimis within limits and 13th month / other
     *                           benefits within the 90,000 cap
     */
    public StatutoryDeductions calculate(
            BigDecimal monthlyBasicSalary,
            PayCycle.CycleType cycleType,
            BigDecimal grossPay,
            BigDecimal nonTaxableEarnings,
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
        BigDecimal nonTaxable = nonTaxableEarnings != null ? nonTaxableEarnings : BigDecimal.ZERO;

        if (minimumWageEarner) {
            BigDecimal exempt = mweExemptPay != null ? mweExemptPay : BigDecimal.ZERO;
            taxable = grossPay.subtract(nonTaxable).subtract(exempt).max(BigDecimal.ZERO);
            notes.add("Minimum wage earner: regular, overtime, holiday, night differential and hazard pay ("
                    + money(exempt.min(grossPay)) + ") exempt from withholding tax under RR 11-2018; "
                    + "only other compensation (" + money(taxable) + ") is taxed.");
        } else {
            taxable = grossPay.subtract(nonTaxable).subtract(contributions).max(BigDecimal.ZERO);
        }

        BigDecimal tax = PhilippineWithholdingTax.forPeriod(cycleType, taxable);

        EmployerShares employer = new EmployerShares(
                money(monthlySssEmployer(salary).multiply(periodShare)),
                money(monthlyEc(salary).multiply(periodShare)),
                money(monthlyPhilHealthEmployer(salary).multiply(periodShare)),
                money(monthlyPagIbigEmployer(salary).multiply(periodShare)));

        if (contributions.add(tax).compareTo(grossPay) > 0) {
            notes.add("Statutory deductions (" + contributions.add(tax)
                    + ") exceed gross pay (" + grossPay + "); net pay is set to zero. Review this payroll.");
        }

        return new StatutoryDeductions(sss, philHealth, pagIbig, contributions, money(taxable), tax, employer, notes);
    }

    /**
     * SSS Circular 2024-006: Monthly Salary Credit. Brackets are `step` wide
     * and centred on each credit (e.g. 5,250 to 5,749.99 -> 5,500), clamped
     * to [minimum, maximum]. The credit above 20,000 is the MPF part; the
     * rates apply to the whole credit.
     */
    BigDecimal monthlySalaryCredit(BigDecimal salary) {

        BigDecimal minimum = decimal("sss_msc_minimum");
        BigDecimal maximum = decimal("sss_msc_maximum");
        BigDecimal step = decimal("sss_msc_step");

        BigDecimal steps = salary
                .subtract(minimum.subtract(step.divide(BigDecimal.valueOf(2))))
                .divide(step, 0, RoundingMode.FLOOR);

        return minimum.add(step.multiply(steps)).max(minimum).min(maximum);
    }

    /** SSS employee share = rate x MSC. */
    BigDecimal monthlySss(BigDecimal salary) {
        return monthlySalaryCredit(salary).multiply(decimal("sss_employee_rate"));
    }

    /** SSS employer share = rate x MSC (10% in the 2025 schedule). */
    BigDecimal monthlySssEmployer(BigDecimal salary) {
        return monthlySalaryCredit(salary).multiply(decimal("sss_employer_rate"));
    }

    /** Employees' Compensation, paid by the employer: a flat amount by MSC. */
    BigDecimal monthlyEc(BigDecimal salary) {
        return monthlySalaryCredit(salary).compareTo(decimal("sss_ec_msc_threshold")) < 0
                ? decimal("sss_ec_amount_low")
                : decimal("sss_ec_amount_high");
    }

    /** PhilHealth premium: rate x monthly basic salary within [floor, ceiling]. */
    BigDecimal monthlyPhilHealthPremium(BigDecimal salary) {

        BigDecimal basis = salary
                .max(decimal("philhealth_salary_floor"))
                .min(decimal("philhealth_salary_ceiling"));

        return basis.multiply(decimal("philhealth_premium_rate"));
    }

    /** PhilHealth employee share of the premium. */
    BigDecimal monthlyPhilHealth(BigDecimal salary) {
        return monthlyPhilHealthPremium(salary).multiply(decimal("philhealth_employee_share"));
    }

    /** PhilHealth employer share: the rest of the premium. */
    BigDecimal monthlyPhilHealthEmployer(BigDecimal salary) {
        return monthlyPhilHealthPremium(salary).subtract(monthlyPhilHealth(salary));
    }

    /**
     * Pag-IBIG employer share: RA 9679 Sec. 7, 2% for every employee
     * (including those earning 1,500 or less), on compensation up to the
     * maximum fund salary.
     */
    BigDecimal monthlyPagIbigEmployer(BigDecimal salary) {
        return salary.min(decimal("pagibig_max_fund_salary")).multiply(decimal("pagibig_employer_rate"));
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
