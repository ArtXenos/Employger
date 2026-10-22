package com.employger.dto;

import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A payroll as returned by /api/payroll and inside payslips. Field names
 * match the Payroll entity so existing pages keep working; the employee and
 * processing user are reduced to summaries.
 */
public record PayrollResponse(
        Integer payrollId,
        EmployeeSummary employee,
        PayCycle payCycle,
        BigDecimal regularPay,
        BigDecimal overtimePay,
        BigDecimal bonusAmount,
        BigDecimal grossPay,
        BigDecimal taxAmount,
        BigDecimal deductionAmount,
        BigDecimal netPay,
        String status,
        LocalDateTime processedAt,
        UserSummary processedBy,
        LocalDateTime paymentDate,
        String paymentMethod,
        String paymentReference,
        BigDecimal basePay,
        BigDecimal attendanceDeduction,
        BigDecimal hourlyRate,
        BigDecimal factorRate,
        BigDecimal regularHoursPerDay,
        Integer workingDaysInCycle,
        Integer workingDaysEmployed,
        String calculationNotes,
        LocalDateTime calculatedAt,
        BigDecimal sssContribution,
        BigDecimal philHealthContribution,
        BigDecimal pagIbigContribution,
        BigDecimal taxableCompensation,
        BigDecimal sssEmployerContribution,
        BigDecimal sssEcContribution,
        BigDecimal philHealthEmployerContribution,
        BigDecimal pagIbigEmployerContribution,
        BigDecimal employerContributionTotal) {

    // PayCycle is returned as is: it has no links to people.
    public static PayrollResponse from(Payroll payroll) {
        return new PayrollResponse(
                payroll.getPayrollId(),
                EmployeeSummary.from(payroll.getEmployee()),
                payroll.getPayCycle(),
                payroll.getRegularPay(),
                payroll.getOvertimePay(),
                payroll.getBonusAmount(),
                payroll.getGrossPay(),
                payroll.getTaxAmount(),
                payroll.getDeductionAmount(),
                payroll.getNetPay(),
                payroll.getStatus() != null ? payroll.getStatus().name() : null,
                payroll.getProcessedAt(),
                UserSummary.from(payroll.getProcessedBy()),
                payroll.getPaymentDate(),
                payroll.getPaymentMethod(),
                payroll.getPaymentReference(),
                payroll.getBasePay(),
                payroll.getAttendanceDeduction(),
                payroll.getHourlyRate(),
                payroll.getFactorRate(),
                payroll.getRegularHoursPerDay(),
                payroll.getWorkingDaysInCycle(),
                payroll.getWorkingDaysEmployed(),
                payroll.getCalculationNotes(),
                payroll.getCalculatedAt(),
                payroll.getSssContribution(),
                payroll.getPhilHealthContribution(),
                payroll.getPagIbigContribution(),
                payroll.getTaxableCompensation(),
                payroll.getSssEmployerContribution(),
                payroll.getSssEcContribution(),
                payroll.getPhilHealthEmployerContribution(),
                payroll.getPagIbigEmployerContribution(),
                employerTotal(payroll));
    }

    // NULL for payroll calculated before employer shares were recorded (V5).
    private static BigDecimal employerTotal(Payroll payroll) {

        if (payroll.getSssEmployerContribution() == null) {
            return null;
        }

        return Stream.of(
                        payroll.getSssEmployerContribution(),
                        payroll.getSssEcContribution(),
                        payroll.getPhilHealthEmployerContribution(),
                        payroll.getPagIbigEmployerContribution())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
