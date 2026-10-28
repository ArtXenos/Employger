package com.employger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The name of a new or renamed department or job title. */
public record ChoiceNameRequest(

        @NotBlank(message = "Enter a name.")
        @Size(max = 100, message = "A name can be at most 100 characters.")
        String name) {
}
