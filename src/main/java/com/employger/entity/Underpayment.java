package com.employger.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The employee was paid too little: the difference is added to a later payroll. */
@Entity
@DiscriminatorValue("UNDERPAYMENT")
public class Underpayment extends PayrollAdjustment {

    protected Underpayment() {
        // for JPA
    }

    public Underpayment(Payroll correctedPayroll, BigDecimal amount, String reason,
                        LocalDate applyDate, User createdBy) {
        super(correctedPayroll, amount, reason, applyDate, false, createdBy);
    }

    @Override
    public BigDecimal signedAmount() {
        return getAmount();
    }

    @Override
    public String kindLabel() {
        return "Underpayment";
    }

    // Money owed to the employee needs no consent.
    @Override
    protected void checkKindRules() {
    }
}
