package com.employger.service;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayrollRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceService {

    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("24");

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final OvertimeRepository overtimeRepository;
    private final PayrollRepository payrollRepository;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            EmployeeRepository employeeRepository,
            OvertimeRepository overtimeRepository,
            PayrollRepository payrollRepository) {

        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.overtimeRepository = overtimeRepository;
        this.payrollRepository = payrollRepository;
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

    @Transactional
    public Attendance createAttendance(
            Integer employeeId,
            Attendance attendanceDetails) {

        Employee employee = employeeRepository
                .findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee not found"));

        validateAttendanceDetails(attendanceDetails);
        ensureDateNotLocked(employee, attendanceDetails.getAttendanceDate());

        // Never trust a client-supplied ID: save() would otherwise
        // overwrite the existing attendance record with that ID.
        attendanceDetails.setAttendanceId(null);
        attendanceDetails.setEmployee(employee);

        return attendanceRepository.save(attendanceDetails);
    }

    @Transactional
    public Attendance updateAttendance(
            Integer id,
            Integer employeeId,
            Attendance attendanceDetails) {

        Attendance attendance = attendanceRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Attendance not found"));

        Employee employee = employeeRepository
                .findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee not found"));

        validateAttendanceDetails(attendanceDetails);
        ensureDateNotLocked(attendance.getEmployee(), attendance.getAttendanceDate());
        ensureDateNotLocked(employee, attendanceDetails.getAttendanceDate());

        // Overtime is tied to this record's employee and date and is only
        // allowed on worked days, so those cannot change underneath it.
        if (hasActiveOvertime(attendance)) {

            boolean movedToAnotherDay =
                    !attendance.getEmployee().getEmployeeId().equals(employee.getEmployeeId())
                            || !attendance.getAttendanceDate().equals(attendanceDetails.getAttendanceDate());

            boolean noLongerWorked =
                    attendanceDetails.getStatus() == Attendance.Status.ABSENT
                            || attendanceDetails.getStatus() == Attendance.Status.LEAVE;

            if (movedToAnotherDay || noLongerWorked) {
                throw new IllegalStateException(
                        "This attendance record has pending or approved overtime. "
                                + "Reject or delete that overtime before changing the employee, "
                                + "date, or marking the day ABSENT or LEAVE.");
            }
        }

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

    @Transactional
    public void deleteAttendance(Integer id) {

        Attendance attendance = attendanceRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Attendance not found"));

        ensureDateNotLocked(attendance.getEmployee(), attendance.getAttendanceDate());

        if (hasActiveOvertime(attendance)) {
            throw new IllegalStateException(
                    "This attendance record has pending or approved overtime. "
                            + "Reject or delete that overtime first.");
        }

        attendanceRepository.delete(attendance);
    }

    private void validateAttendanceDetails(Attendance attendance) {

        if (attendance.getAttendanceDate() == null) {
            throw new IllegalArgumentException("Attendance date is required");
        }

        if (attendance.getStatus() == null) {
            throw new IllegalArgumentException("Attendance status is required");
        }

        BigDecimal hours = attendance.getRegularHours();

        if (hours == null) {
            hours = BigDecimal.ZERO;
            attendance.setRegularHours(hours);
        }

        if (hours.signum() < 0 || hours.compareTo(HOURS_PER_DAY) > 0) {
            throw new IllegalArgumentException(
                    "Regular hours must be between 0 and 24");
        }

        if (attendance.getStatus() == Attendance.Status.ABSENT
                && hours.signum() > 0) {

            throw new IllegalArgumentException(
                    "An ABSENT day cannot have worked hours");
        }

        // Time out earlier than time in is allowed: it is an overnight shift.
    }

    private void ensureDateNotLocked(Employee employee, LocalDate date) {

        if (payrollRepository.isDateLockedForEmployee(employee.getEmployeeId(), date)) {
            throw new IllegalStateException(
                    "Payroll covering " + date
                            + " has already been processed or paid for this employee, "
                            + "so attendance for that day can no longer be changed.");
        }
    }

    private boolean hasActiveOvertime(Attendance attendance) {
        return overtimeRepository
                .findByAttendanceAttendanceId(attendance.getAttendanceId())
                .stream()
                .anyMatch(overtime -> overtime.getStatus() != Overtime.Status.REJECTED);
    }
}
