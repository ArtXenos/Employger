package com.employger.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of PUT /api/settings/{name}: only the value can change. The
 * value's format is checked per setting in SystemSettingService.
 */
public record SettingValueRequest(

        @NotNull(message = "A setting value is required.")
        @Size(max = 255, message = "A setting value must be at most 255 characters.")
        String settingValue) {
}
