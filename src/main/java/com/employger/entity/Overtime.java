package com.employger.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "overtime")
public class Overtime {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

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

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    // ---------- lifecycle: PENDING -> APPROVED or REJECTED, then final ----------

    /** Throws unless this request is still pending. */
    public void checkPending() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Only pending overtime can be approved or rejected");
        }
    }

    /** PENDING -> APPROVED. */
    public void approve(User approvedBy) {
        checkPending();
        this.status = Status.APPROVED;
        this.approvedBy = approvedBy;
    }

    /** PENDING -> REJECTED. approvedBy records whoever decided. */
    public void reject(User rejectedBy) {
        checkPending();
        this.status = Status.REJECTED;
        this.approvedBy = rejectedBy;
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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(User approvedBy) {
        this.approvedBy = approvedBy;
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