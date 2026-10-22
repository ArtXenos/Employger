package com.employger.dto;

import com.employger.entity.Attendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceResponse(
        Integer attendanceId,
        EmployeeSummary employee,
        LocalDate attendanceDate,
        LocalTime timeIn,
        LocalTime timeOut,
        BigDecimal regularHours,
        String status,
        String remarks) {

    public static AttendanceResponse from(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getAttendanceId(),
                EmployeeSummary.from(attendance.getEmployee()),
                attendance.getAttendanceDate(),
                attendance.getTimeIn(),
                attendance.getTimeOut(),
                attendance.getRegularHours(),
                attendance.getStatus() != null ? attendance.getStatus().name() : null,
                attendance.getRemarks());
    }
}
