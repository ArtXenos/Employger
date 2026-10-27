package com.employger.service.salary;

import com.employger.entity.SalaryChange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An employee's monthly salary over time. Payroll asks it for the salary on
 * a date instead of reading the employee's current salary, so a raise in
 * the middle of a pay cycle is paid from its effective date.
 *
 * Immutable: built once from the recorded changes, oldest first.
 */
public final class SalaryTimeline {

    private final BigDecimal currentSalary;
    private final List<SalaryChange> changes;

    /**
     * @param currentSalary the employee's base salary, used when no change
     *                      is recorded
     * @param changes       oldest first
     */
    public SalaryTimeline(BigDecimal currentSalary, List<SalaryChange> changes) {
        this.currentSalary = currentSalary;
        this.changes = List.copyOf(changes);
    }

    /**
     * The salary in effect on that date: the latest change that took effect
     * on or before it. Before the first change, that change's previous
     * salary. With no changes, the current salary. Null if never set.
     */
    public BigDecimal salaryOn(LocalDate date) {
        SalaryChange inEffect = null;

        for (SalaryChange change : changes) {
            if (change.getEffectiveDate().isAfter(date)) {
                break;
            }
            inEffect = change;
        }

        if (inEffect != null) {
            return inEffect.getNewSalary();
        }

        if (!changes.isEmpty() && changes.get(0).getPreviousSalary() != null) {
            return changes.get(0).getPreviousSalary();
        }

        return currentSalary;
    }

    /**
     * The dates after from, up to and including to, on which a different
     * salary takes effect. Empty when the salary is the same all through.
     */
    public List<LocalDate> changeDatesWithin(LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();

        for (SalaryChange change : changes) {
            LocalDate date = change.getEffectiveDate();

            if (date.isAfter(from) && !date.isAfter(to) && !dates.contains(date)
                    && !sameAmount(salaryOn(date.minusDays(1)), salaryOn(date))) {
                dates.add(date);
            }
        }

        return dates;
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
