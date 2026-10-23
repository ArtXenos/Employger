package com.employger.service;

import com.employger.dto.ContactDetailsRequest;
import com.employger.dto.OvertimeFilingRequest;
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
 * Self-service for a logged-in user, on the records of the employee linked
 * to their account. The employee is always taken from the login, never from
 * the request, so one employee cannot read or change another's data.
 * DRAFT payroll is not shown: it is not final.
 *
 * Read-only except three actions, each delegated to the service that owns
 * the rules: filing overtime (OvertimeService), changing contact details
 * (EmployeeService) and the yearly BIR 2316 (ReportService).
 */
@Service
@Transactional(readOnly = true)
public class SelfServiceService {

    private final UserRepository userRepository;
    private final PayrollRepository payrollRepository;
    private final PayslipRepository payslipRepository;
    private final AttendanceRepository attendanceRepository;
    private final OvertimeRepository overtimeRepository;
    private final OvertimeService overtimeService;
    private final EmployeeService employeeService;
    private final ReportService reportService;
    private final AuditService auditService;

    public SelfServiceService(
            UserRepository userRepository,
            PayrollRepository payrollRepository,
            PayslipRepository payslipRepository,
            AttendanceRepository attendanceRepository,
            OvertimeRepository overtimeRepository,
            OvertimeService overtimeService,
            EmployeeService employeeService,
            ReportService reportService,
            AuditService auditService) {

        this.userRepository = userRepository;
        this.payrollRepository = payrollRepository;
        this.payslipRepository = payslipRepository;
        this.attendanceRepository = attendanceRepository;
        this.overtimeRepository = overtimeRepository;
        this.overtimeService = overtimeService;
        this.employeeService = employeeService;
        this.reportService = reportService;
        this.auditService = auditService;
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

    /**
     * Files a PENDING overtime request for the employee's own worked day.
     * OvertimeService applies the same rules as when HR files it: the day
     * must be worked, not locked by processed or paid payroll, and within
     * the daily hour limit. HR then approves or rejects it.
     */
    @Transactional
    public Overtime fileOvertime(String username, OvertimeFilingRequest request) {

        Employee employee = getMyEmployee(username);

        Attendance attendance = attendanceRepository
                .findByEmployeeEmployeeIdAndAttendanceDate(employee.getEmployeeId(), request.date())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No attendance is recorded for " + request.date()
                                + ". Overtime can only be filed for a day you worked."));

        Overtime overtime = new Overtime();
        overtime.setOvertimeDate(request.date());
        overtime.setHours(request.hours());
        overtime.setAttendance(attendance);

        Overtime saved = overtimeService.createOvertime(employee.getEmployeeId(), overtime);

        auditService.record("OVERTIME_REQUESTED",
                "Employee #" + employee.getEmployeeId() + " filed overtime #" + saved.getOvertimeId()
                        + " (" + saved.getOvertimeDate() + ", " + saved.getHours() + " h)");

        return saved;
    }

    @Transactional
    public Employee updateMyContactDetails(String username, ContactDetailsRequest request) {
        return employeeService.updateOwnContactDetails(getMyEmployee(username), request);
    }

    public ReportService.Form2316 getMyForm2316(String username, int year) {
        return reportService.form2316(year, getMyEmployee(username).getEmployeeId());
    }
}
