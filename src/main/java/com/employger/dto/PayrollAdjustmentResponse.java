package com.employger.dto;

import com.employger.entity.PayrollAdjustment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayrollAdjustmentResponse(
        Integer adjustmentId,
        String kind,
        String kindLabel,
        EmployeeSummary employee,
        Integer correctedPayrollId,
        String correctedCycleName,
        BigDecimal amount,
        BigDecimal signedAmount,
        String reason,
        LocalDate applyDate,
        boolean employeeConsent,
        Integer payrollId,
        String payrollStatus,
        boolean locked,
        UserSummary createdBy) {

    public static PayrollAdjustmentResponse from(PayrollAdjustment adjustment) {
        return new PayrollAdjustmentResponse(
                adjustment.getAdjustmentId(),
                adjustment.isRecovery() ? "OVERPAYMENT" : "UNDERPAYMENT",
                adjustment.kindLabel(),
                EmployeeSummary.from(adjustment.getEmployee()),
                adjustment.getCorrectedPayroll().getPayrollId(),
                adjustment.getCorrectedPayroll().getPayCycle().getCycleName(),
                adjustment.getAmount(),
                adjustment.signedAmount(),
                adjustment.getReason(),
                adjustment.getApplyDate(),
                adjustment.isEmployeeConsent(),
                adjustment.getPayroll() != null ? adjustment.getPayroll().getPayrollId() : null,
                adjustment.getPayroll() != null ? adjustment.getPayroll().getStatus().name() : null,
                adjustment.isLocked(),
                UserSummary.from(adjustment.getCreatedBy()));
    }
}
