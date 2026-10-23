package com.employger.service;

import com.employger.entity.AuditLog;
import com.employger.entity.User;
import com.employger.repository.AuditLogRepository;
import com.employger.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
}
