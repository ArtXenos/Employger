package com.employger.service;

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
import com.employger.service.SystemSettingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PayrollService {

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private final PayrollRepository payrollRepository;
        private final EmployeeRepository employeeRepository;
        private final PayCycleRepository payCycleRepository;
        private final OvertimeRepository overtimeRepository;
        private final AttendanceRepository attendanceRepository;
        private final UserRepository userRepository;
        private final SystemSettingService systemSettingService;
        private final PayrollCalculationService payrollCalculationService;

        public PayrollService(
                        PayrollRepository payrollRepository,
                        EmployeeRepository employeeRepository,
                        PayCycleRepository payCycleRepository,
                        OvertimeRepository overtimeRepository,
                        AttendanceRepository attendanceRepository,
                        UserRepository userRepository,
                        PayrollCalculationService payrollCalculationService,
                        SystemSettingService systemSettingService) {

                this.payrollRepository = payrollRepository;
                this.employeeRepository = employeeRepository;
                this.payCycleRepository = payCycleRepository;
                this.overtimeRepository = overtimeRepository;
                this.attendanceRepository = attendanceRepository;
                this.userRepository = userRepository;
                this.payrollCalculationService = payrollCalculationService;
                this.systemSettingService = systemSettingService;
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

        public Map<String, Object> previewPayroll(
                        Integer employeeId,
                        Integer payCycleId) {

                Employee employee = employeeRepository.findById(employeeId)
                                .orElseThrow(() -> new RuntimeException("Employee not found"));

                PayCycle payCycle = payCycleRepository.findById(payCycleId)
                                .orElseThrow(() -> new RuntimeException("Pay cycle not found"));

                // Calculate regular pay without creating a payroll record.
                BigDecimal baseRegularPay = payrollCalculationService.calculateCycleBasePay(
                                employee,
                                payCycle.getCycleType());

                BigDecimal attendanceDeduction = calculateAttendanceDeduction(employee, payCycle);

                BigDecimal regularPay = baseRegularPay
                                .subtract(attendanceDeduction)
                                .max(ZERO)
                                .setScale(2, RoundingMode.HALF_UP);

                // Include only approved overtime within this pay cycle.
                List<Overtime> approvedOvertime = overtimeRepository
                                .findByEmployeeEmployeeIdAndOvertimeDateBetweenAndStatus(
                                                employeeId,
                                                payCycle.getStartDate(),
                                                payCycle.getEndDate(),
                                                Overtime.Status.APPROVED);

                BigDecimal overtimePay = approvedOvertime.stream()
                                .map(Overtime::getOvertimeAmount)
                                .filter(amount -> amount != null)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(2, RoundingMode.HALF_UP);

                BigDecimal bonusAmount = ZERO;
                BigDecimal grossPay = regularPay
                                .add(overtimePay)
                                .add(bonusAmount)
                                .setScale(2, RoundingMode.HALF_UP);

                // Tax and other deductions are not yet calculated by the
                // existing payroll logic, so preserve its current behavior.
                BigDecimal taxAmount = ZERO;
                BigDecimal otherDeductions = ZERO;
                BigDecimal netPay = grossPay
                                .subtract(taxAmount)
                                .subtract(otherDeductions)
                                .max(ZERO)
                                .setScale(2, RoundingMode.HALF_UP);

                // Build an attendance breakdown for the preview response.
                List<Map<String, Object>> attendanceBreakdown = new ArrayList<>();

                List<Attendance> attendanceRecords = attendanceRepository.findByEmployeeEmployeeId(employeeId);

                BigDecimal regularHoursPerDay = new BigDecimal(
                                systemSettingService.getSettingValue(
                                                "regular_hours_per_day"));

                for (Attendance attendance : attendanceRecords) {
                        LocalDate date = attendance.getAttendanceDate();

                        if (date == null
                                        || date.isBefore(payCycle.getStartDate())
                                        || date.isAfter(payCycle.getEndDate())
                                        || attendance.getStatus() == null) {
                                continue;
                        }

                        BigDecimal recordedHours = attendance.getRegularHours();

                        if (recordedHours == null) {
                                recordedHours = BigDecimal.ZERO;
                        }

                        BigDecimal unpaidHours;

                        switch (attendance.getStatus()) {
                                case ABSENT:
                                        unpaidHours = regularHoursPerDay;
                                        break;

                                case PRESENT:
                                case LATE:
                                        unpaidHours = regularHoursPerDay
                                                        .subtract(recordedHours)
                                                        .max(BigDecimal.ZERO);
                                        break;

                                case LEAVE:
                                default:
                                        unpaidHours = BigDecimal.ZERO;
                                        break;
                        }

                        BigDecimal hourlyRate = payrollCalculationService.calculateHourlyRate(employee);

                        BigDecimal attendanceDeductionForDay = hourlyRate
                                        .multiply(unpaidHours)
                                        .setScale(2, RoundingMode.HALF_UP);

                        Map<String, Object> attendanceItem = new LinkedHashMap<>();

                        attendanceItem.put("date", date);
                        attendanceItem.put("status", attendance.getStatus());
                        attendanceItem.put("recordedHours", recordedHours);
                        attendanceItem.put("unpaidHours", unpaidHours);
                        attendanceItem.put(
                                        "deduction",
                                        attendanceDeductionForDay);

                        attendanceBreakdown.add(attendanceItem);
                }

                Map<String, Object> preview = new LinkedHashMap<>();

                preview.put("employeeId", employee.getEmployeeId());
                preview.put("payCycleId", payCycle.getPayCycleId());
                preview.put("cycleStartDate", payCycle.getStartDate());
                preview.put("cycleEndDate", payCycle.getEndDate());

                preview.put("baseRegularPay", baseRegularPay);
                preview.put("attendanceDeduction", attendanceDeduction);
                preview.put("regularPay", regularPay);
                preview.put("approvedOvertimeCount", approvedOvertime.size());
                preview.put("overtimePay", overtimePay);
                preview.put("bonusAmount", bonusAmount);
                preview.put("grossPay", grossPay);
                preview.put("taxAmount", taxAmount);
                preview.put("otherDeductions", otherDeductions);
                preview.put("netPay", netPay);

                preview.put("attendanceBreakdown", attendanceBreakdown);

                preview.put(
                                "note",
                                "Preview only. No payroll record was created or modified.");

                return preview;
        }

        public Payroll createPayroll(
                        Integer employeeId,
                        Integer payCycleId,
                        Payroll payrollDetails) {

                Employee employee = employeeRepository
                                .findById(employeeId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Employee not found"));

                PayCycle payCycle = payCycleRepository
                                .findById(payCycleId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Pay cycle not found"));

                if (payrollRepository
                                .findByEmployeeEmployeeIdAndPayCyclePayCycleId(
                                                employeeId,
                                                payCycleId)
                                .isPresent()) {

                        throw new IllegalArgumentException(
                                        "Payroll already exists for this employee and pay cycle");
                }

                payrollDetails.setEmployee(employee);
                payrollDetails.setPayCycle(payCycle);

                /*
                 * Calculate the employee's regular pay for the
                 * selected pay cycle.
                 */
                BigDecimal regularPay = payrollCalculationService.calculateCycleBasePay(
                                employee,
                                payCycle.getCycleType());

                /*
                 * Adjust regular pay based on unpaid attendance.
                 */
                BigDecimal attendanceDeduction = calculateAttendanceDeduction(
                                employee,
                                payCycle);

                regularPay = regularPay
                                .subtract(attendanceDeduction)
                                .max(ZERO)
                                .setScale(2, RoundingMode.HALF_UP);

                payrollDetails.setRegularPay(regularPay);

                /*
                 * Only APPROVED overtime records within the
                 * selected pay cycle are included.
                 */
                List<Overtime> approvedOvertime = overtimeRepository
                                .findByEmployeeEmployeeIdAndOvertimeDateBetweenAndStatus(
                                                employeeId,
                                                payCycle.getStartDate(),
                                                payCycle.getEndDate(),
                                                Overtime.Status.APPROVED);

                BigDecimal overtimePay = approvedOvertime.stream()
                                .map(Overtime::getOvertimeAmount)
                                .filter(amount -> amount != null)
                                .reduce(
                                                BigDecimal.ZERO,
                                                BigDecimal::add)
                                .setScale(
                                                2,
                                                RoundingMode.HALF_UP);

                payrollDetails.setOvertimePay(overtimePay);

                /*
                 * Bonuses will be connected in the next step.
                 */
                BigDecimal bonusAmount = ZERO;

                payrollDetails.setBonusAmount(bonusAmount);

                /*
                 * Gross Pay =
                 * Regular Pay + Overtime Pay + Bonuses
                 */
                BigDecimal grossPay = regularPay
                                .add(overtimePay)
                                .add(bonusAmount)
                                .setScale(
                                                2,
                                                RoundingMode.HALF_UP);

                payrollDetails.setGrossPay(grossPay);

                /*
                 * Tax and other deductions will be connected
                 * after the basic payroll calculation is working.
                 */
                BigDecimal taxAmount = ZERO;
                BigDecimal deductionAmount = ZERO;

                payrollDetails.setTaxAmount(taxAmount);
                payrollDetails.setDeductionAmount(deductionAmount);

                /*
                 * Net Pay =
                 * Gross Pay - Tax - Other Deductions
                 */
                BigDecimal netPay = grossPay
                                .subtract(taxAmount)
                                .subtract(deductionAmount)
                                .max(ZERO)
                                .setScale(
                                                2,
                                                RoundingMode.HALF_UP);

                payrollDetails.setNetPay(netPay);

                return payrollRepository.save(payrollDetails);
        }

        private BigDecimal calculateAttendanceDeduction(
                        Employee employee,
                        PayCycle payCycle) {

                List<Attendance> attendanceRecords = attendanceRepository.findByEmployeeEmployeeId(
                                employee.getEmployeeId());

                BigDecimal hourlyRate = payrollCalculationService.calculateHourlyRate(employee);

                BigDecimal regularHoursPerDay;

                try {
                        regularHoursPerDay = new BigDecimal(
                                        systemSettingService.getSettingValue(
                                                        "regular_hours_per_day"));

                        if (regularHoursPerDay.compareTo(BigDecimal.ZERO) <= 0
                                        || regularHoursPerDay.compareTo(
                                                        new BigDecimal("24")) > 0) {
                                throw new IllegalArgumentException(
                                                "regular_hours_per_day must be greater than 0 and at most 24");
                        }
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                        "Invalid regular_hours_per_day setting");
                }

                BigDecimal deduction = BigDecimal.ZERO;

                for (Attendance attendance : attendanceRecords) {

                        LocalDate attendanceDate = attendance.getAttendanceDate();

                        if (attendanceDate == null
                                        || attendanceDate.isBefore(payCycle.getStartDate())
                                        || attendanceDate.isAfter(payCycle.getEndDate())) {
                                continue;
                        }

                        if (attendance.getStatus() == null) {
                                continue;
                        }

                        BigDecimal unpaidHours;

                        switch (attendance.getStatus()) {
                                case ABSENT:
                                        // An explicitly recorded absence is treated as
                                        // a full unpaid workday in this initial version.
                                        unpaidHours = regularHoursPerDay;
                                        break;

                                case PRESENT:
                                case LATE:
                                        // Use recorded hours so lost time is not deducted
                                        // a second time from the same attendance record.
                                        BigDecimal recordedHours = attendance.getRegularHours();

                                        if (recordedHours == null) {
                                                recordedHours = BigDecimal.ZERO;
                                        }

                                        unpaidHours = regularHoursPerDay
                                                        .subtract(recordedHours)
                                                        .max(BigDecimal.ZERO);
                                        break;

                                case LEAVE:
                                        // Leave cannot safely be treated as unpaid until
                                        // the system records whether it is paid or unpaid.
                                        unpaidHours = BigDecimal.ZERO;
                                        break;

                                default:
                                        unpaidHours = BigDecimal.ZERO;
                        }

                        if (unpaidHours.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal dailyDeduction = hourlyRate
                                                .multiply(unpaidHours)
                                                .setScale(2, RoundingMode.HALF_UP);

                                deduction = deduction.add(dailyDeduction);
                        }
                }

                return deduction.setScale(2, RoundingMode.HALF_UP);
        }

        public Payroll updatePayroll(
                        Integer id,
                        Payroll payrollDetails) {

                Payroll payroll = payrollRepository
                                .findById(id)
                                .orElseThrow(() -> new RuntimeException(
                                                "Payroll not found"));

                payroll.setRegularPay(
                                payrollDetails.getRegularPay());

                payroll.setOvertimePay(
                                payrollDetails.getOvertimePay());

                payroll.setBonusAmount(
                                payrollDetails.getBonusAmount());

                payroll.setGrossPay(
                                payrollDetails.getGrossPay());

                payroll.setTaxAmount(
                                payrollDetails.getTaxAmount());

                payroll.setDeductionAmount(
                                payrollDetails.getDeductionAmount());

                payroll.setNetPay(
                                payrollDetails.getNetPay());

                payroll.setStatus(
                                payrollDetails.getStatus());

                payroll.setProcessedAt(
                                payrollDetails.getProcessedAt());

                return payrollRepository.save(payroll);
        }

        public Payroll processPayroll(Integer id, Integer userId) {
                Payroll payroll = payrollRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Payroll not found"));

                if (payroll.getStatus() != Payroll.Status.DRAFT) {
                        throw new IllegalArgumentException(
                                        "Only DRAFT payroll can be processed");
                }

                if (userId == null) {
                        throw new IllegalArgumentException(
                                        "Processing user is required");
                }

                com.employger.entity.User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                payroll.setStatus(Payroll.Status.PROCESSED);
                payroll.setProcessedAt(java.time.LocalDateTime.now());
                payroll.setProcessedBy(user);

                return payrollRepository.save(payroll);
        }

        public Payroll markPayrollAsPaid(
                        Integer id,
                        String paymentMethod,
                        String paymentReference) {

                Payroll payroll = payrollRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Payroll not found"));

                if (payroll.getStatus() != Payroll.Status.PROCESSED) {
                        throw new IllegalArgumentException(
                                        "Only PROCESSED payroll can be marked as paid");
                }

                if (paymentMethod == null || paymentMethod.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Payment method is required");
                }

                payroll.setStatus(Payroll.Status.PAID);
                payroll.setPaymentDate(java.time.LocalDateTime.now());
                payroll.setPaymentMethod(paymentMethod);
                payroll.setPaymentReference(paymentReference);

                return payrollRepository.save(payroll);
        }

        public void deletePayroll(Integer id) {

                if (!payrollRepository.existsById(id)) {
                        throw new RuntimeException(
                                        "Payroll not found");
                }

                payrollRepository.deleteById(id);
        }
}