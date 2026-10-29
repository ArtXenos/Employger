package com.employger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A scan at the kiosk: only the text read from the badge. No time and no
 * employee: the server takes both from its own clock and the badge code.
 */
public record KioskScanRequest(

        @NotBlank(message = "Scan a badge or type its code.")
        @Size(max = 100, message = "That is not a badge code.")
        String badgeCode) {
}
