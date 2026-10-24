package com.employger.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A logged-in user changing their own password. The current password is
 * required, so a session left open on a shared computer can't be used to
 * take over the account. Strength rules: UserService.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required.")
        String currentPassword,

        @NotBlank(message = "New password is required.")
        String newPassword) {
}
