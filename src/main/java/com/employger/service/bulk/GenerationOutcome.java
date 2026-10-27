package com.employger.service.bulk;

import com.employger.entity.Employee;
import com.employger.entity.Payroll;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * What happened to one employee when payroll was generated for a whole
 * pay cycle. The page and the summary only call result() and message();
 * each kind of outcome answers for itself, so nothing checks which kind
 * it has.
 */
public sealed interface GenerationOutcome permits GenerationOutcome.Created, GenerationOutcome.Skipped {

    Integer employeeId();

    String employeeName();

    /** "CREATED" or "SKIPPED". */
    @JsonProperty
    String result();

    /** One line for HR. */
    @JsonProperty
    String message();

    /** A DRAFT payroll was created. */
    record Created(Integer employeeId, String employeeName, Integer payrollId, BigDecimal netPay)
            implements GenerationOutcome {

        static Created of(Payroll payroll) {
            Employee employee = payroll.getEmployee();
            return new Created(employee.getEmployeeId(), nameOf(employee),
                    payroll.getPayrollId(), payroll.getNetPay());
        }

        @Override
        public String result() {
            return "CREATED";
        }

        @Override
        public String message() {
            return "DRAFT payroll #" + payrollId + " created, net pay "
                    + (netPay == null ? "-" : "₱" + String.format(Locale.ENGLISH, "%,.2f", netPay)) + ".";
        }
    }

    /** No payroll was created; reason is the rule that stopped it. */
    record Skipped(Integer employeeId, String employeeName, String reason) implements GenerationOutcome {

        static Skipped of(Employee employee, String reason) {
            return new Skipped(employee.getEmployeeId(), nameOf(employee), reason);
        }

        @Override
        public String result() {
            return "SKIPPED";
        }

        @Override
        public String message() {
            return reason;
        }
    }

    private static String nameOf(Employee employee) {
        return employee.getLastName() + ", " + employee.getFirstName();
    }
}
