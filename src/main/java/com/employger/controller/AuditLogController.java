package com.employger.controller;

import com.employger.entity.AuditLog;
import com.employger.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

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
