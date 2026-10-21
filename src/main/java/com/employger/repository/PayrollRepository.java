package com.employger.repository;

import com.employger.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRepository
        extends JpaRepository<Payroll, Integer> {

    List<Payroll> findByEmployeeEmployeeId(Integer employeeId);

    List<Payroll> findByPayCyclePayCycleId(Integer payCycleId);

    Optional<Payroll> findByEmployeeEmployeeIdAndPayCyclePayCycleId(
            Integer employeeId,
            Integer payCycleId
    );
}