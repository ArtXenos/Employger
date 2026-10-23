package com.employger.service.metric;

import com.employger.entity.Payroll;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Approved overtime paid in payroll (part of gross pay). */
@Component
@Order(2)
public class OvertimePayMetric extends PayrollMetric {

    @Override
    public String key() {
        return "overtimePay";
    }

    @Override
    public String label() {
        return "Overtime pay";
    }

    @Override
    public BigDecimal amountOf(Payroll payroll) {
        return orZero(payroll.getOvertimePay());
    }
}
