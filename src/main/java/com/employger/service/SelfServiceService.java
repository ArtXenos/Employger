package com.employger.service;

import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.entity.Payroll;
import com.employger.entity.Payslip;
import com.employger.entity.User;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PayslipRepository;
import com.employger.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read-only access for a logged-in user to the records of the employee
 * linked to their account. The employee is always taken from the login,
 * never from the request, so one employee cannot read another's data.
 * DRAFT payroll is not shown: it is not final.
 */
@Service
@Transactional(readOnly = true)
public class SelfServiceService {

    private final UserRepository userRepository;
    private final PayrollRepository payrollRepository;
    private final PayslipRepository payslipRepository;
    private final AttendanceRepository attendanceRepository;
    private final OvertimeRepository overtimeRepository;

    public SelfServiceService(
            UserRepository userRepository,
            PayrollRepository payrollRepository,
            PayslipRepository payslipRepository,
            AttendanceRepository attendanceRepository,
            OvertimeRepository overtimeRepository) {

        this.userRepository = userRepository;
        this.payrollRepository = payrollRepository;
        this.payslipRepository = payslipRepository;
        this.attendanceRepository = attendanceRepository;
        this.overtimeRepository = overtimeRepository;
    }

    public Employee getMyEmployee(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getEmployee() == null) {
            throw new ResourceNotFoundException(
                    "Your account is not linked to an employee record. "
                            + "Please contact HR.");
        }

        return user.getEmployee();
    }

    public List<Payroll> getMyPayroll(String username) {

        Employee employee = getMyEmployee(username);

        return payrollRepository.findByEmployeeEmployeeId(employee.getEmployeeId())
                .stream()
                .filter(payroll -> payroll.getStatus() != Payroll.Status.DRAFT)
                .sorted(Comparator.comparing(
                        (Payroll payroll) -> payroll.getPayCycle().getStartDate()).reversed())
                .toList();
    }

    public Payroll getMyPayroll(String username, Integer payrollId) {

        Employee employee = getMyEmployee(username);

        // Someone else's payroll, or a DRAFT, is reported as not found so
        // its existence is not revealed.
        return payrollRepository.findById(payrollId)
                .filter(payroll -> payroll.getEmployee().getEmployeeId().equals(employee.getEmployeeId()))
                .filter(payroll -> payroll.getStatus() != Payroll.Status.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));
    }

    public Optional<Payslip> findPayslip(Payroll payroll) {
        return payslipRepository.findByPayrollPayrollId(payroll.getPayrollId());
    }

    public List<Attendance> getMyAttendance(String username) {

        Employee employee = getMyEmployee(username);

        return attendanceRepository.findByEmployeeEmployeeId(employee.getEmployeeId())
                .stream()
                .sorted(Comparator.comparing(Attendance::getAttendanceDate).reversed())
                .toList();
    }

    public List<Overtime> getMyOvertime(String username) {

        Employee employee = getMyEmployee(username);

        return overtimeRepository.findByEmployeeEmployeeId(employee.getEmployeeId())
                .stream()
                .sorted(Comparator.comparing(Overtime::getOvertimeDate).reversed())
                .toList();
    }
}
