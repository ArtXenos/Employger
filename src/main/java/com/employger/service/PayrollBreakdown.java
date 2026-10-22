package com.employger.service;

import com.employger.entity.Attendance;
import com.employger.entity.Overtime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything one payroll calculation produces: the amounts, the inputs used,
 * and the per-day detail behind the attendance deduction. Produced only by
 * PayrollService.calculate, so preview and saved payroll always agree.
 */
public record PayrollBreakdown(
        BigDecimal cycleBasePay,
        BigDecimal basePay,
        BigDecimal hourlyRate,
        BigDecimal factorRate,
        BigDecimal regularHoursPerDay,
        int workingDaysInCycle,
        int workingDaysEmployed,
        List<AttendanceDay> attendanceDays,
        List<LocalDate> missingAttendanceDates,
        BigDecimal unpaidHours,
        BigDecimal attendanceDeduction,
        BigDecimal regularPay,
        List<Overtime> overtime,
        BigDecimal overtimePay,
        BigDecimal bonusAmount,
        BigDecimal grossPay,
        BigDecimal taxAmount,
        BigDecimal deductionAmount,
        BigDecimal netPay,
        List<String> notes) {

    /**
     * One attendance record's effect on pay. The per-day deduction is rounded
     * for display; the total deduction is rounded once from the unrounded sum.
     */
    public record AttendanceDay(
            Integer attendanceId,
            LocalDate date,
            Attendance.Status status,
            boolean restDay,
            BigDecimal recordedHours,
            BigDecimal unpaidHours,
            BigDecimal deduction) {
    }
}
