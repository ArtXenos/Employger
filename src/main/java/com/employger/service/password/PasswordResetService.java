package com.employger.service.password;

import com.employger.entity.PasswordResetRequest;
import com.employger.entity.User;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.PasswordResetRequestRepository;
import com.employger.repository.UserRepository;
import com.employger.service.AuditService;
import com.employger.service.LoginAttemptService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Forgot password: the request from the login page, and what an
 * administrator does with it. The administrator sets a temporary password
 * and gives it to the person in person; the person must then choose their
 * own (User.isMustChangePassword, enforced by PasswordChangeRequiredFilter).
 */
@Service
public class PasswordResetService {

    private final PasswordResetRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;

    public PasswordResetService(
            PasswordResetRequestRepository requestRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            LoginAttemptService loginAttemptService) {

        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Files a request for an active login that has none waiting. Unknown,
     * inactive and already-requested usernames are silently ignored, and
     * the caller answers the same in every case, so usernames can't be
     * probed and one person can't flood the administrators' list.
     */
    @Transactional
    public void requestReset(String username, String address) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Enter your username.");
        }

        userRepository.findByUsername(username.trim())
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .filter(user -> !requestRepository.existsByUserUserIdAndStatus(
                        user.getUserId(), PasswordResetRequest.Status.PENDING))
                .ifPresent(user -> {
                    PasswordResetRequest request = requestRepository.save(
                            PasswordResetRequest.open(user, address, LocalDateTime.now()));

                    auditService.record("PASSWORD_RESET_REQUESTED",
                            "Password reset request #" + request.getRequestId() + " for user #"
                                    + user.getUserId() + " '" + user.getUsername() + "' from address "
                                    + request.getRequestedFrom(),
                            null);
                });
    }

    public List<PasswordResetRequest> pendingRequests() {
        return requestRepository.findByStatusOrderByRequestedAtAscRequestIdAsc(PasswordResetRequest.Status.PENDING);
    }

    public long pendingCount() {
        return requestRepository.countByStatus(PasswordResetRequest.Status.PENDING);
    }

    /**
     * Gives the login a new temporary password and closes its waiting
     * requests. The password is returned once, for the administrator to
     * pass on; only its hash is stored, and the audit entry doesn't hold it.
     */
    @Transactional
    public TemporaryPassword setTemporaryPassword(Integer userId) {

        User administrator = currentAdministrator();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getUserId().equals(administrator.getUserId())) {
            throw new IllegalStateException("Use Change password for your own account.");
        }

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new IllegalStateException(
                    "This login is inactive. Make it active first; an inactive login can't log in.");
        }

        TemporaryPassword password = TemporaryPassword.generate();
        LocalDateTime now = LocalDateTime.now();

        user.setTemporaryPassword(passwordEncoder.encode(password.value()));
        userRepository.save(user);

        List<PasswordResetRequest> answered = requestRepository.findByUserUserIdAndStatus(
                userId, PasswordResetRequest.Status.PENDING);
        answered.forEach(request -> request.complete(administrator, now));
        requestRepository.saveAll(answered);

        // A lockout from the user's failed guesses would otherwise keep
        // them out for up to 15 more minutes.
        loginAttemptService.recordSuccess(user.getUsername());

        auditService.record("PASSWORD_RESET",
                "Set a temporary password for user #" + userId + " '" + user.getUsername()
                        + "'; they must choose a new one at their next login"
                        + (answered.isEmpty() ? "" : " (request #" + answered.get(0).getRequestId() + ")"),
                administrator);

        return password;
    }

    @Transactional
    public void dismiss(Integer requestId) {

        User administrator = currentAdministrator();
        PasswordResetRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Password reset request not found"));

        request.dismiss(administrator, LocalDateTime.now());
        requestRepository.save(request);

        auditService.record("PASSWORD_RESET_DISMISSED",
                "Dismissed password reset request #" + requestId + " for user #"
                        + request.getUser().getUserId() + " '" + request.getUser().getUsername() + "'",
                administrator);
    }

    private User currentAdministrator() {
        return auditService.currentUser()
                .orElseThrow(() -> new IllegalStateException("Log in again to continue."));
    }
}
