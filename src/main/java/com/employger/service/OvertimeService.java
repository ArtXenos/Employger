package com.employger.service;

import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.entity.User;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class OvertimeService {

        private static final BigDecimal ZERO = BigDecimal.ZERO;

        private final OvertimeRepository overtimeRepository;
        private final EmployeeRepository employeeRepository;
        private final AttendanceRepository attendanceRepository;
        private final UserRepository userRepository;
        private final PayrollCalculationService payrollCalculationService;
        private final SystemSettingService systemSettingService;

        public OvertimeService(
                        OvertimeRepository overtimeRepository,
                        EmployeeRepository employeeRepository,
                        AttendanceRepository attendanceRepository,
                        UserRepository userRepository,
                        PayrollCalculationService payrollCalculationService,
                        SystemSettingService systemSettingService) {

                this.overtimeRepository = overtimeRepository;
                this.employeeRepository = employeeRepository;
                this.attendanceRepository = attendanceRepository;
                this.userRepository = userRepository;
                this.payrollCalculationService = payrollCalculationService;
                this.systemSettingService = systemSettingService;
        }

        public List<Overtime> getAllOvertime() {
                return overtimeRepository.findAll();
        }

        public Optional<Overtime> getOvertimeById(Integer id) {
                return overtimeRepository.findById(id);
        }

        public List<Overtime> getOvertimeByEmployee(
                        Integer employeeId) {

                return overtimeRepository
                                .findByEmployeeEmployeeId(employeeId);
        }

        public Overtime createOvertime(
                        Integer employeeId,
                        Overtime overtimeDetails) {

                Employee employee = employeeRepository
                                .findById(employeeId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Employee not found"));

                validateOvertimeDetails(overtimeDetails);

                overtimeDetails.setEmployee(employee);

                Attendance attendance = resolveAttendance(
                                employee,
                                overtimeDetails.getAttendance(),
                                overtimeDetails.getOvertimeDate());

                overtimeDetails.setAttendance(attendance);

                calculateOvertimeValues(
                                employee,
                                overtimeDetails);

                overtimeDetails.setStatus(
                                Overtime.Status.PENDING);

                return overtimeRepository.save(overtimeDetails);
        }

        public Overtime updateOvertime(
                        Integer id,
                        Integer employeeId,
                        Overtime overtimeDetails) {

                Overtime overtime = overtimeRepository
                                .findById(id)
                                .orElseThrow(() -> new RuntimeException(
                                                "Overtime not found"));

                if (overtime.getStatus() != Overtime.Status.PENDING) {
                        throw new IllegalArgumentException(
                                        "Only pending overtime can be edited");
                }

                Employee employee = employeeRepository
                                .findById(employeeId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Employee not found"));

                validateOvertimeDetails(overtimeDetails);

                overtime.setEmployee(employee);

                Attendance attendance = resolveAttendance(
                                employee,
                                overtimeDetails.getAttendance(),
                                overtimeDetails.getOvertimeDate());

                overtime.setAttendance(attendance);

                overtime.setOvertimeDate(
                                overtimeDetails.getOvertimeDate());

                overtime.setHours(
                                overtimeDetails.getHours());

                calculateOvertimeValues(
                                employee,
                                overtime);

                return overtimeRepository.save(overtime);
        }

        public Overtime updateOvertimeStatus(
                        Integer overtimeId,
                        Overtime.Status newStatus,
                        String username) {

                Overtime overtime = overtimeRepository
                                .findById(overtimeId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Overtime not found"));

                if (overtime.getStatus() != Overtime.Status.PENDING) {
                        throw new IllegalArgumentException(
                                        "Only pending overtime can be approved or rejected");
                }

                if (newStatus != Overtime.Status.APPROVED
                                && newStatus != Overtime.Status.REJECTED) {

                        throw new IllegalArgumentException(
                                        "Invalid overtime status");
                }

                User user = userRepository
                                .findByUsername(username)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

                overtime.setStatus(newStatus);
                overtime.setApprovedBy(user);

                return overtimeRepository.save(overtime);
        }

        public void deleteOvertime(Integer id) {

                if (!overtimeRepository.existsById(id)) {
                        throw new RuntimeException(
                                        "Overtime not found");
                }

                overtimeRepository.deleteById(id);
        }

        private void validateOvertimeDetails(
                        Overtime overtime) {

                if (overtime.getOvertimeDate() == null) {
                        throw new IllegalArgumentException(
                                        "Overtime date is required");
                }

                if (overtime.getHours() == null
                                || overtime.getHours()
                                                .compareTo(ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Overtime hours must be greater than zero");
                }

                if (overtime.getHours()
                                .compareTo(new BigDecimal("24.00")) > 0) {

                        throw new IllegalArgumentException(
                                        "Overtime hours cannot exceed 24 hours");
                }
        }

        private Attendance resolveAttendance(
                        Employee employee,
                        Attendance attendanceDetails,
                        java.time.LocalDate overtimeDate) {

                if (attendanceDetails == null
                                || attendanceDetails.getAttendanceId() == null) {

                        throw new IllegalArgumentException(
                                        "Attendance record is required for overtime");
                }

                Attendance attendance = attendanceRepository
                                .findById(
                                                attendanceDetails.getAttendanceId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Attendance not found"));

                if (attendance.getEmployee() == null
                                || !attendance.getEmployee()
                                                .getEmployeeId()
                                                .equals(employee.getEmployeeId())) {

                        throw new IllegalArgumentException(
                                        "Attendance does not belong to the selected employee");
                }

                if (!attendance.getAttendanceDate()
                                .equals(overtimeDate)) {

                        throw new IllegalArgumentException(
                                        "Attendance date must match the overtime date");
                }

                return attendance;
        }

        private void calculateOvertimeValues(
                        Employee employee,
                        Overtime overtime) {

                BigDecimal overtimeMultiplier = getOvertimeMultiplier();

                BigDecimal overtimeRate = payrollCalculationService
                                .calculateOvertimeRate(
                                                employee,
                                                overtimeMultiplier);

                BigDecimal overtimeAmount = payrollCalculationService
                                .calculateOvertimePay(
                                                employee,
                                                overtime.getHours(),
                                                overtimeMultiplier);

                overtime.setOvertimeRate(
                                overtimeRate);

                overtime.setOvertimeAmount(
                                overtimeAmount);
        }

        private BigDecimal getOvertimeMultiplier() {

                String value = systemSettingService.getSettingValue(
                                "default_overtime_rate");

                try {

                        BigDecimal multiplier = new BigDecimal(value);

                        if (multiplier.compareTo(ZERO) <= 0) {
                                throw new IllegalArgumentException(
                                                "Overtime rate must be greater than zero");
                        }

                        return multiplier;

                } catch (NumberFormatException e) {

                        throw new IllegalArgumentException(
                                        "Invalid default overtime rate setting");
                }
        }
}