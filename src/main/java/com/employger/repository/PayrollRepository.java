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

    boolean existsByPayCyclePayCycleId(Integer payCycleId);

    // Payroll for the employee whose pay cycle shares at least one day
    // with the range [startDate, endDate].
    List<Payroll> findByEmployeeEmployeeIdAndPayCycleStartDateLessThanEqualAndPayCycleEndDateGreaterThanEqual(
            Integer employeeId,
            LocalDate endDate,
            LocalDate startDate
    );

    // A day is locked for an employee once a PROCESSED or PAID payroll
    // covers it: its attendance and overtime are part of a final payroll.
    default boolean isDateLockedForEmployee(Integer employeeId, LocalDate date) {
        return findByEmployeeEmployeeIdAndPayCycleStartDateLessThanEqualAndPayCycleEndDateGreaterThanEqual(
                employeeId, date, date)
                .stream()
                .anyMatch(payroll -> payroll.getStatus() != Payroll.Status.DRAFT);
    }
}