package com.employger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body of POST and PUT /api/payroll-adjustments. The employee comes from the
 * corrected payroll, and the payroll that settles it is set by the server.
 */
public record PayrollAdjustmentRequest(

        @NotNull(message = "Choose underpayment or overpayment.")
        Kind kind,

        @NotNull(message = "Choose the paid payroll to correct.")
        Integer correctedPayrollId,

        @NotNull(message = "Amount is required.")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
        @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places.")
        BigDecimal amount,

        @NotBlank(message = "Give the reason for the correction.")
        @Size(max = 255, message = "Reason must be at most 255 characters.")
        String reason,

        @NotNull(message = "Choose the date of the pay cycle that settles it.")
        LocalDate applyDate,

        boolean employeeConsent) {

    public enum Kind {
        UNDERPAYMENT,
        OVERPAYMENT
    }
}
