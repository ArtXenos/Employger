package com.employger.service.salary;

import com.employger.entity.Employee;

import java.time.LocalDate;

/**
 * One rule for when a salary change may take effect. SalaryHistoryService
 * checks every rule the same way, without knowing which ones there are;
 * each subclass says only what it refuses. Adding a rule means adding one
 * subclass.
 */
public abstract class SalaryChangeRule {

    /** Throws IllegalArgumentException or IllegalStateException with a message for HR. */
    public abstract void check(Employee employee, LocalDate effectiveDate, LocalDate today);
}
