package com.employger.service;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.Overtime;
import com.employger.entity.Attendance;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PayrollService {

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private final PayrollRepository payrollRepository;
        private final EmployeeRepository employeeRepository;
        private final PayCycleRepository payCycleRepository;
        private final OvertimeRepository overtimeRepository;
        private final AttendanceRepository attendanceRepository;
        private final UserRepository userRepository;
        private final PayrollCalculationService payrollCalculationService;
        private final AuditService auditService;
        private final StatutoryDeductionService statutoryDeductionService;

        public PayrollService(
                        PayrollRepository payrollRepository,
                        EmployeeRepository employeeRepository,
                        PayCycleRepository payCycleRepository,
                        OvertimeRepository overtimeRepository,
                        AttendanceRepository attendanceRepository,
                        UserRepository userRepository,
                        PayrollCalculationService payrollCalculationService,
                        AuditService auditService,
                        StatutoryDeductionService statutoryDeductionService) {

                this.statutoryDeductionService = statutoryDeductionService;

                this.auditService = auditService;

                this.payrollRepository = payrollRepository;
                this.employeeRepository = employeeRepository;
                this.payCycleRepository = payCycleRepository;
                this.overtimeRepository = overtimeRepository;
                this.attendanceRepository = attendanceRepository;
                this.userRepository = userRepository;
                this.payrollCalculationService = payrollCalculationService;
        }

        public List<Payroll> getAllPayroll() {
                return payrollRepository.findAll();
        }

        public Optional<Payroll> getPayrollById(Integer id) {
                return payrollRepository.findById(id);
        }

        public List<Payroll> getPayrollByEmployee(
                        Integer employeeId) {

                return payrollRepository
                                .findByEmployeeEmployeeId(employeeId);
        }

        public List<Payroll> getPayrollByPayCycle(
                        Integer payCycleId) {

                return payrollRepository
                                .findByPayCyclePayCycleId(payCycleId);
        }

        public Optional<Payroll> getPayrollByEmployeeAndPayCycle(
                        Integer employeeId,
                        Integer payCycleId) {

                return payrollRepository
                                .findByEmployeeEmployeeIdAndPayCyclePayCycleId(
                                                employeeId,
                                                payCycleId);
        }

        /*
         * Shows what payroll would be for this employee and cycle without
         * creating or changing anything. Uses the same calculate() as
         * payroll creation, so the two cannot disagree.
         */
        @Transactional(readOnly = true)
        public Map<String, Object> previewPayroll(
                        Integer employeeId,
                        Integer payCycleId) {

                Employee employee = employeeRepository.findById(employeeId)
                                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

                PayCycle payCycle = payCycleRepository.findById(payCycleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Pay cycle not found"));

                Payroll existing = payrollRepository
                                .findByEmployeeEmployeeIdAndPayCyclePayCycleId(employeeId, payCycleId)
                                .orElse(null);

                PayrollBreakdown breakdown = calculate(employee, payCycle, existing);

                List<Map<String, Object>> attendanceBreakdown = new ArrayList<>();

                for (PayrollBreakdown.AttendanceDay day : breakdown.attendanceDays()) {

                        Map<String, Object> attendanceItem = new LinkedHashMap<>();

                        attendanceItem.put("attendanceId", day.attendanceId());
                        attendanceItem.put("date", day.date());
                        attendanceItem.put("status", day.status());
                        attendanceItem.put("restDay", day.restDay());
                        attendanceItem.put("recordedHours", day.recordedHours());
                        attendanceItem.put("unpaidHours", day.unpaidHours());
                        attendanceItem.put("deduction", day.deduction());

                        attendanceBreakdown.add(attendanceItem);
                }

                List<Map<String, Object>> overtimeBreakdown = new ArrayList<>();

                for (Overtime overtime : breakdown.overtime()) {

                        Map<String, Object> overtimeItem = new LinkedHashMap<>();

                        overtimeItem.put("overtimeId", overtime.getOvertimeId());
                        overtimeItem.put("date", overtime.getOvertimeDate());
                        overtimeItem.put("hours", overtime.getHours());
                        overtimeItem.put("amount", overtime.getOvertimeAmount());

                        overtimeBreakdown.add(overtimeItem);
                }

                Map<String, Object> preview = new LinkedHashMap<>();

                preview.put("employeeId", employee.getEmployeeId());
                preview.put("minimumWageEarner", employee.isMinimumWageEarner());
                preview.put("payCycleId", payCycle.getPayCycleId());
                preview.put("cycleStartDate", payCycle.getStartDate());
                preview.put("cycleEndDate", payCycle.getEndDate());

                preview.put("cycleBasePay", breakdown.cycleBasePay());
                preview.put("baseRegularPay", breakdown.basePay());
                preview.put("hourlyRate", breakdown.hourlyRate());
                preview.put("workingDaysInCycle", breakdown.workingDaysInCycle());
                preview.put("workingDaysEmployed", breakdown.workingDaysEmployed());
                preview.put("unpaidHours", breakdown.unpaidHours());
                preview.put("attendanceDeduction", breakdown.attendanceDeduction());
                preview.put("regularPay", breakdown.regularPay());
                preview.put("approvedOvertimeCount", breakdown.overtime().size());
                preview.put("overtimePay", breakdown.overtimePay());
                preview.put("bonusAmount", breakdown.bonusAmount());
                preview.put("grossPay", breakdown.grossPay());
                preview.put("sssContribution", breakdown.statutory().sss());
                preview.put("philHealthContribution", breakdown.statutory().philHealth());
                preview.put("pagIbigContribution", breakdown.statutory().pagIbig());
                preview.put("taxableCompensation", breakdown.statutory().taxableCompensation());

                // Employer cost on top of gross pay; not deducted from net pay.
                StatutoryDeductionService.EmployerShares employer = breakdown.statutory().employer();
                preview.put("sssEmployerContribution", employer.sss());
                preview.put("sssEcContribution", employer.ec());
                preview.put("philHealthEmployerContribution", employer.philHealth());
                preview.put("pagIbigEmployerContribution", employer.pagIbig());
                preview.put("employerContributionTotal", employer.total());
                preview.put("taxAmount", breakdown.taxAmount());
                preview.put("otherDeductions", breakdown.deductionAmount());
                preview.put("netPay", breakdown.netPay());

                preview.put("attendanceBreakdown", attendanceBreakdown);
                preview.put("overtimeBreakdown", overtimeBreakdown);
                preview.put("missingAttendanceDates", breakdown.missingAttendanceDates());
                preview.put("warnings", breakdown.notes());

                preview.put(
                                "note",
                                "Preview only. No payroll record was created or modified.");

                return preview;
        }

        @Transactional
        public Payroll createPayroll(
                        Integer employeeId,
                        Integer payCycleId) {

                Employee employee = employeeRepository
                                .findById(employeeId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Employee not found"));

                PayCycle payCycle = payCycleRepository
                                .findById(payCycleId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Pay cycle not found"));

                ensureEligibleForPayroll(employee, payCycle);

                if (payrollRepository
                                .findByEmployeeEmployeeIdAndPayCyclePayCycleId(
                                                employeeId,
                                                payCycleId)
                                .isPresent()) {

                        throw new IllegalStateException(
                                        "Payroll already exists for this employee and pay cycle");
                }

                ensureNoOverlappingPayroll(employee, payCycle, null);

                /*
                 * Always start from a new record. Identifiers, status,
                 * and processing/payment fields are never taken from
                 * the client, so a request cannot create an already
                 * PAID payroll or overwrite an existing one.
                 */
                Payroll payroll = new Payroll();

                payroll.setEmployee(employee);
                payroll.setPayCycle(payCycle);
                payroll.setStatus(Payroll.Status.DRAFT);

                // Save first so the new ID can be linked to its overtime.
                Payroll saved = payrollRepository.save(payroll);

                applyCalculation(saved);

                auditService.record("PAYROLL_CREATED",
                                "Created DRAFT " + describe(saved) + " with net pay " + saved.getNetPay());

                return saved;
        }

        private static String describe(Payroll payroll) {
                return "payroll #" + payroll.getPayrollId()
                                + " (employee #" + payroll.getEmployee().getEmployeeId()
                                + ", pay cycle \"" + payroll.getPayCycle().getCycleName() + "\")";
        }

        private void ensureEligibleForPayroll(
                        Employee employee,
                        PayCycle payCycle) {

                if (payCycle.getStatus() == PayCycle.Status.PROCESSED
                                || payCycle.getStatus() == PayCycle.Status.CLOSED) {

                        throw new IllegalStateException(
                                        "Pay cycle \"" + payCycle.getCycleName() + "\" is "
                                                        + payCycle.getStatus()
                                                        + ". Payroll can only be generated for OPEN or PROCESSING pay cycles.");
                }

                if (employee.getEmploymentStatus() != Employee.EmploymentStatus.ACTIVE) {
                        throw new IllegalStateException(
                                        "Payroll can only be generated for ACTIVE employees.");
                }

                if (employee.getDateHired() != null
                                && employee.getDateHired().isAfter(payCycle.getEndDate())) {

                        throw new IllegalStateException(
                                        "The employee was hired on " + employee.getDateHired()
                                                        + ", after this pay cycle ends ("
                                                        + payCycle.getEndDate() + ").");
                }
        }

        /*
         * An employee must never be paid for the same day twice, so
         * no two payroll records for one employee may belong to pay
         * cycles whose date ranges overlap.
         */
        private void ensureNoOverlappingPayroll(
                        Employee employee,
                        PayCycle payCycle,
                        Integer excludedPayrollId) {

                List<Payroll> overlapping = payrollRepository
                                .findByEmployeeEmployeeIdAndPayCycleStartDateLessThanEqualAndPayCycleEndDateGreaterThanEqual(
                                                employee.getEmployeeId(),
                                                payCycle.getEndDate(),
                                                payCycle.getStartDate());

                for (Payroll existing : overlapping) {

                        if (existing.getPayrollId().equals(excludedPayrollId)) {
                                continue;
                        }

                        PayCycle existingCycle = existing.getPayCycle();

                        throw new IllegalStateException(
                                        "Employee already has payroll #"
                                                        + existing.getPayrollId()
                                                        + " (" + existing.getStatus() + ") in pay cycle \""
                                                        + existingCycle.getCycleName() + "\" ("
                                                        + existingCycle.getStartDate() + " to "
                                                        + existingCycle.getEndDate()
                                                        + "), which overlaps this pay cycle. "
                                                        + "An employee cannot be paid twice for the same days.");
                }
        }

        /*
         * The single payroll calculation, used by preview, create,
         * recalculate, and process. Policy decided 2026-10-22:
         *
         *  - Base pay = annual salary / periods per year, prorated by
         *    working days for an employee hired after the cycle starts.
         *  - Rest days (rest_days setting, default Saturday and Sunday) never
         *    produce attendance deductions.
         *  - Working days: ABSENT = full regular day unpaid; PRESENT/LATE =
         *    regular hours minus recorded hours unpaid; LEAVE = no deduction.
         *  - A working day with no attendance record is paid, but listed as a
         *    warning so HR can complete attendance before processing.
         *  - Only APPROVED overtime dated in the cycle and not already paid by
         *    another payroll is included.
         *  - Intermediate values keep 6 decimals; only final amounts are
         *    rounded to centavos.
         *
         * `existing` is the payroll being recalculated (null for a new one),
         * so overtime already linked to it is still included.
         */
        PayrollBreakdown calculate(
                        Employee employee,
                        PayCycle payCycle,
                        Payroll existing) {

                PayrollCalculationService rates = payrollCalculationService;

                BigDecimal hourlyRate = rates.calculateHourlyRate(employee);
                BigDecimal factorRate = rates.calculateFactorRate();
                BigDecimal regularHoursPerDay = rates.calculateRegularHoursPerDay();
                Set<DayOfWeek> restDays = rates.getRestDays();

                LocalDate cycleStart = payCycle.getStartDate();
                LocalDate cycleEnd = payCycle.getEndDate();

                LocalDate employedFrom = employee.getDateHired() != null
                                && employee.getDateHired().isAfter(cycleStart)
                                                ? employee.getDateHired()
                                                : cycleStart;

                int workingDaysInCycle = rates.countWorkingDays(cycleStart, cycleEnd, restDays);

                int workingDaysEmployed = employedFrom.isAfter(cycleEnd)
                                ? 0
                                : rates.countWorkingDays(employedFrom, cycleEnd, restDays);

                List<String> notes = new ArrayList<>();

                // ---------- base pay, prorated by working days ----------

                BigDecimal cycleBasePay = rates.calculateCycleBasePay(
                                employee,
                                payCycle.getCycleType());

                BigDecimal basePay = cycleBasePay;

                if (workingDaysInCycle > 0 && workingDaysEmployed < workingDaysInCycle) {

                        basePay = cycleBasePay
                                        .multiply(BigDecimal.valueOf(workingDaysEmployed))
                                        .divide(BigDecimal.valueOf(workingDaysInCycle),
                                                        PayrollCalculationService.INTERMEDIATE_SCALE,
                                                        RoundingMode.HALF_UP);

                        notes.add("Base pay prorated: employed from " + employedFrom
                                        + ", paid for " + workingDaysEmployed + " of "
                                        + workingDaysInCycle + " working days.");
                }

                basePay = money(basePay);

                // ---------- attendance deduction ----------

                List<Attendance> attendanceRecords = new ArrayList<>(attendanceRepository
                                .findByEmployeeEmployeeIdAndAttendanceDateBetween(
                                                employee.getEmployeeId(),
                                                employedFrom,
                                                cycleEnd));

                attendanceRecords.sort(Comparator.comparing(Attendance::getAttendanceDate));

                List<PayrollBreakdown.AttendanceDay> attendanceDays = new ArrayList<>();
                Set<LocalDate> recordedDates = new HashSet<>();
                BigDecimal totalUnpaidHours = BigDecimal.ZERO;

                for (Attendance attendance : attendanceRecords) {

                        LocalDate date = attendance.getAttendanceDate();
                        recordedDates.add(date);

                        boolean restDay = !rates.isWorkingDay(date, restDays);

                        BigDecimal recordedHours = attendance.getRegularHours() != null
                                        ? attendance.getRegularHours()
                                        : BigDecimal.ZERO;

                        BigDecimal unpaidHours = restDay
                                        ? BigDecimal.ZERO
                                        : unpaidHours(attendance.getStatus(), recordedHours, regularHoursPerDay);

                        totalUnpaidHours = totalUnpaidHours.add(unpaidHours);

                        attendanceDays.add(new PayrollBreakdown.AttendanceDay(
                                        attendance.getAttendanceId(),
                                        date,
                                        attendance.getStatus(),
                                        restDay,
                                        recordedHours,
                                        unpaidHours,
                                        money(hourlyRate.multiply(unpaidHours))));
                }

                BigDecimal attendanceDeduction = money(hourlyRate.multiply(totalUnpaidHours));

                List<LocalDate> missingAttendanceDates = new ArrayList<>();

                for (LocalDate date = employedFrom; !date.isAfter(cycleEnd); date = date.plusDays(1)) {
                        if (rates.isWorkingDay(date, restDays) && !recordedDates.contains(date)) {
                                missingAttendanceDates.add(date);
                        }
                }

                if (!missingAttendanceDates.isEmpty()) {
                        notes.add("No attendance recorded for " + missingAttendanceDates.size()
                                        + " working day(s), paid in full: " + missingAttendanceDates);
                }

                BigDecimal regularPay = basePay.subtract(attendanceDeduction).max(ZERO);

                // ---------- overtime ----------

                Integer existingPayrollId = existing != null ? existing.getPayrollId() : null;

                List<Overtime> overtime = overtimeRepository
                                .findByEmployeeEmployeeIdAndOvertimeDateBetweenAndStatus(
                                                employee.getEmployeeId(),
                                                cycleStart,
                                                cycleEnd,
                                                Overtime.Status.APPROVED)
                                .stream()
                                .filter(record -> record.getPayroll() == null
                                                || record.getPayroll().getPayrollId().equals(existingPayrollId))
                                .sorted(Comparator.comparing(Overtime::getOvertimeDate))
                                .toList();

                BigDecimal overtimePay = money(overtime.stream()
                                .map(Overtime::getOvertimeAmount)
                                .filter(amount -> amount != null)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));

                // ---------- totals ----------

                // Bonuses are not recorded yet.
                BigDecimal bonusAmount = ZERO;

                BigDecimal grossPay = regularPay.add(overtimePay).add(bonusAmount);

                // Employee SSS, PhilHealth, and Pag-IBIG shares are the
                // deductions; withholding tax applies to gross pay less them.
                // For a minimum wage earner, regular and overtime pay are
                // exempt from withholding tax (RR 11-2018).
                StatutoryDeductionService.StatutoryDeductions statutory = statutoryDeductionService
                                .calculate(
                                                employee.getBaseSalary(),
                                                payCycle.getCycleType(),
                                                grossPay,
                                                employee.isMinimumWageEarner(),
                                                regularPay.add(overtimePay));

                notes.addAll(statutory.notes());

                BigDecimal taxAmount = statutory.withholdingTax();
                BigDecimal deductionAmount = statutory.totalContributions();

                BigDecimal netPay = grossPay
                                .subtract(taxAmount)
                                .subtract(deductionAmount)
                                .max(ZERO);

                return new PayrollBreakdown(
                                cycleBasePay,
                                basePay,
                                hourlyRate,
                                factorRate,
                                regularHoursPerDay,
                                workingDaysInCycle,
                                workingDaysEmployed,
                                attendanceDays,
                                missingAttendanceDates,
                                totalUnpaidHours,
                                attendanceDeduction,
                                regularPay,
                                overtime,
                                overtimePay,
                                bonusAmount,
                                grossPay,
                                statutory,
                                taxAmount,
                                deductionAmount,
                                netPay,
                                notes);
        }

        private static BigDecimal unpaidHours(
                        Attendance.Status status,
                        BigDecimal recordedHours,
                        BigDecimal regularHoursPerDay) {

                if (status == null) {
                        return BigDecimal.ZERO;
                }

                switch (status) {
                        case ABSENT:
                                return regularHoursPerDay;

                        case PRESENT:
                        case LATE:
                                return regularHoursPerDay.subtract(recordedHours).max(BigDecimal.ZERO);

                        case LEAVE:
                        default:
                                // Leave is not deducted: paid/unpaid leave is not recorded.
                                return BigDecimal.ZERO;
                }
        }

        private static BigDecimal money(BigDecimal amount) {
                return amount.setScale(
                                PayrollCalculationService.FINAL_MONETARY_SCALE,
                                RoundingMode.HALF_UP);
        }

        /*
         * Copies a fresh calculation onto the payroll, records the snapshot
         * of how it was calculated, and links the included overtime to it.
         * The payroll must already have an ID.
         */
        private void applyCalculation(Payroll payroll) {

                PayrollBreakdown breakdown = calculate(
                                payroll.getEmployee(),
                                payroll.getPayCycle(),
                                payroll);

                payroll.setRegularPay(breakdown.regularPay());
                payroll.setOvertimePay(breakdown.overtimePay());
                payroll.setBonusAmount(breakdown.bonusAmount());
                payroll.setGrossPay(breakdown.grossPay());
                payroll.setTaxAmount(breakdown.taxAmount());
                payroll.setDeductionAmount(breakdown.deductionAmount());
                payroll.setNetPay(breakdown.netPay());

                payroll.setBasePay(breakdown.basePay());
                payroll.setAttendanceDeduction(breakdown.attendanceDeduction());
                payroll.setHourlyRate(breakdown.hourlyRate());
                payroll.setFactorRate(breakdown.factorRate());
                payroll.setRegularHoursPerDay(breakdown.regularHoursPerDay());
                payroll.setWorkingDaysInCycle(breakdown.workingDaysInCycle());
                payroll.setWorkingDaysEmployed(breakdown.workingDaysEmployed());
                payroll.setCalculationNotes(breakdown.notes().isEmpty()
                                ? null
                                : String.join("\n", breakdown.notes()));
                payroll.setCalculatedAt(LocalDateTime.now());

                payroll.setSssContribution(breakdown.statutory().sss());
                payroll.setPhilHealthContribution(breakdown.statutory().philHealth());
                payroll.setPagIbigContribution(breakdown.statutory().pagIbig());
                payroll.setTaxableCompensation(breakdown.statutory().taxableCompensation());

                StatutoryDeductionService.EmployerShares employer = breakdown.statutory().employer();
                payroll.setSssEmployerContribution(employer.sss());
                payroll.setSssEcContribution(employer.ec());
                payroll.setPhilHealthEmployerContribution(employer.philHealth());
                payroll.setPagIbigEmployerContribution(employer.pagIbig());

                // Release overtime no longer included, then link what is.
                for (Overtime linked : overtimeRepository.findByPayrollPayrollId(payroll.getPayrollId())) {
                        if (!breakdown.overtime().contains(linked)) {
                                linked.setPayroll(null);
                        }
                }

                for (Overtime included : breakdown.overtime()) {
                        included.setPayroll(payroll);
                }
        }

        /*
         * Only DRAFT payroll can change. Amounts are recalculated from
         * the current attendance, overtime, salary, and settings rather
         * than accepted from the client, and status/processing fields
         * can only change through processPayroll and markPayrollAsPaid.
         */
        @Transactional
        public Payroll updatePayroll(Integer id) {

                Payroll payroll = payrollRepository
                                .findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Payroll not found"));

                if (payroll.getStatus() != Payroll.Status.DRAFT) {
                        throw new IllegalStateException(
                                        "Only DRAFT payroll can be updated. "
                                                        + payroll.getStatus()
                                                        + " payroll is locked.");
                }

                BigDecimal previousNetPay = payroll.getNetPay();

                applyCalculation(payroll);

                auditService.record("PAYROLL_RECALCULATED",
                                "Recalculated DRAFT " + describe(payroll) + ": net pay "
                                                + previousNetPay + " -> " + payroll.getNetPay());

                return payrollRepository.save(payroll);
        }

        @Transactional
        public Payroll processPayroll(Integer id, String username) {
                Payroll payroll = payrollRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));

                if (payroll.getStatus() != Payroll.Status.DRAFT) {
                        throw new IllegalStateException(
                                        "Only DRAFT payroll can be processed");
                }

                ensureNoOverlappingPayroll(
                                payroll.getEmployee(),
                                payroll.getPayCycle(),
                                payroll.getPayrollId());

                com.employger.entity.User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                // Process the current numbers, not those from when the draft
                // was created: attendance or overtime may have changed since.
                BigDecimal draftNetPay = payroll.getNetPay();

                applyCalculation(payroll);

                payroll.setStatus(Payroll.Status.PROCESSED);
                payroll.setProcessedAt(LocalDateTime.now());
                payroll.setProcessedBy(user);

                auditService.record("PAYROLL_PROCESSED",
                                "Processed " + describe(payroll) + " with net pay " + payroll.getNetPay()
                                                + (draftNetPay != null && draftNetPay.compareTo(payroll.getNetPay()) != 0
                                                                ? " (recalculated from draft net pay " + draftNetPay + ")"
                                                                : ""));

                return payrollRepository.save(payroll);
        }

        @Transactional
        public Payroll markPayrollAsPaid(
                        Integer id,
                        String paymentMethod,
                        String paymentReference) {

                Payroll payroll = payrollRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));

                if (payroll.getStatus() != Payroll.Status.PROCESSED) {
                        throw new IllegalStateException(
                                        "Only PROCESSED payroll can be marked as paid");
                }

                if (paymentMethod == null || paymentMethod.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Payment method is required");
                }

                paymentMethod = paymentMethod.trim();
                paymentReference = paymentReference == null || paymentReference.isBlank()
                                ? null
                                : paymentReference.trim();

                // Column limits: payment_method VARCHAR(50), payment_reference VARCHAR(100).
                if (paymentMethod.length() > 50) {
                        throw new IllegalArgumentException(
                                        "Payment method must be at most 50 characters");
                }

                if (paymentReference != null && paymentReference.length() > 100) {
                        throw new IllegalArgumentException(
                                        "Payment reference must be at most 100 characters");
                }

                ensureNoOverlappingPayroll(
                                payroll.getEmployee(),
                                payroll.getPayCycle(),
                                payroll.getPayrollId());

                payroll.setStatus(Payroll.Status.PAID);
                payroll.setPaymentDate(java.time.LocalDateTime.now());
                payroll.setPaymentMethod(paymentMethod);
                payroll.setPaymentReference(paymentReference);

                auditService.record("PAYROLL_PAID",
                                "Marked " + describe(payroll) + " as PAID: net pay " + payroll.getNetPay()
                                                + ", method " + paymentMethod
                                                + (paymentReference != null && !paymentReference.isBlank()
                                                                ? ", reference " + paymentReference
                                                                : ""));

                return payrollRepository.save(payroll);
        }

        @Transactional
        public void deletePayroll(Integer id) {

                Payroll payroll = payrollRepository
                                .findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Payroll not found"));

                if (payroll.getStatus() != Payroll.Status.DRAFT) {
                        throw new IllegalStateException(
                                        "Only DRAFT payroll can be deleted. "
                                                        + payroll.getStatus()
                                                        + " payroll is kept for the payroll history.");
                }

                auditService.record("PAYROLL_DELETED",
                                "Deleted DRAFT " + describe(payroll) + " with net pay " + payroll.getNetPay());

                // Release this draft's overtime so a new payroll can include it.
                for (Overtime linked : overtimeRepository.findByPayrollPayrollId(payroll.getPayrollId())) {
                        linked.setPayroll(null);
                }

                payrollRepository.delete(payroll);
        }
}