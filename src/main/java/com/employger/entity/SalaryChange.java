package com.employger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One change of an employee's monthly base salary, from the date it takes
 * effect. A recorded change is history and never edited: there are no
 * setters, and record() is the only way to make one.
 */
@Entity
@Table(name = "salary_changes")
public class SalaryChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "salary_change_id")
    private Integer salaryChangeId;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // NULL when the employee had no salary before.
    @Column(name = "previous_salary", precision = 12, scale = 2)
    private BigDecimal previousSalary;

    @Column(name = "new_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal newSalary;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(length = 255)
    private String reason;

    @ManyToOne
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SalaryChange() {
        // For JPA.
    }

    /** A change to newSalary from effectiveDate; previousSalary is what it replaces. */
    public static SalaryChange record(
            Employee employee,
            BigDecimal previousSalary,
            BigDecimal newSalary,
            LocalDate effectiveDate,
            String reason,
            User changedBy) {

        if (newSalary == null || newSalary.signum() <= 0) {
            throw new IllegalArgumentException("The new salary must be greater than zero.");
        }

        if (effectiveDate == null) {
            throw new IllegalArgumentException("Choose the date the new salary takes effect.");
        }

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();

        if (trimmedReason != null && trimmedReason.length() > 255) {
            throw new IllegalArgumentException("The reason must be at most 255 characters.");
        }

        SalaryChange change = new SalaryChange();
        change.employee = employee;
        change.previousSalary = previousSalary;
        change.newSalary = newSalary;
        change.effectiveDate = effectiveDate;
        change.reason = trimmedReason;
        change.changedBy = changedBy;
        change.createdAt = LocalDateTime.now();
        return change;
    }

    public Integer getSalaryChangeId() {
        return salaryChangeId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public BigDecimal getPreviousSalary() {
        return previousSalary;
    }

    public BigDecimal getNewSalary() {
        return newSalary;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getReason() {
        return reason;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
