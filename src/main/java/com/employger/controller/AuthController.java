package com.employger.controller;

import com.employger.dto.LoginRequest;
import com.employger.entity.User;
import com.employger.repository.UserRepository;
import com.employger.service.UserService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
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

                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                loginRequest.getUsername(),
                                                loginRequest.getPassword()));

                if (authentication.isAuthenticated()) {

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
                                .orElseThrow(() -> new RuntimeException("User not found"));

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