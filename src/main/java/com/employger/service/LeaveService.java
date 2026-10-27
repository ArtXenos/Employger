package com.employger.service;

import com.employger.dto.LeaveFilingRequest;
import com.employger.entity.ApprovalRequest;
import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.LeaveRequest;
import com.employger.entity.User;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.LeaveRequestRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Leave requests: employees file them, HR approves or rejects them.
 *
 * A leave covers the working days in its range: Monday to Friday, except
 * holidays (Saturday and Sunday are rest days). Paid leave is limited to
 * paid_leave_days_per_year per employee per calendar year, 5 by default
 * (Service Incentive Leave, Labor Code Art. 95). Approving a leave marks
 * each covered day in attendance, so payroll pays or deducts it.
 */
@Service
public class LeaveService {

    static final String PAID_LEAVE_SETTING = "paid_leave_days_per_year";
    static final int DEFAULT_PAID_LEAVE_DAYS = 5;

    private static final Set<ApprovalRequest.Status> ACTIVE =
            Set.of(ApprovalRequest.Status.PENDING, ApprovalRequest.Status.APPROVED);

    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final PayrollRepository payrollRepository;
    private final UserRepository userRepository;
    private final HolidayService holidayService;
    private final SystemSettingService systemSettingService;
    private final AuditService auditService;

    public LeaveService(
            LeaveRequestRepository leaveRequestRepository,
            AttendanceRepository attendanceRepository,
            PayrollRepository payrollRepository,
            UserRepository userRepository,
            HolidayService holidayService,
            SystemSettingService systemSettingService,
            AuditService auditService) {

        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.payrollRepository = payrollRepository;
        this.userRepository = userRepository;
        this.holidayService = holidayService;
        this.systemSettingService = systemSettingService;
        this.auditService = auditService;
    }

    /** Paid leave days for one employee and year. */
    public record PaidLeaveBalance(int year, int allowed, int used, int pending, int remaining) {
    }

    public List<LeaveRequest> getLeave(ApprovalRequest.Status status) {
        return status != null
                ? leaveRequestRepository.findByStatusOrderByStartDateAsc(status)
                : leaveRequestRepository.findAllByOrderByStartDateDesc();
    }

    public List<LeaveRequest> getLeaveOf(Employee employee) {
        return leaveRequestRepository.findByEmployeeEmployeeIdOrderByStartDateDesc(employee.getEmployeeId());
    }

    public PaidLeaveBalance paidLeaveBalance(Employee employee, int year) {
        int allowed = paidLeaveDaysPerYear();
        int used = paidDays(employee, year, ApprovalRequest.Status.APPROVED, null);
        int pending = paidDays(employee, year, ApprovalRequest.Status.PENDING, null);

        return new PaidLeaveBalance(year, allowed, used, pending, Math.max(0, allowed - used - pending));
    }

    @Transactional
    public LeaveRequest fileLeave(Employee employee, LeaveFilingRequest request) {

        LocalDate start = request.startDate();
        LocalDate end = request.endDate();

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The end date cannot be before the start date.");
        }

        if (start.getYear() != end.getYear()) {
            throw new IllegalArgumentException(
                    "A leave can't cross into another year. File one request for each year.");
        }

        List<LocalDate> days = workingDays(start, end);

        if (days.isEmpty()) {
            throw new IllegalArgumentException(
                    "These dates have no working days: weekends and holidays aren't counted as leave.");
        }

        if (!leaveRequestRepository
                .findByEmployeeEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getEmployeeId(), ACTIVE, end, start)
                .isEmpty()) {
            throw new IllegalStateException("You already have pending or approved leave on some of these dates.");
        }

        ensureDaysAvailable(employee, days);

        if (request.leaveType() == LeaveRequest.LeaveType.PAID) {
            int allowed = paidLeaveDaysPerYear();
            int alreadyAsked = paidDays(employee, start.getYear(), ApprovalRequest.Status.APPROVED, null)
                    + paidDays(employee, start.getYear(), ApprovalRequest.Status.PENDING, null);

            if (alreadyAsked + days.size() > allowed) {
                throw new IllegalStateException(paidLimitMessage(allowed, alreadyAsked, days.size(), start.getYear()));
            }
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setLeaveType(request.leaveType());
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setWorkingDays(days.size());
        leave.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());

        LeaveRequest saved = leaveRequestRepository.save(leave);

        auditService.record("LEAVE_REQUESTED", "Employee #" + employee.getEmployeeId() + " filed " + saved.describe());

        return saved;
    }

    @Transactional
    public LeaveRequest decide(Integer leaveId, ApprovalRequest.Status decision, String username, String remarks) {

        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        leave.checkPending();

        if (decision != ApprovalRequest.Status.APPROVED && decision != ApprovalRequest.Status.REJECTED) {
            throw new IllegalArgumentException("Invalid leave status");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (decision == ApprovalRequest.Status.APPROVED) {

            // Attendance or payroll may have changed since it was filed.
            List<LocalDate> days = workingDays(leave.getStartDate(), leave.getEndDate());
            ensureDaysAvailable(leave.getEmployee(), days);

            if (leave.getLeaveType() == LeaveRequest.LeaveType.PAID) {
                int allowed = paidLeaveDaysPerYear();
                int approved = paidDays(leave.getEmployee(), leave.getStartDate().getYear(),
                        ApprovalRequest.Status.APPROVED, leave.getLeaveId());

                if (approved + days.size() > allowed) {
                    throw new IllegalStateException(
                            paidLimitMessage(allowed, approved, days.size(), leave.getStartDate().getYear()));
                }
            }

            markAttendance(leave, days);
            leave.approve(user);
        } else {
            leave.reject(user, remarks);
        }

        auditService.record("LEAVE_" + decision, decision + " " + leave.describe()
                + (leave.getDecisionRemarks() != null ? ": " + leave.getDecisionRemarks() : ""));

        return leaveRequestRepository.save(leave);
    }

    /** An employee withdrawing their own leave before HR decides. */
    @Transactional
    public void cancel(Employee employee, Integer leaveId) {

        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .filter(found -> found.getEmployee().getEmployeeId().equals(employee.getEmployeeId()))
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        if (!leave.isPending()) {
            throw new IllegalStateException("Only pending leave can be cancelled. Ask HR to change a decided one.");
        }

        auditService.record("LEAVE_CANCELLED", "Employee #" + employee.getEmployeeId() + " cancelled " + leave.describe());

        leaveRequestRepository.delete(leave);
    }

    // Weekdays in [start, end] that aren't holidays.
    private List<LocalDate> workingDays(LocalDate start, LocalDate end) {
        Set<LocalDate> holidays = holidayService.typesBetween(start, end).keySet();
        List<LocalDate> days = new ArrayList<>();

        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            DayOfWeek weekday = day.getDayOfWeek();

            if (weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY && !holidays.contains(day)) {
                days.add(day);
            }
        }

        return days;
    }

    // A day can be taken as leave unless final payroll covers it or the
    // employee already worked it.
    private void ensureDaysAvailable(Employee employee, List<LocalDate> days) {
        for (LocalDate day : days) {

            if (payrollRepository.isDateLockedForEmployee(employee.getEmployeeId(), day)) {
                throw new IllegalStateException("Payroll covering " + day
                        + " has already been processed or paid, so it can't be taken as leave.");
            }

            Optional<Attendance> existing = attendanceRepository
                    .findByEmployeeEmployeeIdAndAttendanceDate(employee.getEmployeeId(), day);

            if (existing.isPresent() && worked(existing.get())) {
                throw new IllegalStateException("Attendance on " + day
                        + " shows the employee worked that day, so it can't be taken as leave.");
            }
        }
    }

    private static boolean worked(Attendance attendance) {
        return attendance.getStatus() == Attendance.Status.PRESENT
                || attendance.getStatus() == Attendance.Status.LATE;
    }

    // Records each covered day as LEAVE (paid) or ABSENT (unpaid); a day HR
    // had already marked ABSENT or LEAVE is updated, not duplicated.
    private void markAttendance(LeaveRequest leave, List<LocalDate> days) {
        Employee employee = leave.getEmployee();
        String remark = leave.getLeaveType().label() + " #" + leave.getLeaveId();

        for (LocalDate day : days) {
            Attendance attendance = attendanceRepository
                    .findByEmployeeEmployeeIdAndAttendanceDate(employee.getEmployeeId(), day)
                    .orElseGet(Attendance::new);

            attendance.setEmployee(employee);
            attendance.setAttendanceDate(day);
            attendance.setTimeIn(null);
            attendance.setTimeOut(null);
            attendance.setRegularHours(BigDecimal.ZERO);
            attendance.setStatus(leave.getLeaveType().attendanceStatus());
            attendance.setRemarks(remark);

            attendanceRepository.save(attendance);
        }
    }

    private int paidDays(Employee employee, int year, ApprovalRequest.Status status, Integer excludeLeaveId) {
        return leaveRequestRepository
                .findByEmployeeEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getEmployeeId(), Set.of(status), LocalDate.of(year, 12, 31), LocalDate.of(year, 1, 1))
                .stream()
                .filter(leave -> leave.getLeaveType() == LeaveRequest.LeaveType.PAID)
                .filter(leave -> !leave.getLeaveId().equals(excludeLeaveId))
                .mapToInt(LeaveRequest::getWorkingDays)
                .sum();
    }

    private int paidLeaveDaysPerYear() {
        return systemSettingService.findSettingValue(PAID_LEAVE_SETTING)
                .map(Integer::parseInt)
                .orElse(DEFAULT_PAID_LEAVE_DAYS);
    }

    private static String paidLimitMessage(int allowed, int taken, int asked, int year) {
        int left = Math.max(0, allowed - taken);
        return "Paid leave is limited to " + allowed + " days in " + year + ". " + taken
                + " already used or pending, so " + left + " left, but this request needs " + asked
                + ". File the rest as unpaid leave.";
    }
}
