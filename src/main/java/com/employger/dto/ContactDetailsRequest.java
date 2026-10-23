package com.employger.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The only employee fields an employee may change themselves (self-service).
 * Same limits as EmployeeRequest. Name, salary, department, government
 * numbers and status stay HR-only.
 */
public record ContactDetailsRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email is not a valid email address.")
        @Size(max = 150, message = "Email must be at most 150 characters.")
        String email,

        @Size(max = 30, message = "Phone must be at most 30 characters.")
        String phone,

        @Size(max = 255, message = "Address must be at most 255 characters.")
        String address) {
}
