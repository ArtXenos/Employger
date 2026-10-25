package com.employger.service;

import com.employger.entity.Attendance;
import com.employger.entity.Holiday;
import com.employger.entity.Overtime;
import com.employger.entity.PayrollAdjustment;

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
        BigDecimal premiumPay,
        BigDecimal nightDifferentialPay,
        EarningsCalculator.Result earnings,
        BigDecimal bonusAmount,
        List<PayrollAdjustment> adjustments,
        BigDecimal adjustmentAmount,
        BigDecimal grossPay,
        BigDecimal mweExemptPay,
        StatutoryDeductionService.StatutoryDeductions statutory,
        BigDecimal taxAmount,
        BigDecimal taxRefund,
        YearEndTaxService.Result yearEnd,
        BigDecimal deductionAmount,
        BigDecimal netPay,
        boolean recoveryExceedsPay,
        List<String> notes) {

    /**
     * One attendance record's effect on pay. The per-day amounts are rounded
     * for display; totals are rounded once from the unrounded sums.
     */
    public record AttendanceDay(
            Integer attendanceId,
            LocalDate date,
            Attendance.Status status,
            boolean restDay,
            Holiday.Type holidayType,
            BigDecimal recordedHours,
            BigDecimal unpaidHours,
            BigDecimal deduction,
            BigDecimal premium,
            BigDecimal nightDifferential) {
    }
}
