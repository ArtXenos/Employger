package com.employger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Figures copied from the previous employer's BIR Form 2316 for the same year. */
public record PriorEmployerRequest(

        @NotNull(message = "Employee is required.")
        Integer employeeId,

        @NotNull(message = "Tax year is required.")
        @Min(value = 2000, message = "Tax year must be between 2000 and 2100.")
        @Max(value = 2100, message = "Tax year must be between 2000 and 2100.")
        Integer taxYear,

        @NotBlank(message = "Previous employer's name is required.")
        @Size(max = 150, message = "Employer name must be at most 150 characters.")
        String employerName,

        @Size(max = 20, message = "Employer TIN must be at most 20 characters.")
        String employerTin,

        @NotNull(message = "Taxable compensation is required (0 if none).")
        @DecimalMin(value = "0.00", message = "Amounts cannot be negative.")
        @Digits(integer = 10, fraction = 2, message = "Amounts must have at most 2 decimal places.")
        BigDecimal taxableCompensation,

        @NotNull(message = "Non-taxable 13th month and other benefits are required (0 if none).")
        @DecimalMin(value = "0.00", message = "Amounts cannot be negative.")
        @Digits(integer = 10, fraction = 2, message = "Amounts must have at most 2 decimal places.")
        BigDecimal exemptBenefits,

        @NotNull(message = "Tax withheld is required (0 if none).")
        @DecimalMin(value = "0.00", message = "Amounts cannot be negative.")
        @Digits(integer = 10, fraction = 2, message = "Amounts must have at most 2 decimal places.")
        BigDecimal taxWithheld) {
}
