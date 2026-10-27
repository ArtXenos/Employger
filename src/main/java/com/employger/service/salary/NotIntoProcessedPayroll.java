package com.employger.service.salary;

import com.employger.entity.Employee;
import com.employger.entity.Payroll;
import com.employger.repository.PayrollRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Processed, approved and paid payroll is never recalculated, so a change
 * can't reach back into it. Pay owed for those cycles is recorded as a
 * correction (underpayment) instead.
 */
@Component
public class NotIntoProcessedPayroll extends SalaryChangeRule {

    private final PayrollRepository payrollRepository;

    public NotIntoProcessedPayroll(PayrollRepository payrollRepository) {
        this.payrollRepository = payrollRepository;
    }

    @Override
    public void check(Employee employee, LocalDate effectiveDate, LocalDate today) {
        payrollRepository.findByEmployeeEmployeeId(employee.getEmployeeId()).stream()
                .filter(payroll -> !payroll.isDraft())
                .map(Payroll::getPayCycle)
                .max(Comparator.comparing(cycle -> cycle.getEndDate()))
                .filter(cycle -> !effectiveDate.isAfter(cycle.getEndDate()))
                .ifPresent(cycle -> {
                    throw new IllegalStateException("Payroll up to " + cycle.getEndDate() + " (\""
                            + cycle.getCycleName() + "\") is already processed. Choose a date after it, "
                            + "and record a correction on the Payroll page for pay owed before then.");
                });
    }
}
