package com.employger.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Overtime an employee worked or asked for; HR approves or rejects it. */
@Entity
@Table(name = "overtime")
public class Overtime extends ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "overtime_id")
    private Integer overtimeId;

    @ManyToOne
    @JoinColumn(
        name = "employee_id",
        nullable = false
    )
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "attendance_id")
    private Attendance attendance;

    @Column(
        name = "overtime_date",
        nullable = false
    )
    private LocalDate overtimeDate;

    @Column(
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal hours = BigDecimal.ZERO;

    @Column(
        name = "overtime_rate",
        nullable = false,
        precision = 10,
        scale = 2
    )
    private BigDecimal overtimeRate = BigDecimal.ZERO;

    @Column(
        name = "overtime_amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal overtimeAmount = BigDecimal.ZERO;

    // The payroll that paid this overtime (migration V2). Set only by the
    // server when payroll is calculated; never read from request bodies.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_id")
    private Payroll payroll;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private LocalDateTime createdAt;

    // The lifecycle (approve, reject, checkPending) is inherited from
    // ApprovalRequest; only the wording is overtime's own.

    @Override
    protected String requestKind() {
        return "overtime";
    }

    @Override
    public String describe() {
        return "overtime #" + overtimeId + " (employee #" + employee.getEmployeeId() + ", "
                + overtimeDate + ", " + hours + " h, amount " + overtimeAmount + ")";
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Integer getOvertimeId() {
        return overtimeId;
    }

    public void setOvertimeId(Integer overtimeId) {
        this.overtimeId = overtimeId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Attendance getAttendance() {
        return attendance;
    }

    public void setAttendance(Attendance attendance) {
        this.attendance = attendance;
    }

    public LocalDate getOvertimeDate() {
        return overtimeDate;
    }

    public void setOvertimeDate(LocalDate overtimeDate) {
        this.overtimeDate = overtimeDate;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public BigDecimal getOvertimeRate() {
        return overtimeRate;
    }

    public void setOvertimeRate(BigDecimal overtimeRate) {
        this.overtimeRate = overtimeRate;
    }

    public BigDecimal getOvertimeAmount() {
        return overtimeAmount;
    }

    public void setOvertimeAmount(BigDecimal overtimeAmount) {
        this.overtimeAmount = overtimeAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Payroll getPayroll() {
        return payroll;
    }

    public void setPayroll(Payroll payroll) {
        this.payroll = payroll;
    }
}