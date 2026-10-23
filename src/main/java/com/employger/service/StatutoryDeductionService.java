package com.employger.service;

import com.employger.entity.PayCycle;
import com.employger.service.contribution.StatutoryContribution;
import com.employger.service.contribution.StatutoryContribution.PeriodShares;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Employee shares of Philippine statutory contributions and the withholding
 * tax for one pay period. Each contribution's formula lives in its own
 * StatutoryContribution subclass (package service.contribution), with
 * rates from system settings (seeded by migration V3 with the values
 * verified 2026-10-22; see docs/STATUTORY_DEDUCTIONS.md); the tax table is
 * PhilippineWithholdingTax.
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
    private final List<StatutoryContribution> statutoryContributions;

    /**
     * @param contributions every StatutoryContribution bean; each type must
     *                      appear exactly once
     */
    public StatutoryDeductionService(
            SystemSettingService systemSettingService,
            PayrollCalculationService payrollCalculationService,
            List<StatutoryContribution> contributions) {

        this.systemSettingService = systemSettingService;
        this.payrollCalculationService = payrollCalculationService;
        this.statutoryContributions = List.copyOf(contributions);

        for (StatutoryContribution.Type type : StatutoryContribution.Type.values()) {
            long count = this.statutoryContributions.stream().filter(c -> c.type() == type).count();
            if (count != 1) {
                throw new IllegalStateException(
                        "Expected exactly one " + type + " contribution, found " + count);
            }
        }
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

        // Every contribution is asked the same way; each answers with its own formula.
        Map<StatutoryContribution.Type, PeriodShares> shares = new EnumMap<>(StatutoryContribution.Type.class);
        for (StatutoryContribution contribution : statutoryContributions) {
            shares.put(contribution.type(), contribution.forPeriod(salary, periodShare));
        }

        BigDecimal contributions = shares.values().stream()
                .map(PeriodShares::employee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
                shares.get(StatutoryContribution.Type.SSS).employer(),
                shares.values().stream()
                        .map(PeriodShares::employeesCompensation)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                shares.get(StatutoryContribution.Type.PHILHEALTH).employer(),
                shares.get(StatutoryContribution.Type.PAG_IBIG).employer());

        if (contributions.add(tax).compareTo(grossPay) > 0) {
            notes.add("Statutory deductions (" + contributions.add(tax)
                    + ") exceed gross pay (" + grossPay + "); net pay is set to zero. Review this payroll.");
        }

        return new StatutoryDeductions(
                shares.get(StatutoryContribution.Type.SSS).employee(),
                shares.get(StatutoryContribution.Type.PHILHEALTH).employee(),
                shares.get(StatutoryContribution.Type.PAG_IBIG).employee(),
                contributions, money(taxable), tax, employer, notes);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
