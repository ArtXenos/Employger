package com.employger.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The employee was paid too much: the difference is deducted from a later
 * payroll. A deduction from wages needs the employee's written consent
 * (Labor Code Art. 113), and payroll refuses to process if it would take
 * more than that payroll's net pay.
 */
@Entity
@DiscriminatorValue("OVERPAYMENT")
public class OverpaymentRecovery extends PayrollAdjustment {

    protected OverpaymentRecovery() {
        // for JPA
    }

    public OverpaymentRecovery(Payroll correctedPayroll, BigDecimal amount, String reason,
                               LocalDate applyDate, boolean employeeConsent, User createdBy) {
        super(correctedPayroll, amount, reason, applyDate, employeeConsent, createdBy);
    }

    @Override
    public BigDecimal signedAmount() {
        return getAmount().negate();
    }

    @Override
    public String kindLabel() {
        return "Overpayment recovery";
    }

    @Override
    protected void checkKindRules() {
        if (!isEmployeeConsent()) {
            throw new IllegalArgumentException("An overpayment can only be deducted with the employee's "
                    + "written consent (Labor Code Art. 113). Confirm you have it.");
        }
    }
}
