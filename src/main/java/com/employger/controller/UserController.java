package com.employger.controller;

import com.employger.exception.ResourceNotFoundException;
import com.employger.dto.CreateUserRequest;
import com.employger.dto.UserResponse;
import com.employger.service.UserService;
import com.employger.service.password.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.employger.dto.UpdateUserRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Errors (400 invalid input, 404 not found, 409 conflict) are turned into
// plain-text responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public UserController(UserService userService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {

        return userService.getAllUsers()
                .stream()
                .map(UserResponse::new)
                .toList();
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Integer id) {

        return userService.getUserById(id)
                .map(UserResponse::new)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @PostMapping
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return new UserResponse(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateUserRequest request) {

        return new UserResponse(userService.updateUser(id, request));
    }

    // ---------- forgot password ----------

    public record PasswordResetRequestResponse(
            Integer requestId,
            Integer userId,
            String username,
            String role,
            String employeeName,
            LocalDateTime requestedAt,
            String requestedFrom) {
    }

    public record TemporaryPasswordResponse(String username, String temporaryPassword) {
    }

    @GetMapping("/password-resets")
    public List<PasswordResetRequestResponse> pendingPasswordResets() {

        return passwordResetService.pendingRequests().stream()
                .map(request -> new PasswordResetRequestResponse(
                        request.getRequestId(),
                        request.getUser().getUserId(),
                        request.getUser().getUsername(),
                        request.getUser().getRole().name(),
                        request.getUser().getEmployee() == null ? null
                                : request.getUser().getEmployee().getFirstName() + " "
                                        + request.getUser().getEmployee().getLastName(),
                        request.getRequestedAt(),
                        request.getRequestedFrom()))
                .toList();
    }

    // For the badge on the Users link.
    @GetMapping("/password-resets/count")
    public Map<String, Long> pendingPasswordResetCount() {
        return Map.of("pending", passwordResetService.pendingCount());
    }

    @PostMapping("/password-resets/{requestId}/dismiss")
    public ResponseEntity<Void> dismissPasswordReset(@PathVariable Integer requestId) {
        passwordResetService.dismiss(requestId);
        return ResponseEntity.noContent().build();
    }

    // The temporary password is in this response only; it isn't stored.
    @PostMapping("/{id}/temporary-password")
    public TemporaryPasswordResponse setTemporaryPassword(@PathVariable Integer id) {
        var password = passwordResetService.setTemporaryPassword(id);
        String username = userService.getUserById(id).map(user -> user.getUsername()).orElse(null);
        return new TemporaryPasswordResponse(username, password.value());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Integer id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
