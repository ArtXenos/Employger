package com.employger.controller;

import com.employger.entity.AuditLog;
import com.employger.service.AuditLogFilter;
import com.employger.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Administrators only (SecurityConfig). Read-only: there is no endpoint to
// change or delete an entry, which is what makes the trail trustworthy.
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    // The 200 most recent entries, newest first. Read-only.
    @GetMapping
    public List<AuditLogResponse> getRecentEntries() {
        return auditService.getRecentEntries()
                .stream()
                .map(AuditLogResponse::new)
                .toList();
    }

    // One page of entries matching the filters, newest first (page starts at 0).
    @GetMapping("/search")
    public AuditLogPage search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page must be 0 or more and size 1 to " + MAX_PAGE_SIZE + ".");
        }

        Page<AuditLog> result = auditService.search(new AuditLogFilter(from, to, username, action, text), page, size);

        return new AuditLogPage(
                result.getContent().stream().map(AuditLogResponse::new).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    // Every action recorded so far, for the filter list.
    @GetMapping("/actions")
    public List<String> actions() {
        return auditService.actions();
    }

    // The same filters as /search, as a CSV file (newest first, at most 10,000 rows).
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String text) {

        String csv = auditService.csv(new AuditLogFilter(from, to, username, action, text));
        // UTF-8 with a byte order mark so spreadsheet programs show ñ and ₱.
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"audit-log-" + LocalDate.now() + ".csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    public record AuditLogPage(
            List<AuditLogResponse> entries,
            int page,
            int size,
            long totalEntries,
            int totalPages) {
    }

    public record AuditLogResponse(
            Integer auditId,
            String username,
            String action,
            String description,
            LocalDateTime createdAt) {

        AuditLogResponse(AuditLog entry) {
            this(
                    entry.getAuditId(),
                    entry.getUser() != null ? entry.getUser().getUsername() : null,
                    entry.getAction(),
                    entry.getDescription(),
                    entry.getCreatedAt());
        }
    }
}
