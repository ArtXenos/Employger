package com.employger.controller;

import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.entity.Payroll;
import com.employger.entity.Payslip;
import com.employger.service.SelfServiceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Employee self-service: the logged-in user's own profile, payroll,
 * attendance, and overtime. Read-only. Responses are purpose-built records
 * so no other employee's or user's data is ever included.
 */
@RestController
@RequestMapping("/api/me")
public class SelfServiceController {

    private final SelfServiceService selfServiceService;

    public SelfServiceController(SelfServiceService selfServiceService) {
        this.selfServiceService = selfServiceService;
    }

    @GetMapping("/profile")
    public ProfileResponse getProfile(Authentication authentication) {
        return new ProfileResponse(selfServiceService.getMyEmployee(authentication.getName()));
    }

    @GetMapping("/payroll")
    public List<PayrollResponse> getPayroll(Authentication authentication) {
        return selfServiceService.getMyPayroll(authentication.getName())
                .stream()
                .map(payroll -> new PayrollResponse(
                        payroll,
                        selfServiceService.findPayslip(payroll).orElse(null)))
                .toList();
    }

    @GetMapping("/payroll/{payrollId}")
    public PayrollResponse getPayroll(
            @PathVariable Integer payrollId,
            Authentication authentication) {

        Payroll payroll = selfServiceService.getMyPayroll(authentication.getName(), payrollId);

        return new PayrollResponse(payroll, selfServiceService.findPayslip(payroll).orElse(null));
    }

    @GetMapping("/attendance")
    public List<AttendanceResponse> getAttendance(Authentication authentication) {
        return selfServiceService.getMyAttendance(authentication.getName())
                .stream()
                .map(AttendanceResponse::new)
                .toList();
    }

    @GetMapping("/overtime")
    public List<OvertimeResponse> getOvertime(Authentication authentication) {
        return selfServiceService.getMyOvertime(authentication.getName())
                .stream()
                .map(OvertimeResponse::new)
                .toList();
    }

    public record ProfileResponse(
            Integer employeeId,
            String firstName,
            String lastName,
            String email,
            String department,
            String jobTitle,
            LocalDate dateHired) {

        ProfileResponse(Employee employee) {
            this(
                    employee.getEmployeeId(),
                    employee.getFirstName(),
                    employee.getLastName(),
                    employee.getEmail(),
                    employee.getDepartment(),
                    employee.getJobTitle(),
                    employee.getDateHired());
        }
    }

    public record PayrollResponse(
            Integer payrollId,
            String payCycleName,
            LocalDate cycleStartDate,
            LocalDate cycleEndDate,
            LocalDate payDate,
            String status,
            BigDecimal basePay,
            BigDecimal attendanceDeduction,
            BigDecimal regularPay,
            BigDecimal overtimePay,
            BigDecimal bonusAmount,
            BigDecimal grossPay,
            BigDecimal sssContribution,
            BigDecimal philHealthContribution,
            BigDecimal pagIbigContribution,
            BigDecimal taxAmount,
            BigDecimal deductionAmount,
            BigDecimal netPay,
            LocalDateTime paymentDate,
            String paymentMethod,
            Integer payslipId,
            LocalDateTime payslipGeneratedAt) {

        PayrollResponse(Payroll payroll, Payslip payslip) {
            this(
                    payroll.getPayrollId(),
                    payroll.getPayCycle().getCycleName(),
                    payroll.getPayCycle().getStartDate(),
                    payroll.getPayCycle().getEndDate(),
                    payroll.getPayCycle().getPayDate(),
                    payroll.getStatus().name(),
                    payroll.getBasePay(),
                    payroll.getAttendanceDeduction(),
                    payroll.getRegularPay(),
                    payroll.getOvertimePay(),
                    payroll.getBonusAmount(),
                    payroll.getGrossPay(),
                    payroll.getSssContribution(),
                    payroll.getPhilHealthContribution(),
                    payroll.getPagIbigContribution(),
                    payroll.getTaxAmount(),
                    payroll.getDeductionAmount(),
                    payroll.getNetPay(),
                    payroll.getPaymentDate(),
                    payroll.getPaymentMethod(),
                    payslip != null ? payslip.getPayslipId() : null,
                    payslip != null ? payslip.getGeneratedAt() : null);
        }
    }

    public record AttendanceResponse(
            LocalDate date,
            LocalTime timeIn,
            LocalTime timeOut,
            BigDecimal regularHours,
            String status,
            String remarks) {

        AttendanceResponse(Attendance attendance) {
            this(
                    attendance.getAttendanceDate(),
                    attendance.getTimeIn(),
                    attendance.getTimeOut(),
                    attendance.getRegularHours(),
                    attendance.getStatus().name(),
                    attendance.getRemarks());
        }
    }

    public record OvertimeResponse(
            LocalDate date,
            BigDecimal hours,
            BigDecimal amount,
            String status) {

        OvertimeResponse(Overtime overtime) {
            this(
                    overtime.getOvertimeDate(),
                    overtime.getHours(),
                    overtime.getOvertimeAmount(),
                    overtime.getStatus().name());
        }
    }
}
