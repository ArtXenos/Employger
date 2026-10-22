package com.employger.dto;

import com.employger.entity.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An employee as returned by /api/employees. Leaves out the profile image,
 * which no page shows and which could be megabytes per employee.
 */
public class EmployeeResponse {

    private final Integer employeeId;
    private final String firstName;
    private final String lastName;
    private final LocalDate dateOfBirth;
    private final String email;
    private final String phone;
    private final String address;
    private final String department;
    private final String gender;
    private final BigDecimal baseSalary;
    private final boolean minimumWageEarner;
    private final boolean managerial;
    private final String sssNumber;
    private final String philHealthNumber;
    private final String pagIbigNumber;
    private final String tin;
    private final String postalCode;
    private final String designation;
    private final String employmentStatus;
    private final LocalDate dateHired;
    private final String jobTitle;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public EmployeeResponse(Employee employee) {
        this(employee, false);
    }

    /**
     * @param includeGovernmentIds true only for administrators and HR
     *                             managers; otherwise the SSS, PhilHealth,
     *                             Pag-IBIG and TIN numbers are null
     */
    public EmployeeResponse(Employee employee, boolean includeGovernmentIds) {
        this.employeeId = employee.getEmployeeId();
        this.firstName = employee.getFirstName();
        this.lastName = employee.getLastName();
        this.dateOfBirth = employee.getDateOfBirth();
        this.email = employee.getEmail();
        this.phone = employee.getPhone();
        this.address = employee.getAddress();
        this.department = employee.getDepartment();
        this.gender = employee.getGender();
        this.baseSalary = employee.getBaseSalary();
        this.minimumWageEarner = employee.isMinimumWageEarner();
        this.managerial = employee.isManagerial();
        this.sssNumber = includeGovernmentIds ? employee.getSssNumber() : null;
        this.philHealthNumber = includeGovernmentIds ? employee.getPhilHealthNumber() : null;
        this.pagIbigNumber = includeGovernmentIds ? employee.getPagIbigNumber() : null;
        this.tin = includeGovernmentIds ? employee.getTin() : null;
        this.postalCode = employee.getPostalCode();
        this.designation = employee.getDesignation();
        this.employmentStatus = employee.getEmploymentStatus() != null
                ? employee.getEmploymentStatus().name()
                : null;
        this.dateHired = employee.getDateHired();
        this.jobTitle = employee.getJobTitle();
        this.createdAt = employee.getCreatedAt();
        this.updatedAt = employee.getUpdatedAt();
    }

    public Integer getEmployeeId() {
        return employeeId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getDepartment() {
        return department;
    }

    public String getGender() {
        return gender;
    }

    public BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public boolean isMinimumWageEarner() {
        return minimumWageEarner;
    }

    public boolean isManagerial() {
        return managerial;
    }

    public String getSssNumber() {
        return sssNumber;
    }

    public String getPhilHealthNumber() {
        return philHealthNumber;
    }

    public String getPagIbigNumber() {
        return pagIbigNumber;
    }

    public String getTin() {
        return tin;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getDesignation() {
        return designation;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public LocalDate getDateHired() {
        return dateHired;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
