package com.employger.service;

import com.employger.entity.AuditLog;
import com.employger.entity.User;
import com.employger.repository.AuditLogRepository;
import com.employger.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Records payroll-relevant changes in audit_logs. Called from inside the
 * business transaction, so an entry is written only when the change itself
 * is committed.
 */
@Service
public class AuditService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "auditId");

    /** The most rows one CSV export holds. */
    public static final int CSV_LIMIT = 10_000;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository) {

        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void record(String action, String description) {
        record(action, description, currentUser().orElse(null));
    }

    /**
     * For changes that can rename the acting user: look them up before the
     * change, because afterwards the session's username matches nobody.
     */
    public void record(String action, String description, User actor) {

        AuditLog entry = new AuditLog();

        entry.setAction(action);
        entry.setDescription(description);
        entry.setUser(actor);

        auditLogRepository.save(entry);
    }

    public Optional<User> currentUser() {

        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        return userRepository.findByUsername(authentication.getName());
    }

    public List<AuditLog> getRecentEntries() {
        return auditLogRepository.findTop200ByOrderByAuditIdDesc();
    }

    /** One page of matching entries, newest first. */
    public Page<AuditLog> search(AuditLogFilter filter, int page, int size) {
        return auditLogRepository.findAll(filter.toSpecification(),
                PageRequest.of(page, size, NEWEST_FIRST));
    }

    public List<String> actions() {
        return auditLogRepository.findDistinctActions();
    }

    /**
     * Matching entries as CSV, newest first, at most CSV_LIMIT rows. Cells
     * are quoted and protected against spreadsheet formulas.
     */
    public String csv(AuditLogFilter filter) {
        StringBuilder csv = new StringBuilder(ReportService.csvLine("Date and time", "User", "Action", "Description"));

        for (AuditLog entry : auditLogRepository.findAll(filter.toSpecification(),
                PageRequest.of(0, CSV_LIMIT, NEWEST_FIRST))) {
            csv.append(ReportService.csvLine(
                    entry.getCreatedAt().toString().replace('T', ' '),
                    entry.getUser() != null ? entry.getUser().getUsername() : "(system)",
                    entry.getAction(),
                    entry.getDescription()));
        }

        return csv.toString();
    }
}
