package com.employger.service.contribution;

import com.employger.service.SystemSettingService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * PhilHealth: one premium on the monthly basic salary, shared between the
 * employee and the employer.
 */
@Component
public class PhilHealthContribution extends StatutoryContribution {

    public PhilHealthContribution(SystemSettingService settings) {
        super(settings);
    }

    @Override
    public Type type() {
        return Type.PHILHEALTH;
    }

    /** The employee's share of the premium. */
    @Override
    public BigDecimal monthlyEmployeeShare(BigDecimal salary) {
        return monthlyPremium(salary).multiply(setting("philhealth_employee_share"));
    }

    /** The employer pays the rest of the premium. */
    @Override
    public BigDecimal monthlyEmployerShare(BigDecimal salary) {
        return monthlyPremium(salary).subtract(monthlyEmployeeShare(salary));
    }

    /** Premium: rate x monthly basic salary within [floor, ceiling]. */
    public BigDecimal monthlyPremium(BigDecimal salary) {

        BigDecimal basis = salary
                .max(setting("philhealth_salary_floor"))
                .min(setting("philhealth_salary_ceiling"));

        return basis.multiply(setting("philhealth_premium_rate"));
    }
}
