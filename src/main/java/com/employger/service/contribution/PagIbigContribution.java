package com.employger.service.contribution;

import com.employger.service.SystemSettingService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pag-IBIG (HDMF): rates of compensation up to the maximum fund salary.
 * The employee pays a lower rate at or below the low-salary threshold.
 */
@Component
public class PagIbigContribution extends StatutoryContribution {

    public PagIbigContribution(SystemSettingService settings) {
        super(settings);
    }

    @Override
    public Type type() {
        return Type.PAG_IBIG;
    }

    /** Lower rate at or below the threshold; basis capped at the maximum fund salary. */
    @Override
    public BigDecimal monthlyEmployeeShare(BigDecimal salary) {

        BigDecimal rate = salary.compareTo(setting("pagibig_low_salary_threshold")) <= 0
                ? setting("pagibig_low_salary_rate")
                : setting("pagibig_employee_rate");

        return fundSalary(salary).multiply(rate);
    }

    /**
     * RA 9679 Sec. 7: 2% for every employee (including those earning 1,500
     * or less), on compensation up to the maximum fund salary.
     */
    @Override
    public BigDecimal monthlyEmployerShare(BigDecimal salary) {
        return fundSalary(salary).multiply(setting("pagibig_employer_rate"));
    }

    private BigDecimal fundSalary(BigDecimal salary) {
        return salary.min(setting("pagibig_max_fund_salary"));
    }
}
