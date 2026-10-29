package com.employger.repository;

import com.employger.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    Optional<Employee> findByBadgeCode(String badgeCode);

    boolean existsByBadgeCode(String badgeCode);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndEmployeeIdNot(String email, Integer employeeId);
}