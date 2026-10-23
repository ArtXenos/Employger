package com.employger.repository;

import com.employger.entity.Overtime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OvertimeRepository
        extends JpaRepository<Overtime, Integer> {

    List<Overtime> findByEmployeeEmployeeId(Integer employeeId);

    long countByStatus(Overtime.Status status);

    List<Overtime> findByAttendanceAttendanceId(Integer attendanceId);

    List<Overtime> findByPayrollPayrollId(Integer payrollId);

    List<Overtime> findByEmployeeEmployeeIdAndOvertimeDate(
        Integer employeeId,
        LocalDate overtimeDate);

    List<Overtime> findByEmployeeEmployeeIdAndOvertimeDateBetweenAndStatus(
        Integer employeeId,
        LocalDate startDate,
        LocalDate endDate,
        Overtime.Status status);
}