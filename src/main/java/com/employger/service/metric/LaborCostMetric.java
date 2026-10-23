package com.employger.service.metric;

import com.employger.entity.Payroll;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * What payroll costs the company: gross pay plus employer contributions.
 * Built from those two metrics (composition) rather than extending either:
 * labor cost is not a kind of gross pay, it is made of it.
 */
@Component
@Order(4)
public class LaborCostMetric extends PayrollMetric {

    private final GrossPayMetric grossPay;
    private final EmployerContributionsMetric employerContributions;

    public LaborCostMetric(GrossPayMetric grossPay, EmployerContributionsMetric employerContributions) {
        this.grossPay = grossPay;
        this.employerContributions = employerContributions;
    }

    @Override
    public String key() {
        return "laborCost";
    }

    @Override
    public String label() {
        return "Labor cost";
    }

    @Override
    public BigDecimal amountOf(Payroll payroll) {
        return grossPay.amountOf(payroll).add(employerContributions.amountOf(payroll));
    }
}
