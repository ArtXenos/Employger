package com.employger.repository;

import com.employger.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndUserIdNot(String username, Integer userId);

    boolean existsByEmployeeEmployeeId(Integer employeeId);

    boolean existsByEmployeeEmployeeIdAndUserIdNot(Integer employeeId, Integer userId);

    long countByRoleAndStatus(User.Role role, User.Status status);
}