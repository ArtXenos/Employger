package com.employger.service;

import com.employger.dto.CreateUserRequest;
import com.employger.entity.Employee;
import com.employger.entity.User;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.employger.dto.UpdateUserRequest;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
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

    public User createUser(CreateUserRequest request) {

        User user = new User();

        user.setUsername(request.getUsername());

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword()));

        user.setRole(request.getRole());

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        } else {
            user.setStatus(User.Status.ACTIVE);
        }

        if (request.getEmployeeId() != null) {

            Employee employee = employeeRepository
                    .findById(request.getEmployeeId())
                    .orElseThrow(() -> new RuntimeException(
                            "Employee not found"));

            user.setEmployee(employee);
        }

        return userRepository.save(user);
    }

    public User updateUser(Integer id, UpdateUserRequest request) {

    User user = userRepository.findById(id)
            .orElseThrow(() ->
                    new RuntimeException("User not found")
            );

    if (request.getEmployeeId() != null) {

        Employee employee = employeeRepository
                .findById(request.getEmployeeId())
                .orElseThrow(() ->
                        new RuntimeException("Employee not found")
                );

        user.setEmployee(employee);

    } else {

        user.setEmployee(null);
    }

    user.setUsername(request.getUsername());

    if (request.getPassword() != null &&
            !request.getPassword().isBlank()) {

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );
    }

    user.setRole(request.getRole());
    user.setStatus(request.getStatus());

    return userRepository.save(user);
}

    public void deleteUser(Integer id) {

        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found");
        }

        userRepository.deleteById(id);
    }

    public void updateLastLogin(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setLastLogin(java.time.LocalDateTime.now());

        userRepository.save(user);
    }
}