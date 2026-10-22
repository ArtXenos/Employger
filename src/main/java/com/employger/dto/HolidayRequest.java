package com.employger.dto;

import com.employger.entity.Holiday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HolidayRequest(

        @NotNull(message = "Holiday date is required.")
        LocalDate holidayDate,

        @NotBlank(message = "Holiday name is required.")
        @Size(max = 100, message = "Holiday name must be at most 100 characters.")
        String name,

        @NotNull(message = "Holiday type is required.")
        Holiday.Type holidayType) {
}
