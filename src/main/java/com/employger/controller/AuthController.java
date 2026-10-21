package com.employger.controller;

import com.employger.exception.ResourceNotFoundException;
import com.employger.dto.LoginRequest;
import com.employger.entity.User;
import com.employger.repository.UserRepository;
import com.employger.service.UserService;

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
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private static final Logger log = LoggerFactory.getLogger(AuthController.class);

        private final AuthenticationManager authenticationManager;
        private final UserService userService;
        private final UserRepository userRepository;

        private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

        public AuthController(
                        AuthenticationManager authenticationManager,
                        UserService userService,
                        UserRepository userRepository) {

                this.authenticationManager = authenticationManager;
                this.userService = userService;
                this.userRepository = userRepository;
        }

        @PostMapping("/login")
        public ResponseEntity<String> login(
                        @RequestBody LoginRequest loginRequest,
                        HttpServletRequest request) {

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
                        // One message for unknown users, wrong passwords, and
                        // inactive accounts, so usernames cannot be probed.
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .body("Invalid username or password.");
                }

                if (authentication.isAuthenticated()) {

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

                        userService.updateLastLogin(
                                        authentication.getName());

                        return ResponseEntity.ok("Login successful");
                }

                return ResponseEntity.status(401)
                                .body("Login failed");
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