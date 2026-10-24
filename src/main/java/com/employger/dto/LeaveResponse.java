package com.employger.dto;

import com.employger.entity.LeaveRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A leave request as the pages show it: no salary or other personal data. */
public record LeaveResponse(
        Integer leaveId,
        Integer employeeId,
        String employeeName,
        String leaveType,
        String leaveTypeLabel,
        LocalDate startDate,
        LocalDate endDate,
        int workingDays,
        String reason,
        String status,
        String decidedBy,
        LocalDateTime createdAt) {

    public LeaveResponse(LeaveRequest leave) {
        this(
                leave.getLeaveId(),
                leave.getEmployee().getEmployeeId(),
                leave.getEmployee().getFirstName() + " " + leave.getEmployee().getLastName(),
                leave.getLeaveType().name(),
                leave.getLeaveType().label(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getWorkingDays(),
                leave.getReason(),
                leave.getStatus().name(),
                leave.getApprovedBy() != null ? leave.getApprovedBy().getUsername() : null,
                leave.getCreatedAt());
    }
}
