package com.employger.dto;

import com.employger.entity.Earning;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EarningResponse(
        Integer earningId,
        EmployeeSummary employee,
        String earningType,
        BigDecimal amount,
        LocalDate dateGiven,
        String description,
        Integer payrollId,
        String payrollStatus,
        UserSummary createdBy) {

    public static EarningResponse from(Earning earning) {
        return new EarningResponse(
                earning.getEarningId(),
                EmployeeSummary.from(earning.getEmployee()),
                earning.getEarningType().name(),
                earning.getAmount(),
                earning.getDateGiven(),
                earning.getDescription(),
                earning.getPayroll() != null ? earning.getPayroll().getPayrollId() : null,
                earning.getPayroll() != null ? earning.getPayroll().getStatus().name() : null,
                UserSummary.from(earning.getCreatedBy()));
    }
}
