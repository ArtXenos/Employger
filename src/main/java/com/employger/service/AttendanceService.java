package com.employger.service;

import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            EmployeeRepository employeeRepository) {

        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<Attendance> getAllAttendance() {
        return attendanceRepository.findAll();
    }

    public Optional<Attendance> getAttendanceById(Integer id) {
        return attendanceRepository.findById(id);
    }

    public List<Attendance> getAttendanceByEmployee(Integer employeeId) {
        return attendanceRepository
                .findByEmployeeEmployeeId(employeeId);
    }

    public Attendance createAttendance(
            Integer employeeId,
            Attendance attendanceDetails) {

        Employee employee = employeeRepository
                .findById(employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

        // Never trust a client-supplied ID: save() would otherwise
        // overwrite the existing attendance record with that ID.
        attendanceDetails.setAttendanceId(null);
        attendanceDetails.setEmployee(employee);

        return attendanceRepository.save(attendanceDetails);
    }

    public Attendance updateAttendance(
            Integer id,
            Integer employeeId,
            Attendance attendanceDetails) {

        Attendance attendance = attendanceRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Attendance not found"));

        Employee employee = employeeRepository
                .findById(employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

        attendance.setEmployee(employee);
        attendance.setAttendanceDate(
                attendanceDetails.getAttendanceDate());
        attendance.setTimeIn(
                attendanceDetails.getTimeIn());
        attendance.setTimeOut(
                attendanceDetails.getTimeOut());
        attendance.setRegularHours(
                attendanceDetails.getRegularHours());
        attendance.setStatus(
                attendanceDetails.getStatus());
        attendance.setRemarks(
                attendanceDetails.getRemarks());

        return attendanceRepository.save(attendance);
    }

    public void deleteAttendance(Integer id) {

        if (!attendanceRepository.existsById(id)) {
            throw new RuntimeException("Attendance not found");
        }

        attendanceRepository.deleteById(id);
    }
}