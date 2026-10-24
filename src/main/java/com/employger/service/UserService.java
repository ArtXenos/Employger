package com.employger.service;

import com.employger.exception.ResourceNotFoundException;
import com.employger.dto.ChangePasswordRequest;
import com.employger.dto.CreateUserRequest;
import com.employger.entity.Employee;
import com.employger.entity.User;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.employger.dto.UpdateUserRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserService {

    static final int MIN_PASSWORD_LENGTH = 12;

    // BCrypt ignores everything after 72 bytes, so a longer password
    // would silently accept any text that shares its first 72 bytes.
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {

        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Integer id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User createUser(CreateUserRequest request) {

        String username = request.getUsername().trim();

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("This username is already taken.");
        }

        checkPasswordStrength(request.getPassword());

        if (userRepository.existsByEmployeeEmployeeId(request.getEmployeeId())) {
            throw new IllegalStateException("This employee already has a login.");
        }

        User.Status status = request.getStatus() != null ? request.getStatus() : User.Status.ACTIVE;
        Employee employee = findEmployee(request.getEmployeeId());
        checkStillEmployed(employee, status);

        User user = new User();

        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setStatus(status);
        user.setEmployee(employee);

        User saved = userRepository.save(user);

        auditService.record("USER_CREATED",
                "Created user #" + saved.getUserId() + " '" + saved.getUsername() + "' with role "
                        + saved.getRole() + ", status " + saved.getStatus()
                        + ", employee " + employeeIdOf(saved));

        return saved;
    }

    @Transactional
    public User updateUser(Integer id, UpdateUserRequest request) {

        // Before the change: renaming your own account would otherwise leave
        // the audit entry without an actor.
        User actor = auditService.currentUser().orElse(null);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String username = request.getUsername().trim();

        if (userRepository.existsByUsernameIgnoreCaseAndUserIdNot(username, id)) {
            throw new IllegalStateException("This username is already taken.");
        }

        boolean losesAdministrator = isActiveAdministrator(user)
                && (request.getRole() != User.Role.ADMINISTRATOR
                        || request.getStatus() != User.Status.ACTIVE);

        if (losesAdministrator && isLastActiveAdministrator()) {
            throw new IllegalStateException(
                    "This is the only active administrator. Make another user an active "
                            + "administrator first, so nobody is locked out of user management.");
        }

        if (userRepository.existsByEmployeeEmployeeIdAndUserIdNot(request.getEmployeeId(), id)) {
            throw new IllegalStateException("This employee already has a login.");
        }

        Employee employee = findEmployee(request.getEmployeeId());
        checkStillEmployed(employee, request.getStatus());

        boolean changesPassword = request.getPassword() != null && !request.getPassword().isBlank();

        if (changesPassword) {
            checkPasswordStrength(request.getPassword());
        }

        List<String> changes = new ArrayList<>();
        noteChange(changes, "username", user.getUsername(), username);
        noteChange(changes, "role", user.getRole(), request.getRole());
        noteChange(changes, "status", user.getStatus(), request.getStatus());
        noteChange(changes, "employee", employeeIdOf(user), employee.getEmployeeId());

        user.setUsername(username);
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        user.setEmployee(employee);

        if (changesPassword) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            changes.add("password changed");
        }

        if (!changes.isEmpty()) {
            auditService.record("USER_UPDATED",
                    "User #" + id + ": " + String.join(", ", changes), actor);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Integer id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isSelf = auditService.currentUser()
                .map(current -> current.getUserId().equals(id))
                .orElse(false);

        if (isSelf) {
            throw new IllegalStateException("You cannot delete your own account.");
        }

        if (isActiveAdministrator(user) && isLastActiveAdministrator()) {
            throw new IllegalStateException("The only active administrator cannot be deleted.");
        }

        // Written before the delete: audit_logs.user_id of this user's past
        // entries becomes NULL, so this entry keeps the name on record.
        auditService.record("USER_DELETED",
                "Deleted user #" + id + " '" + user.getUsername() + "' with role " + user.getRole());

        userRepository.delete(user);
    }

    @Transactional
    public void changeOwnPassword(String username, ChangePasswordRequest request) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Your current password is incorrect.");
        }

        if (request.newPassword().equals(request.currentPassword())) {
            throw new IllegalArgumentException("Choose a new password that is different from your current one.");
        }

        checkPasswordStrength(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        auditService.record("PASSWORD_CHANGED",
                "User #" + user.getUserId() + " '" + user.getUsername() + "' changed their own password", user);
    }

    public User updateLastLogin(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setLastLogin(java.time.LocalDateTime.now());

        return userRepository.save(user);
    }

    static void checkPasswordStrength(String password) {

        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "Password is too long: at most " + MAX_PASSWORD_BYTES
                            + " bytes (72 plain letters or digits).");
        }

        if (password.isBlank() || !password.equals(password.strip())) {
            throw new IllegalArgumentException("Password cannot start or end with a space.");
        }
    }

    // A former employee's login may be kept, but only as INACTIVE.
    private static void checkStillEmployed(Employee employee, User.Status status) {
        if (status == User.Status.ACTIVE && !employee.isEmployed()) {
            throw new IllegalArgumentException(
                    "This employee no longer works for the company. Their login can only be inactive.");
        }
    }

    private Employee findEmployee(Integer employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private static boolean isActiveAdministrator(User user) {
        return user.getRole() == User.Role.ADMINISTRATOR && user.getStatus() == User.Status.ACTIVE;
    }

    private boolean isLastActiveAdministrator() {
        return userRepository.countByRoleAndStatus(User.Role.ADMINISTRATOR, User.Status.ACTIVE) <= 1;
    }

    private static Integer employeeIdOf(User user) {
        return user.getEmployee() != null ? user.getEmployee().getEmployeeId() : null;
    }

    private static void noteChange(List<String> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(field + " " + before + " -> " + after);
        }
    }
}
