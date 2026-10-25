package com.employger.service;

import com.employger.entity.Employee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Where one employee's 13th month pay for a year stands (PD 851): earned so
 * far, already paid, entered but not yet paid, and the balance still due.
 *
 * Encapsulation: the amounts are fixed when it is made, and everything the
 * report shows is derived here, so the report, the employee's own view and
 * the generator can't disagree about what "earned" or "due" means.
 */
public final class ThirteenthMonthStatus {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private final Integer employeeId;
    private final String employeeName;
    private final int year;
    private final String notEligibleReason;
    private final BigDecimal basicSalaryEarned;
    private final int payrollCount;
    private final BigDecimal paid;
    private final BigDecimal entered;

    private ThirteenthMonthStatus(Employee employee, int year, String notEligibleReason,
                                  BigDecimal basicSalaryEarned, int payrollCount,
                                  BigDecimal paid, BigDecimal entered) {
        this.employeeId = employee.getEmployeeId();
        this.employeeName = employee.getFirstName() + " " + employee.getLastName();
        this.year = year;
        this.notEligibleReason = notEligibleReason;
        this.basicSalaryEarned = basicSalaryEarned.setScale(2, RoundingMode.HALF_UP);
        this.payrollCount = payrollCount;
        this.paid = paid.setScale(2, RoundingMode.HALF_UP);
        this.entered = entered.setScale(2, RoundingMode.HALF_UP);
    }

    /** Not entitled for this year, e.g. a managerial employee. */
    static ThirteenthMonthStatus notEligible(Employee employee, int year, String reason) {
        return new ThirteenthMonthStatus(employee, year, reason,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * basicSalaryEarned: regular pay of the year's processed, approved and
     * paid payroll. paid: 13th month entries already paid by processed
     * payroll. entered: entries waiting for their payroll.
     */
    static ThirteenthMonthStatus of(Employee employee, int year, BigDecimal basicSalaryEarned,
                                    int payrollCount, BigDecimal paid, BigDecimal entered) {
        return new ThirteenthMonthStatus(employee, year, null,
                basicSalaryEarned, payrollCount, paid, entered);
    }

    /** PD 851: pay it no later than December 24. */
    public static LocalDate dueDate(int year) {
        return LocalDate.of(year, 12, 24);
    }

    public boolean isEligible() {
        return notEligibleReason == null;
    }

    /** Why the employee is not entitled; null when eligible. */
    public String getNotEligibleReason() {
        return notEligibleReason;
    }

    /** Basic salary earned so far / 12; zero when not eligible. */
    public BigDecimal getEarned() {
        if (!isEligible()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return basicSalaryEarned.divide(TWELVE, 2, RoundingMode.HALF_UP);
    }

    /** Earned, less what is paid or already entered; negative if over-entered. */
    public BigDecimal getBalanceDue() {
        return getEarned().subtract(paid).subtract(entered);
    }

    /** One line for people: what, if anything, HR still has to do. */
    public String getStatus() {
        if (!isEligible()) {
            return "Not eligible: " + notEligibleReason;
        }
        if (payrollCount == 0 || basicSalaryEarned.signum() <= 0) {
            return "No processed payroll in " + year + " yet.";
        }

        int balance = getBalanceDue().signum();

        if (balance < 0) {
            return "More entered or paid than earned so far.";
        }
        if (balance > 0) {
            return paid.signum() > 0 || entered.signum() > 0
                    ? "Partly entered; generate again to top up."
                    : "Not entered yet; generate it on the Benefits page.";
        }
        return entered.signum() > 0
                ? "Entered; paid with the payroll that covers its date."
                : "Paid in full so far.";
    }

    public Integer getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getBasicSalaryEarned() {
        return basicSalaryEarned;
    }

    public int getPayrollCount() {
        return payrollCount;
    }

    public BigDecimal getPaid() {
        return paid;
    }

    public BigDecimal getEntered() {
        return entered;
    }
}
