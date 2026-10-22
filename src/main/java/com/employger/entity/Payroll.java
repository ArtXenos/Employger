package com.employger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payroll_employee_cycle", columnNames = {
                "employee_id",
                "pay_cycle_id"
        })
})
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payroll_id")
    private Integer payrollId;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "pay_cycle_id", nullable = false)
    private PayCycle payCycle;

    @Column(name = "regular_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal regularPay = BigDecimal.ZERO;

    @Column(name = "overtime_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal overtimePay = BigDecimal.ZERO;

    @Column(name = "bonus_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal bonusAmount = BigDecimal.ZERO;

    @Column(name = "gross_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossPay = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "deduction_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @ManyToOne
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    // Calculation snapshot (migration V2): how the amounts above were
    // derived, recorded at calculation time. NULL for payroll calculated
    // before V2.

    @Column(name = "base_pay", precision = 12, scale = 2)
    private BigDecimal basePay;

    @Column(name = "attendance_deduction", precision = 12, scale = 2)
    private BigDecimal attendanceDeduction;

    @Column(name = "hourly_rate", precision = 14, scale = 6)
    private BigDecimal hourlyRate;

    @Column(name = "factor_rate", precision = 10, scale = 2)
    private BigDecimal factorRate;

    @Column(name = "regular_hours_per_day", precision = 5, scale = 2)
    private BigDecimal regularHoursPerDay;

    @Column(name = "working_days_in_cycle")
    private Integer workingDaysInCycle;

    @Column(name = "working_days_employed")
    private Integer workingDaysEmployed;

    @Column(name = "calculation_notes", columnDefinition = "text")
    private String calculationNotes;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    // Employee statutory contributions and tax basis (migration V3).
    // deduction_amount is their sum; tax_amount is the withholding tax.

    @Column(name = "sss_contribution", precision = 12, scale = 2)
    private BigDecimal sssContribution;

    @Column(name = "philhealth_contribution", precision = 12, scale = 2)
    private BigDecimal philHealthContribution;

    @Column(name = "pagibig_contribution", precision = 12, scale = 2)
    private BigDecimal pagIbigContribution;

    @Column(name = "taxable_compensation", precision = 12, scale = 2)
    private BigDecimal taxableCompensation;

    // Employer shares (migration V5): a cost to the company, never deducted
    // from the employee's pay. NULL on payroll calculated before V5.
    @Column(name = "sss_employer_contribution", precision = 12, scale = 2)
    private BigDecimal sssEmployerContribution;

    @Column(name = "sss_ec_contribution", precision = 12, scale = 2)
    private BigDecimal sssEcContribution;

    @Column(name = "philhealth_employer_contribution", precision = 12, scale = 2)
    private BigDecimal philHealthEmployerContribution;

    @Column(name = "pagibig_employer_contribution", precision = 12, scale = 2)
    private BigDecimal pagIbigEmployerContribution;

    public enum Status {
        DRAFT,
        PROCESSED,
        PAID
    }

    public Integer getPayrollId() {
        return payrollId;
    }

    public void setPayrollId(Integer payrollId) {
        this.payrollId = payrollId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public PayCycle getPayCycle() {
        return payCycle;
    }

    public void setPayCycle(PayCycle payCycle) {
        this.payCycle = payCycle;
    }

    public BigDecimal getRegularPay() {
        return regularPay;
    }

    public void setRegularPay(BigDecimal regularPay) {
        this.regularPay = regularPay;
    }

    public BigDecimal getOvertimePay() {
        return overtimePay;
    }

    public void setOvertimePay(BigDecimal overtimePay) {
        this.overtimePay = overtimePay;
    }

    public BigDecimal getBonusAmount() {
        return bonusAmount;
    }

    public void setBonusAmount(BigDecimal bonusAmount) {
        this.bonusAmount = bonusAmount;
    }

    public BigDecimal getGrossPay() {
        return grossPay;
    }

    public void setGrossPay(BigDecimal grossPay) {
        this.grossPay = grossPay;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getDeductionAmount() {
        return deductionAmount;
    }

    public void setDeductionAmount(
            BigDecimal deductionAmount) {
        this.deductionAmount = deductionAmount;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public void setNetPay(BigDecimal netPay) {
        this.netPay = netPay;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public User getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(User processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public BigDecimal getBasePay() {
        return basePay;
    }

    public void setBasePay(BigDecimal basePay) {
        this.basePay = basePay;
    }

    public BigDecimal getAttendanceDeduction() {
        return attendanceDeduction;
    }

    public void setAttendanceDeduction(BigDecimal attendanceDeduction) {
        this.attendanceDeduction = attendanceDeduction;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public BigDecimal getFactorRate() {
        return factorRate;
    }

    public void setFactorRate(BigDecimal factorRate) {
        this.factorRate = factorRate;
    }

    public BigDecimal getRegularHoursPerDay() {
        return regularHoursPerDay;
    }

    public void setRegularHoursPerDay(BigDecimal regularHoursPerDay) {
        this.regularHoursPerDay = regularHoursPerDay;
    }

    public Integer getWorkingDaysInCycle() {
        return workingDaysInCycle;
    }

    public void setWorkingDaysInCycle(Integer workingDaysInCycle) {
        this.workingDaysInCycle = workingDaysInCycle;
    }

    public Integer getWorkingDaysEmployed() {
        return workingDaysEmployed;
    }

    public void setWorkingDaysEmployed(Integer workingDaysEmployed) {
        this.workingDaysEmployed = workingDaysEmployed;
    }

    public String getCalculationNotes() {
        return calculationNotes;
    }

    public void setCalculationNotes(String calculationNotes) {
        this.calculationNotes = calculationNotes;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public BigDecimal getSssContribution() {
        return sssContribution;
    }

    public void setSssContribution(BigDecimal sssContribution) {
        this.sssContribution = sssContribution;
    }

    public BigDecimal getPhilHealthContribution() {
        return philHealthContribution;
    }

    public void setPhilHealthContribution(BigDecimal philHealthContribution) {
        this.philHealthContribution = philHealthContribution;
    }

    public BigDecimal getPagIbigContribution() {
        return pagIbigContribution;
    }

    public void setPagIbigContribution(BigDecimal pagIbigContribution) {
        this.pagIbigContribution = pagIbigContribution;
    }

    public BigDecimal getTaxableCompensation() {
        return taxableCompensation;
    }

    public void setTaxableCompensation(BigDecimal taxableCompensation) {
        this.taxableCompensation = taxableCompensation;
    }

    public BigDecimal getSssEmployerContribution() {
        return sssEmployerContribution;
    }

    public void setSssEmployerContribution(BigDecimal sssEmployerContribution) {
        this.sssEmployerContribution = sssEmployerContribution;
    }

    public BigDecimal getSssEcContribution() {
        return sssEcContribution;
    }

    public void setSssEcContribution(BigDecimal sssEcContribution) {
        this.sssEcContribution = sssEcContribution;
    }

    public BigDecimal getPhilHealthEmployerContribution() {
        return philHealthEmployerContribution;
    }

    public void setPhilHealthEmployerContribution(BigDecimal philHealthEmployerContribution) {
        this.philHealthEmployerContribution = philHealthEmployerContribution;
    }

    public BigDecimal getPagIbigEmployerContribution() {
        return pagIbigEmployerContribution;
    }

    public void setPagIbigEmployerContribution(BigDecimal pagIbigEmployerContribution) {
        this.pagIbigEmployerContribution = pagIbigEmployerContribution;
    }
}