package com.employger.dto;

import com.employger.entity.Earning;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EarningRequest(

        @NotNull(message = "Employee is required.")
        Integer employeeId,

        @NotNull(message = "Earning type is required.")
        Earning.Type earningType,

        @NotNull(message = "Amount is required.")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
        @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places.")
        BigDecimal amount,

        @NotNull(message = "Date is required.")
        LocalDate dateGiven,

        @Size(max = 255, message = "Description must be at most 255 characters.")
        String description) {
}
