package com.employger.dto;

import com.employger.entity.SalaryChange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One salary change as returned by /api/employees/{id}/salary-history. */
public record SalaryChangeResponse(
        Integer salaryChangeId,
        BigDecimal previousSalary,
        BigDecimal newSalary,
        LocalDate effectiveDate,
        String reason,
        UserSummary changedBy,
        LocalDateTime createdAt) {

    public static SalaryChangeResponse from(SalaryChange change) {
        return new SalaryChangeResponse(
                change.getSalaryChangeId(),
                change.getPreviousSalary(),
                change.getNewSalary(),
                change.getEffectiveDate(),
                change.getReason(),
                UserSummary.from(change.getChangedBy()),
                change.getCreatedAt());
    }
}
