package com.employger.service.salary;

import com.employger.entity.Employee;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** A salary can't change before the employee started. */
@Component
public class NotBeforeHireDate extends SalaryChangeRule {

    @Override
    public void check(Employee employee, LocalDate effectiveDate, LocalDate today) {
        if (employee.getDateHired() != null && effectiveDate.isBefore(employee.getDateHired())) {
            throw new IllegalArgumentException("The new salary can't take effect before the date hired ("
                    + employee.getDateHired() + ").");
        }
    }
}
