package com.employger.repository;

import com.employger.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
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

    boolean existsByEmployeeEmployeeId(Integer employeeId);

    // Payroll for the employee whose pay cycle shares at least one day
    // with the range [startDate, endDate].
    List<Payroll> findByEmployeeEmployeeIdAndPayCycleStartDateLessThanEqualAndPayCycleEndDateGreaterThanEqual(
            Integer employeeId,
            LocalDate endDate,
            LocalDate startDate
    );
}