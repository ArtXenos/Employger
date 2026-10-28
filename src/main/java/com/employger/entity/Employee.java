package com.employger.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    // From the choice lists (migration V16). The JSON keeps "department"
    // and "jobTitle" as names: see getDepartment() and getJobTitle().
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "job_title_id")
    private JobTitle jobTitle;

    // Never serialized: attendance, overtime and payroll responses embed the
    // employee, and an image could add megabytes to each of them.
    @JsonIgnore
    @Column(name = "profile_image")
    private byte[] profileImage;

    @Column(length = 20)
    private String gender;

    @Column(name = "base_salary", nullable = false)
    private BigDecimal baseSalary = BigDecimal.ZERO;

    // Paid the regional statutory minimum wage (RR 11-2018): regular and
    // overtime pay are exempt from withholding tax. Set by HR; the system
    // does not know regional wage rates. Migration V4.
    @Column(name = "minimum_wage_earner", nullable = false)
    private boolean minimumWageEarner = false;

    // Managerial employees are not entitled to mandatory 13th month pay
    // (PD 851). Migration V6.
    @Column(nullable = false)
    private boolean managerial = false;

    // Government numbers for remittance reports and BIR Form 2316. Only
    // administrators and HR managers see them (EmployeeResponse).
    @Column(name = "sss_number", length = 20)
    private String sssNumber;

    @Column(name = "philhealth_number", length = 20)
    private String philHealthNumber;

    @Column(name = "pagibig_number", length = 20)
    private String pagIbigNumber;

    @Column(length = 20)
    private String tin;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 100)
    private String designation;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Column(name = "date_hired", nullable = false)
    private LocalDate dateHired;

    // Last day of work (migration V11). The final pay cycle's
    // base pay stops here; NULL while the employee still works here.
    @Column(name = "separation_date")
    private LocalDate separationDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum EmploymentStatus {
        ACTIVE,
        INACTIVE
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Integer getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Integer employeeId) {
        this.employeeId = employeeId;
    }

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

    /**
     * Changes the contact details together, the way both HR and the
     * employee (self-service) do: email is required and trimmed; a blank
     * phone or address is stored as NULL.
     */
    public void updateContactDetails(String email, String phone, String address) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        this.email = email.trim();
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
        this.address = address == null || address.isBlank() ? null : address.trim();
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

    /** The department's name, or null. */
    @JsonProperty("department")
    public String getDepartment() {
        return department == null ? null : department.getName();
    }

    /** The job title's name, or null. */
    @JsonProperty("jobTitle")
    public String getJobTitle() {
        return jobTitle == null ? null : jobTitle.getName();
    }

    public Department getDepartmentOption() {
        return department;
    }

    public JobTitle getJobTitleOption() {
        return jobTitle;
    }

    /**
     * Sets the department and job title together, so a job title is never
     * left under another department. Either may be null, but a job title
     * needs its department. A retired entry may be kept by an employee
     * who already has it, not newly given.
     */
    public void assignPosition(Department newDepartment, JobTitle newJobTitle) {
        if (newJobTitle != null && newDepartment == null) {
            throw new IllegalArgumentException("Choose the department before the job title.");
        }
        if (newJobTitle != null && !newJobTitle.belongsTo(newDepartment)) {
            throw new IllegalArgumentException("The job title \"" + newJobTitle.getName()
                    + "\" is not in the " + newDepartment.getName() + " department.");
        }
        if (newDepartment != null && !newDepartment.isActive() && !newDepartment.sameAs(department)) {
            throw new IllegalStateException("The department \"" + newDepartment.getName()
                    + "\" is retired and can't be chosen.");
        }
        if (newJobTitle != null && !newJobTitle.isSelectable() && !newJobTitle.sameAs(jobTitle)) {
            throw new IllegalStateException("The job title \"" + newJobTitle.getName()
                    + "\" is retired and can't be chosen.");
        }

        this.department = newDepartment;
        this.jobTitle = newJobTitle;
    }

    public byte[] getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(byte[] profileImage) {
        this.profileImage = profileImage;
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

    public boolean isMinimumWageEarner() {
        return minimumWageEarner;
    }

    public void setMinimumWageEarner(boolean minimumWageEarner) {
        this.minimumWageEarner = minimumWageEarner;
    }

    public boolean isManagerial() {
        return managerial;
    }

    public LocalDate getSeparationDate() {
        return separationDate;
    }

    public void setSeparationDate(LocalDate separationDate) {
        this.separationDate = separationDate;
    }

    // Still working for the company: only then may they have an active login.
    @JsonIgnore
    public boolean isEmployed() {
        return employmentStatus == EmploymentStatus.ACTIVE;
    }

    public void setManagerial(boolean managerial) {
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

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public LocalDate getDateHired() {
        return dateHired;
    }

    public void setDateHired(LocalDate dateHired) {
        this.dateHired = dateHired;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}