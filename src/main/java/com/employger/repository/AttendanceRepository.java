package com.employger.repository;

import com.employger.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository
        extends JpaRepository<Attendance, Integer> {

    List<Attendance> findByEmployeeEmployeeId(Integer employeeId);

    Optional<Attendance> findByEmployeeEmployeeIdAndAttendanceDate(
            Integer employeeId,
            LocalDate attendanceDate
    );
}