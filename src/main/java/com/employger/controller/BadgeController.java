package com.employger.controller;

import com.employger.dto.BadgeCardResponse;
import com.employger.service.kiosk.BadgeService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Attendance badges. Administrators and HR managers only (SecurityConfig):
// the code on a badge is enough to clock in as that employee.
@RestController
@RequestMapping("/api/employees/{id}/badge")
public class BadgeController {

    private final BadgeService badgeService;

    public BadgeController(BadgeService badgeService) {
        this.badgeService = badgeService;
    }

    // What the printable card shows; badgeCode is null before the first issue.
    @GetMapping
    public BadgeCardResponse getBadge(@PathVariable Integer id) {
        return BadgeCardResponse.from(badgeService.getEmployee(id));
    }

    // Issues a badge, or replaces the current one (the old card stops working).
    @PostMapping
    public BadgeCardResponse issueBadge(@PathVariable Integer id) {
        return BadgeCardResponse.from(badgeService.issueBadge(id));
    }

    @GetMapping(value = "/qr.svg", produces = "image/svg+xml")
    public ResponseEntity<String> qrCode(@PathVariable Integer id) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .cacheControl(CacheControl.noStore())
                .body(badgeService.qrSvg(id));
    }
}
