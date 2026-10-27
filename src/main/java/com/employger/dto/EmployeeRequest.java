package com.employger.dto;

import com.employger.entity.Employee;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a client may set when creating or updating an employee. The ID,
 * timestamps and profile image are deliberately absent, so a request can
 * never overwrite them. Limits match the employees table.
 */
public class EmployeeRequest {

    @NotBlank(message = "First name is required.")
    @Size(max = 100, message = "First name must be at most 100 characters.")
    private String firstName;

    @NotBlank(message = "Last name is required.")
    @Size(max = 100, message = "Last name must be at most 100 characters.")
    private String lastName;

    @NotNull(message = "Date of birth is required.")
    @Past(message = "Date of birth must be in the past.")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Email is required.")
    @Email(message = "Email is not a valid email address.")
    @Size(max = 150, message = "Email must be at most 150 characters.")
    private String email;

    @Size(max = 30, message = "Phone must be at most 30 characters.")
    private String phone;

    @Size(max = 255, message = "Address must be at most 255 characters.")
    private String address;

    @Size(max = 100, message = "Department must be at most 100 characters.")
    private String department;

    @Size(max = 20, message = "Gender must be at most 20 characters.")
    private String gender;

    @NotNull(message = "Base salary is required.")
    @DecimalMin(value = "0.00", message = "Base salary cannot be negative.")
    @Digits(integer = 10, fraction = 2,
            message = "Base salary must have at most 10 digits before and 2 after the decimal point.")
    private BigDecimal baseSalary;

    // Optional: new employees default to false, updates keep the current value.
    private Boolean minimumWageEarner;

    // Optional: new employees default to false (rank and file), updates keep
    // the current value. Managerial employees get no mandatory 13th month.
    private Boolean managerial;

    // Government numbers: digits and dashes only. Blank clears the number.
    @Size(max = 20, message = "SSS number must be at most 20 characters.")
    @Pattern(regexp = "[0-9-]*", message = "SSS number may contain only digits and dashes.")
    private String sssNumber;

    @Size(max = 20, message = "PhilHealth number must be at most 20 characters.")
    @Pattern(regexp = "[0-9-]*", message = "PhilHealth number may contain only digits and dashes.")
    private String philHealthNumber;

    @Size(max = 20, message = "Pag-IBIG number must be at most 20 characters.")
    @Pattern(regexp = "[0-9-]*", message = "Pag-IBIG number may contain only digits and dashes.")
    private String pagIbigNumber;

    @Size(max = 20, message = "TIN must be at most 20 characters.")
    @Pattern(regexp = "[0-9-]*", message = "TIN may contain only digits and dashes.")
    private String tin;

    @Size(max = 20, message = "Postal code must be at most 20 characters.")
    private String postalCode;

    @Size(max = 100, message = "Designation must be at most 100 characters.")
    private String designation;

    // Optional: new employees default to ACTIVE, updates keep the current status.
    private Employee.EmploymentStatus employmentStatus;

    @NotNull(message = "Date hired is required.")
    private LocalDate dateHired;

    // Optional last day of work; empty means still employed.
    private LocalDate separationDate;

    // When the base salary changes on an update: the date the new salary
    // takes effect (today if left out) and an optional reason.
    private LocalDate salaryEffectiveDate;

    @Size(max = 255, message = "The reason for the salary change must be at most 255 characters.")
    private String salaryChangeReason;

    @Size(max = 100, message = "Job title must be at most 100 characters.")
    private String jobTitle;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(BigDecimal baseSalary) {
        this.baseSalary = baseSalary;
    }

    public Boolean getMinimumWageEarner() {
        return minimumWageEarner;
    }

    public void setMinimumWageEarner(Boolean minimumWageEarner) {
        this.minimumWageEarner = minimumWageEarner;
    }

    public Boolean getManagerial() {
        return managerial;
    }

    public void setManagerial(Boolean managerial) {
        this.managerial = managerial;
    }

    public String getSssNumber() {
        return sssNumber;
    }

    public void setSssNumber(String sssNumber) {
        this.sssNumber = sssNumber;
    }

    public String getPhilHealthNumber() {
        return philHealthNumber;
    }

    public void setPhilHealthNumber(String philHealthNumber) {
        this.philHealthNumber = philHealthNumber;
    }

    public String getPagIbigNumber() {
        return pagIbigNumber;
    }

    public void setPagIbigNumber(String pagIbigNumber) {
        this.pagIbigNumber = pagIbigNumber;
    }

    public String getTin() {
        return tin;
    }

    public void setTin(String tin) {
        this.tin = tin;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Employee.EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(Employee.EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public LocalDate getDateHired() {
        return dateHired;
    }

    public LocalDate getSeparationDate() {
        return separationDate;
    }

    public void setSeparationDate(LocalDate separationDate) {
        this.separationDate = separationDate;
    }

    public LocalDate getSalaryEffectiveDate() {
        return salaryEffectiveDate;
    }

    public void setSalaryEffectiveDate(LocalDate salaryEffectiveDate) {
        this.salaryEffectiveDate = salaryEffectiveDate;
    }

    public String getSalaryChangeReason() {
        return salaryChangeReason;
    }

    public void setSalaryChangeReason(String salaryChangeReason) {
        this.salaryChangeReason = salaryChangeReason;
    }

    public void setDateHired(LocalDate dateHired) {
        this.dateHired = dateHired;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }
}
