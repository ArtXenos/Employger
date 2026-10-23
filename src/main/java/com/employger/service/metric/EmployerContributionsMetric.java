package com.employger.service.metric;

import com.employger.entity.Payroll;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The employer's SSS (with EC), PhilHealth and Pag-IBIG shares: a cost on
 * top of gross pay, never deducted from employees. NULL on payroll saved
 * before migration V5, which counts as zero.
 */
@Component
@Order(3)
public class EmployerContributionsMetric extends PayrollMetric {

    @Override
    public String key() {
        return "employerContributions";
    }

    @Override
    public String label() {
        return "Employer contributions";
    }

    @Override
    public BigDecimal amountOf(Payroll payroll) {
        return orZero(payroll.getSssEmployerContribution())
                .add(orZero(payroll.getSssEcContribution()))
                .add(orZero(payroll.getPhilHealthEmployerContribution()))
                .add(orZero(payroll.getPagIbigEmployerContribution()));
    }
}
