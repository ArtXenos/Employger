package com.employger.dto;

import com.employger.entity.LeaveRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * An employee filing their own leave. The employee comes from the login;
 * the working days and status are set by the server.
 */
public record LeaveFilingRequest(
        @NotNull(message = "Leave type is required.")
        LeaveRequest.LeaveType leaveType,

        @NotNull(message = "Start date is required.")
        LocalDate startDate,

        @NotNull(message = "End date is required.")
        LocalDate endDate,

        @Size(max = 255, message = "Reason must be at most 255 characters.")
        String reason) {
}
