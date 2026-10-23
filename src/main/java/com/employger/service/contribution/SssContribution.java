package com.employger.service.contribution;

import com.employger.service.SystemSettingService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SSS (SSS Circular 2024-006): employee and employer shares are rates of
 * the Monthly Salary Credit, and the employer also pays Employees'
 * Compensation.
 */
@Component
public class SssContribution extends StatutoryContribution {

    public SssContribution(SystemSettingService settings) {
        super(settings);
    }

    @Override
    public Type type() {
        return Type.SSS;
    }

    /** Employee share = rate x MSC. */
    @Override
    public BigDecimal monthlyEmployeeShare(BigDecimal salary) {
        return monthlySalaryCredit(salary).multiply(setting("sss_employee_rate"));
    }

    /** Employer share = rate x MSC (10% in the 2025 schedule). */
    @Override
    public BigDecimal monthlyEmployerShare(BigDecimal salary) {
        return monthlySalaryCredit(salary).multiply(setting("sss_employer_rate"));
    }

    /** Employees' Compensation, paid by the employer: a flat amount by MSC. */
    @Override
    public BigDecimal monthlyEmployeesCompensation(BigDecimal salary) {
        return monthlySalaryCredit(salary).compareTo(setting("sss_ec_msc_threshold")) < 0
                ? setting("sss_ec_amount_low")
                : setting("sss_ec_amount_high");
    }

    /**
     * Monthly Salary Credit. Brackets are `step` wide and centred on each
     * credit (e.g. 5,250 to 5,749.99 -> 5,500), clamped to [minimum,
     * maximum]. The credit above 20,000 is the MPF part; the rates apply to
     * the whole credit.
     */
    public BigDecimal monthlySalaryCredit(BigDecimal salary) {

        BigDecimal minimum = setting("sss_msc_minimum");
        BigDecimal maximum = setting("sss_msc_maximum");
        BigDecimal step = setting("sss_msc_step");

        BigDecimal steps = salary
                .subtract(minimum.subtract(step.divide(BigDecimal.valueOf(2))))
                .divide(step, 0, RoundingMode.FLOOR);

        return minimum.add(step.multiply(steps)).max(minimum).min(maximum);
    }
}
