package com.employger.service;

import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.User;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for payroll calculation and lifecycle rules. No database is
 * used; repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

    @Mock
    private PayrollRepository payrollRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PayCycleRepository payCycleRepository;
    @Mock
    private OvertimeRepository overtimeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SystemSettingService systemSettingService;

    private PayrollService payrollService;

    private Employee employee;
    private PayCycle firstHalf;
    private PayCycle wholeMonth;

    @BeforeEach
    void setUp() {
        lenient().when(systemSettingService.getSettingValue("factor_rate")).thenReturn("261");
        lenient().when(systemSettingService.getSettingValue("regular_hours_per_day")).thenReturn("8");
        lenient().when(systemSettingService.getSettingValue("default_overtime_rate")).thenReturn("1.25");

        PayrollCalculationService calculationService =
                new PayrollCalculationService(systemSettingService);

        payrollService = new PayrollService(
                payrollRepository,
                employeeRepository,
                payCycleRepository,
                overtimeRepository,
                attendanceRepository,
                userRepository,
                calculationService,
                systemSettingService);

        employee = new Employee();
        employee.setEmployeeId(35);
        employee.setBaseSalary(new BigDecimal("25000.00"));

        firstHalf = payCycle(1, "October 1-15, 2026", OCT_1, OCT_15, PayCycle.CycleType.SEMI_MONTHLY);
        wholeMonth = payCycle(2, "October 2026", OCT_1, OCT_31, PayCycle.CycleType.MONTHLY);

        lenient().when(employeeRepository.findById(35)).thenReturn(Optional.of(employee));
        lenient().when(payCycleRepository.findById(1)).thenReturn(Optional.of(firstHalf));
        lenient().when(payCycleRepository.findById(2)).thenReturn(Optional.of(wholeMonth));
        lenient().when(payrollRepository.save(any(Payroll.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------- calculation ----------

    @Test
    void createPayroll_reproducesStoredPayroll6Figures() {
        stubOctoberAttendanceAndOvertime();

        Payroll payroll = payrollService.createPayroll(35, 1);

        // Values match payroll #6 in employger_db, verified by hand:
        // base 12,500.00 - deduction (0.25h + 8h) x 143.68 = 11,314.64
        assertThat(payroll.getRegularPay()).isEqualByComparingTo("11314.64");
        assertThat(payroll.getOvertimePay()).isEqualByComparingTo("898.00");
        assertThat(payroll.getGrossPay()).isEqualByComparingTo("12212.64");
        assertThat(payroll.getNetPay()).isEqualByComparingTo("12212.64");
        assertThat(payroll.getStatus()).isEqualTo(Payroll.Status.DRAFT);
        assertThat(payroll.getPayrollId()).isNull();
        assertThat(payroll.getProcessedBy()).isNull();
        assertThat(payroll.getPaymentDate()).isNull();
    }

    @Test
    void previewPayroll_matchesCreatedPayrollAndSavesNothing() {
        stubOctoberAttendanceAndOvertime();

        Map<String, Object> preview = payrollService.previewPayroll(35, 1);

        verify(payrollRepository, never()).save(any());

        Payroll created = payrollService.createPayroll(35, 1);

        assertThat((BigDecimal) preview.get("regularPay")).isEqualByComparingTo(created.getRegularPay());
        assertThat((BigDecimal) preview.get("overtimePay")).isEqualByComparingTo(created.getOvertimePay());
        assertThat((BigDecimal) preview.get("grossPay")).isEqualByComparingTo(created.getGrossPay());
        assertThat((BigDecimal) preview.get("netPay")).isEqualByComparingTo(created.getNetPay());
        assertThat((BigDecimal) preview.get("attendanceDeduction")).isEqualByComparingTo("1185.36");
    }

    // ---------- overlapping pay cycles ----------

    @Test
    void createPayroll_rejectsPayCycleOverlappingExistingPayroll() {
        Payroll paidFirstHalf = payroll(6, firstHalf, Payroll.Status.PAID);

        when(payrollRepository.findByEmployeeEmployeeIdAndPayCyclePayCycleId(35, 2))
                .thenReturn(Optional.empty());
        when(payrollRepository
                .findByEmployeeEmployeeIdAndPayCycleStartDateLessThanEqualAndPayCycleEndDateGreaterThanEqual(
                        35, OCT_31, OCT_1))
                .thenReturn(List.of(paidFirstHalf));

        assertThatThrownBy(() -> payrollService.createPayroll(35, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#6")
                .hasMessageContaining("overlaps");

        verify(payrollRepository, never()).save(any());
    }

    @Test
    void createPayroll_rejectsDuplicateForSamePayCycle() {
        when(payrollRepository.findByEmployeeEmployeeIdAndPayCyclePayCycleId(35, 1))
                .thenReturn(Optional.of(payroll(6, firstHalf, Payroll.Status.PAID)));

        assertThatThrownBy(() -> payrollService.createPayroll(35, 1))
                .isInstanceOf(IllegalStateException.class);

        verify(payrollRepository, never()).save(any());
    }

    @Test
    void processPayroll_rejectsDraftOverlappingPaidPayroll() {
        // Mirrors payroll #7 (DRAFT, whole month) vs #6 (PAID, first half).
        Payroll draftMonth = payroll(7, wholeMonth, Payroll.Status.DRAFT);
        Payroll paidFirstHalf = payroll(6, firstHalf, Payroll.Status.PAID);

        when(payrollRepository.findById(7)).thenReturn(Optional.of(draftMonth));
        when(payrollRepository
                .findByEmployeeEmployeeIdAndPayCycleStartDateLessThanEqualAndPayCycleEndDateGreaterThanEqual(
                        35, OCT_31, OCT_1))
                .thenReturn(List.of(draftMonth, paidFirstHalf));

        assertThatThrownBy(() -> payrollService.processPayroll(7, "hr"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#6");

        assertThat(draftMonth.getStatus()).isEqualTo(Payroll.Status.DRAFT);
        verify(payrollRepository, never()).save(any());
    }

    @Test
    void processPayroll_recordsAuthenticatedUser() {
        Payroll draft = payroll(6, firstHalf, Payroll.Status.DRAFT);
        User hr = new User();
        hr.setUsername("hr");

        when(payrollRepository.findById(6)).thenReturn(Optional.of(draft));
        when(payrollRepository
                .findByEmployeeEmployeeIdAndPayCycleStartDateLessThanEqualAndPayCycleEndDateGreaterThanEqual(
                        35, OCT_15, OCT_1))
                .thenReturn(List.of(draft));
        when(userRepository.findByUsername("hr")).thenReturn(Optional.of(hr));

        Payroll processed = payrollService.processPayroll(6, "hr");

        assertThat(processed.getStatus()).isEqualTo(Payroll.Status.PROCESSED);
        assertThat(processed.getProcessedBy()).isSameAs(hr);
        assertThat(processed.getProcessedAt()).isNotNull();
    }

    // ---------- eligibility ----------

    @Test
    void createPayroll_rejectsClosedOrProcessedPayCycle() {
        firstHalf.setStatus(PayCycle.Status.CLOSED);

        assertThatThrownBy(() -> payrollService.createPayroll(35, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED");

        firstHalf.setStatus(PayCycle.Status.PROCESSED);

        assertThatThrownBy(() -> payrollService.createPayroll(35, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROCESSED");

        verify(payrollRepository, never()).save(any());
    }

    @Test
    void createPayroll_rejectsInactiveEmployee() {
        employee.setEmploymentStatus(Employee.EmploymentStatus.INACTIVE);

        assertThatThrownBy(() -> payrollService.createPayroll(35, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");

        verify(payrollRepository, never()).save(any());
    }

    @Test
    void createPayroll_rejectsEmployeeHiredAfterCycleEnds() {
        employee.setDateHired(LocalDate.of(2026, 10, 16));

        assertThatThrownBy(() -> payrollService.createPayroll(35, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hired");

        verify(payrollRepository, never()).save(any());
    }

    // ---------- lifecycle ----------

    @Test
    void updatePayroll_rejectsPaidPayrollAndLeavesItUnchanged() {
        Payroll paid = payroll(6, firstHalf, Payroll.Status.PAID);
        paid.setNetPay(new BigDecimal("12212.64"));

        when(payrollRepository.findById(6)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> payrollService.updatePayroll(6))
                .isInstanceOf(IllegalStateException.class);

        assertThat(paid.getStatus()).isEqualTo(Payroll.Status.PAID);
        assertThat(paid.getNetPay()).isEqualByComparingTo("12212.64");
        verify(payrollRepository, never()).save(any());
    }

    @Test
    void updatePayroll_recalculatesDraftFromRecords() {
        stubOctoberAttendanceAndOvertime();
        Payroll draft = payroll(6, firstHalf, Payroll.Status.DRAFT);
        draft.setNetPay(new BigDecimal("999999.99"));

        when(payrollRepository.findById(6)).thenReturn(Optional.of(draft));

        Payroll updated = payrollService.updatePayroll(6);

        assertThat(updated.getNetPay()).isEqualByComparingTo("12212.64");
        assertThat(updated.getStatus()).isEqualTo(Payroll.Status.DRAFT);
    }

    @Test
    void deletePayroll_rejectsProcessedAndPaidPayroll() {
        when(payrollRepository.findById(6))
                .thenReturn(Optional.of(payroll(6, firstHalf, Payroll.Status.PAID)));
        when(payrollRepository.findById(9))
                .thenReturn(Optional.of(payroll(9, firstHalf, Payroll.Status.PROCESSED)));

        assertThatThrownBy(() -> payrollService.deletePayroll(6))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payrollService.deletePayroll(9))
                .isInstanceOf(IllegalStateException.class);

        verify(payrollRepository, never()).delete(any());
        verify(payrollRepository, never()).deleteById(anyInt());
    }

    @Test
    void deletePayroll_allowsDraft() {
        Payroll draft = payroll(7, wholeMonth, Payroll.Status.DRAFT);
        when(payrollRepository.findById(7)).thenReturn(Optional.of(draft));

        payrollService.deletePayroll(7);

        verify(payrollRepository).delete(draft);
    }

    @Test
    void markPayrollAsPaid_rejectsDraft() {
        when(payrollRepository.findById(7))
                .thenReturn(Optional.of(payroll(7, wholeMonth, Payroll.Status.DRAFT)));

        assertThatThrownBy(() -> payrollService.markPayrollAsPaid(7, "BANK_TRANSFER", null))
                .isInstanceOf(IllegalStateException.class);

        verify(payrollRepository, never()).save(any());
    }

    // ---------- helpers ----------

    private void stubOctoberAttendanceAndOvertime() {
        when(attendanceRepository.findByEmployeeEmployeeId(35)).thenReturn(List.of(
                attendance(LocalDate.of(2026, 10, 3), Attendance.Status.PRESENT, "8.00"),
                attendance(LocalDate.of(2026, 10, 4), Attendance.Status.LATE, "7.75"),
                attendance(LocalDate.of(2026, 10, 5), Attendance.Status.ABSENT, "0.00")));

        when(overtimeRepository.findByEmployeeEmployeeIdAndOvertimeDateBetweenAndStatus(
                35, OCT_1, OCT_15, Overtime.Status.APPROVED)).thenReturn(List.of(
                        approvedOvertime("0.00"),
                        approvedOvertime("179.60"),
                        approvedOvertime("359.20"),
                        approvedOvertime("359.20")));
    }

    private static PayCycle payCycle(
            Integer id, String name, LocalDate start, LocalDate end, PayCycle.CycleType type) {
        PayCycle cycle = new PayCycle();
        cycle.setPayCycleId(id);
        cycle.setCycleName(name);
        cycle.setStartDate(start);
        cycle.setEndDate(end);
        cycle.setPayDate(end);
        cycle.setCycleType(type);
        return cycle;
    }

    private Payroll payroll(Integer id, PayCycle cycle, Payroll.Status status) {
        Payroll payroll = new Payroll();
        payroll.setPayrollId(id);
        payroll.setEmployee(employee);
        payroll.setPayCycle(cycle);
        payroll.setStatus(status);
        return payroll;
    }

    private Attendance attendance(LocalDate date, Attendance.Status status, String hours) {
        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(date);
        attendance.setStatus(status);
        attendance.setRegularHours(new BigDecimal(hours));
        return attendance;
    }

    private Overtime approvedOvertime(String amount) {
        Overtime overtime = new Overtime();
        overtime.setEmployee(employee);
        overtime.setStatus(Overtime.Status.APPROVED);
        overtime.setOvertimeAmount(new BigDecimal(amount));
        return overtime;
    }
}
