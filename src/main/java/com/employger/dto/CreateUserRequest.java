package com.employger.dto;

import com.employger.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class CreateUserRequest {

    // Every login belongs to someone who works for the company, except the
    // kiosk device's, which has none. Checked in UserService, by role.
    private Integer employeeId;

    @NotBlank(message = "Username is required.")
    @Pattern(regexp = "[A-Za-z0-9._@-]{3,100}",
            message = "Username must be 3 to 100 characters: letters, digits, '.', '_', '-' or '@'.")
    private String username;

    // Strength rules are checked in UserService, shared with password changes.
    @NotBlank(message = "Password is required.")
    private String password;

    @NotNull(message = "Role is required.")
    private User.Role role;

    // Optional: defaults to ACTIVE.
    private User.Status status;

    public Integer getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Integer employeeId) {
        this.employeeId = employeeId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public User.Role getRole() {
        return role;
    }

    public void setRole(User.Role role) {
        this.role = role;
    }

    public User.Status getStatus() {
        return status;
    }

    public void setStatus(User.Status status) {
        this.status = status;
    }
}