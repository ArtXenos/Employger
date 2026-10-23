package com.employger.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An employee filing their own overtime: only the day and the hours. The
 * employee comes from the login; the attendance record, rate, amount and
 * status are set by the server.
 */
public record OvertimeFilingRequest(
        @NotNull(message = "Overtime date is required.")
        LocalDate date,

        @NotNull(message = "Overtime hours are required.")
        @DecimalMin(value = "0", inclusive = false, message = "Overtime hours must be greater than zero.")
        @DecimalMax(value = "24", message = "Overtime hours cannot exceed 24 hours.")
        BigDecimal hours) {
}
