package com.employger.service.metric;

import com.employger.entity.Payroll;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Everything paid to employees before deductions. */
@Component
@Order(1)
public class GrossPayMetric extends PayrollMetric {

    @Override
    public String key() {
        return "grossPay";
    }

    @Override
    public String label() {
        return "Gross pay";
    }

    @Override
    public BigDecimal amountOf(Payroll payroll) {
        return orZero(payroll.getGrossPay());
    }
}
