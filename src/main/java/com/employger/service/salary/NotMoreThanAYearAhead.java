package com.employger.service.salary;

import com.employger.entity.Employee;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Catches a mistyped year: like the hire date, at most a year ahead. */
@Component
public class NotMoreThanAYearAhead extends SalaryChangeRule {

    @Override
    public void check(Employee employee, LocalDate effectiveDate, LocalDate today) {
        if (effectiveDate.isAfter(today.plusYears(1))) {
            throw new IllegalArgumentException("The new salary can't take effect more than a year from today.");
        }
    }
}
