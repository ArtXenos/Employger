package com.employger.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What the kiosk screen shows after a scan: the name, so whoever watches the
 * entrance can see that it matches the face, and what was recorded. Nothing
 * else about the employee leaves the server.
 */
public record KioskScanResponse(
        String employeeName,
        String direction,
        LocalDate attendanceDate,
        LocalTime time,
        String status,
        String message) {
}
