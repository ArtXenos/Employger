package com.employger.dto;

import com.employger.entity.Employee;

/**
 * The few employee fields that payroll, attendance and payslip responses
 * need. Date of birth, address, phone, email and salary stay out: the full
 * record is only available from /api/employees.
 */
public record EmployeeSummary(
        Integer employeeId,
        String firstName,
        String lastName,
        String department,
        String jobTitle) {

    public static EmployeeSummary from(Employee employee) {

        if (employee == null) {
            return null;
        }

        return new EmployeeSummary(
                employee.getEmployeeId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getDepartment(),
                employee.getJobTitle());
    }
}
