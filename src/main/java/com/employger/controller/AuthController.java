package com.employger.controller;

import com.employger.config.ActiveAccountFilter;
import com.employger.exception.ResourceNotFoundException;
import com.employger.dto.LoginRequest;
import com.employger.entity.User;
import com.employger.repository.UserRepository;
import com.employger.service.AuditService;
import com.employger.service.LoginAttemptService;
import com.employger.service.UserService;
import com.employger.service.password.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private static final Logger log = LoggerFactory.getLogger(AuthController.class);

        private final AuthenticationManager authenticationManager;
        private final UserService userService;
        private final UserRepository userRepository;
        private final LoginAttemptService loginAttemptService;
        private final AuditService auditService;
        private final PasswordResetService passwordResetService;

        private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

        public AuthController(
                        AuthenticationManager authenticationManager,
                        UserService userService,
                        UserRepository userRepository,
                        LoginAttemptService loginAttemptService,
                        AuditService auditService,
                        PasswordResetService passwordResetService) {

                this.authenticationManager = authenticationManager;
                this.userService = userService;
                this.userRepository = userRepository;
                this.loginAttemptService = loginAttemptService;
                this.auditService = auditService;
                this.passwordResetService = passwordResetService;
        }

        @PostMapping("/login")
        public ResponseEntity<String> login(
                        @RequestBody LoginRequest loginRequest,
                        HttpServletRequest request) {

                // The direct peer address. X-Forwarded-For is not trusted,
                // because any client can set it to dodge the limit.
                String address = request.getRemoteAddr();
                String username = loginRequest.getUsername();

                // Refuse before checking the password, so a locked account
                // cannot be guessed at all while the lock lasts.
                Optional<LoginAttemptService.Lock> lock = loginAttemptService.checkLocked(username, address);

                if (lock.isPresent()) {
                        return tooManyAttempts(lock.get());
                }

                Authentication authentication;

                try {
                        authentication = authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(
                                                        loginRequest.getUsername(),
                                                        loginRequest.getPassword()));

                } catch (AuthenticationServiceException e) {
                        // The user lookup itself failed, typically because the
                        // database is unreachable. This is not a bad password.
                        log.error("Login could not be processed", e);

                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .body("Login is temporarily unavailable because the database "
                                                        + "cannot be reached. Please try again later.");

                } catch (AuthenticationException e) {
                        log.warn("Failed login for username '{}' from {}", abbreviate(username), address);

                        if (loginAttemptService.recordFailure(username, address)) {
                                recordLockout(username, address);
                        }

                        // One message for unknown users, wrong passwords, and
                        // inactive accounts, so usernames cannot be probed.
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .body("Invalid username or password.");
                }

                if (authentication.isAuthenticated()) {

                        loginAttemptService.recordSuccess(username);

                        // Issue a new session ID on login so a session ID obtained
                        // before authentication cannot be reused (session fixation).
                        if (request.getSession(false) != null) {
                                request.changeSessionId();
                        }

                        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();

                        securityContext.setAuthentication(authentication);

                        SecurityContextHolder.setContext(securityContext);

                        securityContextRepository.saveContext(
                                        securityContext,
                                        request,
                                        null);

                        User user = userService.updateLastLogin(
                                        authentication.getName());

                        // Lets ActiveAccountFilter end this session if the login
                        // is later deactivated, deleted or given another role.
                        request.getSession().setAttribute(
                                        ActiveAccountFilter.USER_ID_ATTRIBUTE,
                                        user.getUserId());

                        return ResponseEntity.ok("Login successful");
                }

                return ResponseEntity.status(401)
                                .body("Login failed");
        }

        /**
         * "Forgot password?" on the login page. The answer is the same whether
         * or not the username exists, so it can't be used to find usernames.
         */
        @PostMapping("/forgot-password")
        public ResponseEntity<String> forgotPassword(
                        @RequestBody ForgotPasswordRequest body,
                        HttpServletRequest request) {

                String username = body.username();

                if (username != null && username.length() > 100) {
                        throw new IllegalArgumentException("Enter your username.");
                }

                passwordResetService.requestReset(username, request.getRemoteAddr());

                return ResponseEntity.ok()
                                .contentType(MediaType.TEXT_PLAIN)
                                .body("Your request has been sent. If this username has an active account, "
                                                + "an administrator will contact you and give you a temporary "
                                                + "password in person.");
        }

        public record ForgotPasswordRequest(String username) {
        }

        private static ResponseEntity<String> tooManyAttempts(LoginAttemptService.Lock lock) {

                long minutes = lock.minutesRemaining();

                // Same message for username and address locks, and for
                // usernames that do not exist, so nothing can be probed.
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .header("Retry-After", String.valueOf(lock.remaining().toSeconds()))
                                .contentType(MediaType.TEXT_PLAIN)
                                .body("Too many failed login attempts. Please try again in "
                                                + minutes + (minutes == 1 ? " minute." : " minutes."));
        }

        private void recordLockout(String username, String address) {

                String description = "Login locked for " + LoginAttemptService.LOCK_DURATION.toMinutes()
                                + " minutes after repeated failed attempts "
                                + "(username '" + abbreviate(username) + "', address " + address + ")";

                log.warn(description);

                // The lock already applies; failing to write the audit entry
                // (for example, database down) must not change the response.
                try {
                        auditService.record("LOGIN_LOCKED", description);
                } catch (RuntimeException e) {
                        log.error("Could not write LOGIN_LOCKED audit entry", e);
                }
        }

        // The username is typed by the client; keep log and audit lines short.
        private static String abbreviate(String username) {

                if (username == null) {
                        return "";
                }

                String clean = username.replaceAll("\\p{Cntrl}", "?");

                return clean.length() > 50 ? clean.substring(0, 50) + "..." : clean;
        }

        /**
         * Makes sure the XSRF-TOKEN cookie is set. csrf.js calls this before
         * the first write request on a page that has no token cookie yet.
         */
        @GetMapping("/csrf")
        public ResponseEntity<Void> csrf(CsrfToken csrfToken) {

                // Reading the token generates it and writes the cookie.
                csrfToken.getToken();

                return ResponseEntity.noContent().build();
        }

        @GetMapping("/me")
        public ResponseEntity<?> getCurrentUser(
                        Authentication authentication) {

                if (authentication == null ||
                                !authentication.isAuthenticated()) {

                        return ResponseEntity.status(401)
                                        .body("Not authenticated");
                }

                User user = userRepository
                                .findByUsername(authentication.getName())
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                Map<String, Object> response = new LinkedHashMap<>();

                response.put("userId", user.getUserId());
                response.put("username", user.getUsername());
                response.put("role", user.getRole().name());
                response.put("mustChangePassword", user.isMustChangePassword());

                return ResponseEntity.ok(response);
        }

        @PostMapping("/logout")
        public ResponseEntity<String> logout(
                        HttpServletRequest request) {

                request.getSession().invalidate();

                SecurityContextHolder.clearContext();

                return ResponseEntity.ok("Logout successful");
        }
}