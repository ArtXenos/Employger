package com.employger.dto;

import com.employger.entity.Payslip;

import java.time.LocalDateTime;

/**
 * A payslip as returned by /api/payslips. The server-side file path is
 * left out.
 */
public record PayslipResponse(
        Integer payslipId,
        PayrollResponse payroll,
        LocalDateTime generatedAt,
        UserSummary generatedBy) {

    public static PayslipResponse from(Payslip payslip) {
        return new PayslipResponse(
                payslip.getPayslipId(),
                PayrollResponse.from(payslip.getPayroll()),
                payslip.getGeneratedAt(),
                UserSummary.from(payslip.getGeneratedBy()));
    }
}
