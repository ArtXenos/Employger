package com.employger.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Leave an employee files for a range of days (migration V9). HR approves
 * or rejects it through the lifecycle inherited from ApprovalRequest. On
 * approval each working day in the range is marked in attendance: LEAVE
 * for paid leave (no deduction), ABSENT for unpaid leave (a day deducted).
 */
@Entity
@Table(name = "leave_requests")
public class LeaveRequest extends ApprovalRequest {

    public enum LeaveType {
        PAID(Attendance.Status.LEAVE, "Paid leave"),
        UNPAID(Attendance.Status.ABSENT, "Unpaid leave");

        private final Attendance.Status attendanceStatus;
        private final String label;

        LeaveType(Attendance.Status attendanceStatus, String label) {
            this.attendanceStatus = attendanceStatus;
            this.label = label;
        }

        /** How an approved day of this leave is recorded in attendance. */
        public Attendance.Status attendanceStatus() {
            return attendanceStatus;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_id")
    private Integer leaveId;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // Weekdays in the range that aren't holidays: the days the leave covers.
    @Column(name = "working_days", nullable = false)
    private int workingDays;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Override
    protected String requestKind() {
        return "leave";
    }

    // Approving leave already marked its days LEAVE or ABSENT in attendance,
    // so cancelling it would leave attendance wrong.
    @Override
    protected void checkCancellable() {
        if (getStatus() == Status.APPROVED) {
            throw new IllegalStateException("Approved leave has already been recorded in attendance "
                    + "and can't be cancelled. Correct the attendance days instead.");
        }
    }

    @Override
    public String describe() {
        return leaveType + " leave #" + leaveId + " (employee #" + employee.getEmployeeId() + ", "
                + startDate + " to " + endDate + ", " + workingDays + " working day"
                + (workingDays == 1 ? "" : "s") + ")";
    }

    /** True if this leave and the given range share at least one date. */
    public boolean overlaps(LocalDate start, LocalDate end) {
        return !startDate.isAfter(end) && !endDate.isBefore(start);
    }

    public Integer getLeaveId() {
        return leaveId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(int workingDays) {
        this.workingDays = workingDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
