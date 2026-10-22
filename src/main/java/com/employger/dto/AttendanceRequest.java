package com.employger.dto;

import com.employger.entity.Attendance;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What a client may set on an attendance record. The record ID and the
 * employee come from the URL, never from the body. Business rules (ABSENT
 * days have no hours, locked payroll days, overtime) stay in
 * AttendanceService.
 */
public record AttendanceRequest(

        @NotNull(message = "Attendance date is required.")
        LocalDate attendanceDate,

        LocalTime timeIn,

        LocalTime timeOut,

        // Optional: derived from the times for PRESENT and LATE days.
        @DecimalMin(value = "0", message = "Regular hours must be between 0 and 24.")
        @DecimalMax(value = "24", message = "Regular hours must be between 0 and 24.")
        BigDecimal regularHours,

        @NotNull(message = "Attendance status is required.")
        Attendance.Status status,

        @Size(max = 255, message = "Remarks must be at most 255 characters.")
        String remarks) {

    public Attendance toEntity() {

        Attendance attendance = new Attendance();

        attendance.setAttendanceDate(attendanceDate);
        attendance.setTimeIn(timeIn);
        attendance.setTimeOut(timeOut);
        attendance.setRegularHours(regularHours);
        attendance.setStatus(status);
        attendance.setRemarks(remarks == null || remarks.isBlank() ? null : remarks.trim());

        return attendance;
    }
}
