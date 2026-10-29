package com.employger.dto;

import com.employger.entity.Employee;

/**
 * What the printable badge shows. Only administrators and HR managers get
 * it (SecurityConfig): whoever has the code can clock in as that employee.
 * badgeCode is null until a badge is issued.
 */
public record BadgeCardResponse(
        Integer employeeId,
        String employeeName,
        String department,
        String jobTitle,
        boolean employed,
        String badgeCode) {

    public static BadgeCardResponse from(Employee employee) {
        return new BadgeCardResponse(
                employee.getEmployeeId(),
                employee.getFirstName() + " " + employee.getLastName(),
                employee.getDepartment(),
                employee.getJobTitle(),
                employee.isEmployed(),
                employee.getBadgeCode());
    }
}
