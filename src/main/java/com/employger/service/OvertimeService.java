package com.employger.service;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.ApprovalRequest;
import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.Holiday;
import com.employger.entity.Overtime;
import com.employger.entity.User;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.UserRepository;
import com.employger.service.salary.SalaryHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class OvertimeService {

        private static final BigDecimal ZERO = BigDecimal.ZERO;

        private static final BigDecimal HOURS_PER_DAY = new BigDecimal("24");

        private final OvertimeRepository overtimeRepository;
        private final EmployeeRepository employeeRepository;
        private final AttendanceRepository attendanceRepository;
        private final UserRepository userRepository;
        private final PayrollRepository payrollRepository;
        private final PayrollCalculationService payrollCalculationService;
        private final SystemSettingService systemSettingService;
        private final AuditService auditService;
        private final HolidayService holidayService;
        private final SalaryHistoryService salaryHistoryService;

        public OvertimeService(
                        OvertimeRepository overtimeRepository,
                        EmployeeRepository employeeRepository,
                        AttendanceRepository attendanceRepository,
                        UserRepository userRepository,
                        PayrollRepository payrollRepository,
                        PayrollCalculationService payrollCalculationService,
                        SystemSettingService systemSettingService,
                        AuditService auditService,
                        HolidayService holidayService,
                        SalaryHistoryService salaryHistoryService) {

                this.salaryHistoryService = salaryHistoryService;
                this.auditService = auditService;
                this.holidayService = holidayService;
                this.overtimeRepository = overtimeRepository;
                this.employeeRepository = employeeRepository;
                this.attendanceRepository = attendanceRepository;
                this.userRepository = userRepository;
                this.payrollRepository = payrollRepository;
                this.payrollCalculationService = payrollCalculationService;
                this.systemSettingService = systemSettingService;
        }

        public List<Overtime> getAllOvertime() {
                return overtimeRepository.findAll();
        }

        public List<Overtime> getOvertimeByStatus(ApprovalRequest.Status status) {
                return overtimeRepository.findByStatusOrderByOvertimeDateAsc(status);
        }

        public Optional<Overtime> getOvertimeById(Integer id) {
                return overtimeRepository.findById(id);
        }

        public List<Overtime> getOvertimeByEmployee(
                        Integer employeeId) {

                return overtimeRepository
                                .findByEmployeeEmployeeId(employeeId);
        }

        @Transactional
        public Overtime createOvertime(
                        Integer employeeId,
                        Overtime overtimeDetails) {

                Employee employee = employeeRepository
                                .findById(employeeId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Employee not found"));

                validateOvertimeDetails(overtimeDetails);

                // Never trust a client-supplied ID or approver: save() would
                // otherwise overwrite the existing overtime record with that ID.
                overtimeDetails.setOvertimeId(null);
                overtimeDetails.setApprovedBy(null);

                overtimeDetails.setEmployee(employee);

                Attendance attendance = resolveAttendance(
                                employee,
                                overtimeDetails.getAttendance(),
                                overtimeDetails.getOvertimeDate());

                overtimeDetails.setAttendance(attendance);

                ensureDateNotLocked(employee, overtimeDetails.getOvertimeDate());
                ensureWorkedDay(attendance);
                ensureWithinDailyHours(employee, attendance, overtimeDetails.getHours(), null);

                calculateOvertimeValues(
                                employee,
                                overtimeDetails);

                return overtimeRepository.save(overtimeDetails);
        }

        @Transactional
        public Overtime updateOvertime(
                        Integer id,
                        Integer employeeId,
                        Overtime overtimeDetails) {

                Overtime overtime = overtimeRepository
                                .findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Overtime not found"));

                if (overtime.getStatus() != ApprovalRequest.Status.PENDING) {
                        throw new IllegalStateException(
                                        "Only pending overtime can be edited");
                }

                Employee employee = employeeRepository
                                .findById(employeeId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Employee not found"));

                validateOvertimeDetails(overtimeDetails);

                Attendance attendance = resolveAttendance(
                                employee,
                                overtimeDetails.getAttendance(),
                                overtimeDetails.getOvertimeDate());

                ensureDateNotLocked(overtime.getEmployee(), overtime.getOvertimeDate());
                ensureDateNotLocked(employee, overtimeDetails.getOvertimeDate());
                ensureWorkedDay(attendance);
                ensureWithinDailyHours(employee, attendance, overtimeDetails.getHours(), id);

                overtime.setEmployee(employee);

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

        @Transactional
        public Overtime updateOvertimeStatus(
                        Integer overtimeId,
                        ApprovalRequest.Status newStatus,
                        String username) {

                Overtime overtime = overtimeRepository
                                .findById(overtimeId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Overtime not found"));

                overtime.checkPending();

                if (newStatus != ApprovalRequest.Status.APPROVED
                                && newStatus != ApprovalRequest.Status.REJECTED) {

                        throw new IllegalArgumentException(
                                        "Invalid overtime status");
                }

                User user = userRepository
                                .findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                if (newStatus == ApprovalRequest.Status.APPROVED) {

                        // Attendance or other overtime may have changed since the
                        // request was filed, so check again before approving.
                        ensureDateNotLocked(overtime.getEmployee(), overtime.getOvertimeDate());
                        ensureWorkedDay(overtime.getAttendance());
                        ensureWithinDailyHours(
                                        overtime.getEmployee(),
                                        overtime.getAttendance(),
                                        overtime.getHours(),
                                        overtime.getOvertimeId());

                        // The approved amount uses the salary and overtime rate in
                        // effect at approval, not those from when it was requested.
                        calculateOvertimeValues(overtime.getEmployee(), overtime);

                        overtime.approve(user);
                } else {
                        overtime.reject(user);
                }

                auditService.record("OVERTIME_" + newStatus, newStatus + " " + overtime.describe());

                return overtimeRepository.save(overtime);
        }

        @Transactional
        public void deleteOvertime(Integer id) {

                Overtime overtime = overtimeRepository
                                .findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Overtime not found"));

                if (overtime.getStatus() == ApprovalRequest.Status.APPROVED
                                && payrollRepository.isDateLockedForEmployee(
                                                overtime.getEmployee().getEmployeeId(),
                                                overtime.getOvertimeDate())) {

                        throw new IllegalStateException(
                                        "This approved overtime is part of a processed or paid payroll "
                                                        + "and cannot be deleted.");
                }

                auditService.record("OVERTIME_DELETED",
                                "Deleted " + overtime.getStatus() + " " + overtime.describe());

                overtimeRepository.delete(overtime);
        }

        private void ensureDateNotLocked(
                        Employee employee,
                        LocalDate date) {

                if (payrollRepository.isDateLockedForEmployee(employee.getEmployeeId(), date)) {
                        throw new IllegalStateException(
                                        "Payroll covering " + date
                                                        + " has already been processed or paid for this employee, "
                                                        + "so overtime for that day can no longer be changed.");
                }
        }

        private void ensureWorkedDay(Attendance attendance) {

                if (attendance == null) {
                        throw new IllegalArgumentException(
                                        "This overtime is not linked to an attendance record. "
                                                        + "Edit it and select the attendance for that day first.");
                }

                if (attendance.getStatus() == Attendance.Status.ABSENT
                                || attendance.getStatus() == Attendance.Status.LEAVE) {

                        throw new IllegalArgumentException(
                                        "Overtime cannot be recorded for a day marked "
                                                        + attendance.getStatus() + ".");
                }
        }

        /*
         * Regular hours plus all pending and approved overtime for the day
         * cannot exceed 24 hours. Rejected overtime does not count.
         */
        private void ensureWithinDailyHours(
                        Employee employee,
                        Attendance attendance,
                        BigDecimal requestedHours,
                        Integer excludedOvertimeId) {

                BigDecimal regularHours = attendance.getRegularHours() != null
                                ? attendance.getRegularHours()
                                : ZERO;

                BigDecimal otherOvertimeHours = overtimeRepository
                                .findByEmployeeEmployeeIdAndOvertimeDate(
                                                employee.getEmployeeId(),
                                                attendance.getAttendanceDate())
                                .stream()
                                .filter(other -> !other.getOvertimeId().equals(excludedOvertimeId))
                                .filter(other -> other.getStatus() != ApprovalRequest.Status.REJECTED)
                                .map(Overtime::getHours)
                                .reduce(ZERO, BigDecimal::add);

                BigDecimal total = regularHours
                                .add(otherOvertimeHours)
                                .add(requestedHours);

                if (total.compareTo(HOURS_PER_DAY) > 0) {
                        throw new IllegalArgumentException(
                                        "Regular hours (" + regularHours.stripTrailingZeros().toPlainString()
                                                        + ") plus pending and approved overtime ("
                                                        + otherOvertimeHours.add(requestedHours)
                                                                        .stripTrailingZeros().toPlainString()
                                                        + ") would exceed 24 hours on "
                                                        + attendance.getAttendanceDate() + ".");
                }
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
                                .orElseThrow(() -> new ResourceNotFoundException(
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

        /*
         * Overtime pay = hourly rate x multiplier x hours. The multiplier
         * depends on the day (migration V6): default_overtime_rate (1.25) on
         * an ordinary working day; on a rest day or holiday, that day's rate
         * x overtime_premium_factor (1.30), e.g. 2.00 x 1.30 = 2.60 on a
         * regular holiday. Set when the overtime is created or approved,
         * at the salary in effect on the overtime date (salary history, V13).
         */
        private void calculateOvertimeValues(
                        Employee employee,
                        Overtime overtime) {

                boolean restDay = !payrollCalculationService.isWorkingDay(
                                overtime.getOvertimeDate(),
                                payrollCalculationService.getRestDays());

                Holiday.Type holidayType = holidayService.typeOn(overtime.getOvertimeDate());

                BigDecimal overtimeMultiplier = holidayType == null && !restDay
                                ? getOvertimeMultiplier()
                                : payrollCalculationService.dayRates(holidayType, restDay).overtime();

                BigDecimal salary = salaryHistoryService.timeline(employee)
                                .salaryOn(overtime.getOvertimeDate());

                BigDecimal overtimeRate = payrollCalculationService
                                .overtimeRateFor(
                                                salary,
                                                overtimeMultiplier);

                BigDecimal overtimeAmount = payrollCalculationService
                                .overtimePayFor(
                                                salary,
                                                overtime.getHours(),
                                                overtimeMultiplier);

                overtime.setOvertimeRate(
                                overtimeRate);

                overtime.setOvertimeAmount(
                                overtimeAmount);
        }

        /**
         * After a salary change: prices again the employee's overtime dated
         * on or after the effective date that no processed payroll has paid
         * (pending, or approved and not yet in payroll or only in a DRAFT).
         * Its hours and status stay as they are. Returns how many changed.
         */
        @Transactional
        public int repriceUnpaidOvertime(Employee employee, LocalDate from) {

                int changed = 0;

                for (Overtime overtime : overtimeRepository.findByEmployeeEmployeeId(employee.getEmployeeId())) {

                        boolean unpaid = overtime.getPayroll() == null || overtime.getPayroll().isDraft();
                        boolean open = overtime.getStatus() == ApprovalRequest.Status.PENDING
                                        || overtime.getStatus() == ApprovalRequest.Status.APPROVED;

                        if (!unpaid || !open || overtime.getOvertimeDate().isBefore(from)) {
                                continue;
                        }

                        BigDecimal before = overtime.getOvertimeAmount();
                        calculateOvertimeValues(employee, overtime);

                        if (before == null || before.compareTo(overtime.getOvertimeAmount()) != 0) {
                                overtimeRepository.save(overtime);
                                auditService.record("OVERTIME_REPRICED", "Overtime #" + overtime.getOvertimeId()
                                                + " on " + overtime.getOvertimeDate() + " repriced after a salary change: "
                                                + before + " -> " + overtime.getOvertimeAmount());
                                changed++;
                        }
                }

                return changed;
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