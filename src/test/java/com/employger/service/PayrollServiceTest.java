package com.employger.service;

import com.employger.entity.ApprovalRequest;
import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.User;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EarningRepository;
import com.employger.repository.HolidayRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PriorEmployerCompensationRepository;
import com.employger.repository.UserRepository;
import com.employger.service.contribution.PagIbigContribution;
import com.employger.service.contribution.PhilHealthContribution;
import com.employger.service.contribution.SssContribution;
import com.employger.service.earning.ClothingAllowance;
import com.employger.service.earning.MedicalCashAllowance;
import com.employger.service.earning.MonetizedLeave;
import com.employger.service.earning.RiceSubsidy;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for payroll calculation and lifecycle rules. No database is
 * used; repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
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
    @Mock
    private AuditService auditService;
    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private EarningRepository earningRepository;
    @Mock
    private PriorEmployerCompensationRepository priorEmployerRepository;

    private PayrollService payrollService;

    private Employee employee;
    private PayCycle firstHalf;
    private PayCycle wholeMonth;

    @BeforeEach
    void setUp() {
        lenient().when(systemSettingService.getSettingValue("factor_rate")).thenReturn("261");
        lenient().when(systemSettingService.getSettingValue("regular_hours_per_day")).thenReturn("8");
        lenient().when(systemSettingService.getSettingValue("default_overtime_rate")).thenReturn("1.25");
        lenient().when(systemSettingService.findSettingValue("rest_days"))
                .thenReturn(Optional.of("SATURDAY,SUNDAY"));

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
                auditService,
                new StatutoryDeductionService(systemSettingService, calculationService, List.of(
                        new SssContribution(systemSettingService),
                        new PhilHealthContribution(systemSettingService),
                        new PagIbigContribution(systemSettingService))),
                new HolidayService(holidayRepository, auditService),
                new EarningsCalculator(earningRepository, payrollRepository, priorEmployerRepository, calculationService,
                        List.of(new RiceSubsidy(calculationService), new ClothingAllowance(calculationService),
                                new MedicalCashAllowance(calculationService), new MonetizedLeave(calculationService))),
                earningRepository,
                new YearEndTaxService(payrollRepository, priorEmployerRepository));

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
    void createPayroll_appliesRestDaysAndRoundsOnlyFinalAmounts() {
        stubOctoberAttendanceAndOvertime();

        Payroll payroll = payrollService.createPayroll(35, 1);

        // Same inputs as paid payroll #6 in employger_db, under the policy
        // adopted on 2026-10-22. #6 itself (11,314.64) is never recalculated.
        //   hourly rate = 25,000 x 12 / 261 / 8 = 143.678161 (not rounded)
        //   Oct 3 (Sat) and Oct 4 (Sun) are rest days: no deduction
        //   Oct 5 ABSENT: 8 h x 143.678161 = 1,149.43
        //   regular pay = 12,500.00 - 1,149.43 = 11,350.57
        // Since 2026-10-23, work on a rest day is paid at 130%: Oct 3 (8 h)
        // and Oct 4 (7.75 h) = 15.75 h x 143.678161 x 1.30 = 2,941.81.
        assertThat(payroll.getAttendanceDeduction()).isEqualByComparingTo("1149.43");
        assertThat(payroll.getRegularPay()).isEqualByComparingTo("11350.57");
        assertThat(payroll.getHolidayPremiumPay()).isEqualByComparingTo("2941.81");
        assertThat(payroll.getOvertimePay()).isEqualByComparingTo("898.00");
        assertThat(payroll.getGrossPay()).isEqualByComparingTo("15190.38");
        assertThat(payroll.getNetPay()).isEqualByComparingTo("15190.38");
        assertThat(payroll.getStatus()).isEqualTo(Payroll.Status.DRAFT);
        assertThat(payroll.getProcessedBy()).isNull();
        assertThat(payroll.getPaymentDate()).isNull();
    }

    @Test
    void createPayroll_recordsCalculationSnapshotAndLinksOvertime() {
        List<Overtime> overtime = stubOctoberAttendanceAndOvertime();

        Payroll payroll = payrollService.createPayroll(35, 1);

        assertThat(payroll.getBasePay()).isEqualByComparingTo("12500.00");
        assertThat(payroll.getHourlyRate()).isEqualByComparingTo("143.678161");
        assertThat(payroll.getFactorRate()).isEqualByComparingTo("261");
        assertThat(payroll.getRegularHoursPerDay()).isEqualByComparingTo("8");
        assertThat(payroll.getWorkingDaysInCycle()).isEqualTo(11);
        assertThat(payroll.getWorkingDaysEmployed()).isEqualTo(11);
        assertThat(payroll.getCalculatedAt()).isNotNull();
        assertThat(overtime).allSatisfy(record -> assertThat(record.getPayroll()).isSameAs(payroll));
    }

    @Test
    void calculation_warnsAboutWorkingDaysWithoutAttendance() {
        stubOctoberAttendanceAndOvertime();

        Map<String, Object> preview = payrollService.previewPayroll(35, 1);

        // Weekdays Oct 1-15 without a record (Oct 3-5 are recorded).
        assertThat((List<Object>) preview.get("missingAttendanceDates")).containsExactly(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13),
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15));
        assertThat((List<?>) preview.get("warnings")).anySatisfy(
                warning -> assertThat(warning.toString()).contains("10 working day(s)"));
    }

    @Test
    void calculation_proratesMidCycleHireByWorkingDays() {
        employee.setDateHired(LocalDate.of(2026, 10, 6));

        Payroll payroll = payrollService.createPayroll(35, 1);

        // Oct 1-15 has 11 working days; Oct 6-15 has 8.
        // 12,500 x 8 / 11 = 9,090.909090... -> 9,090.91
        assertThat(payroll.getWorkingDaysEmployed()).isEqualTo(8);
        assertThat(payroll.getBasePay()).isEqualByComparingTo("9090.91");
        assertThat(payroll.getCalculationNotes()).contains("prorated");
    }

    @Test
    void calculation_proratesLeaverUpToTheirLastDay() {
        employee.setSeparationDate(LocalDate.of(2026, 10, 9));

        Payroll payroll = payrollService.createPayroll(35, 1);

        // Oct 1-15 has 11 working days; Oct 1-9 has 7.
        // 12,500 x 7 / 11 = 7,954.545454... -> 7,954.55
        assertThat(payroll.getWorkingDaysEmployed()).isEqualTo(7);
        assertThat(payroll.getBasePay()).isEqualByComparingTo("7954.55");
        assertThat(payroll.getCalculationNotes())
                .contains("to 2026-10-09 (last day)")
                // Days after the last day aren't reported as missing attendance.
                .contains("7 working day(s)");
    }

    @Test
    void createPayroll_paysAnInactiveLeaverTheirFinalCycleOnly() {
        employee.setEmploymentStatus(Employee.EmploymentStatus.INACTIVE);
        employee.setSeparationDate(LocalDate.of(2026, 10, 9));

        assertThat(payrollService.createPayroll(35, 1).getWorkingDaysEmployed()).isEqualTo(7);

        employee.setSeparationDate(LocalDate.of(2026, 9, 30));

        assertThatThrownBy(() -> payrollService.createPayroll(35, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");

        employee.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);

        assertThatThrownBy(() -> payrollService.createPayroll(35, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last day was 2026-09-30");
    }

    @Test
    void createPayroll_appliesStatutoryDeductionsWhenEnabled() {
        enableStatutoryDeductions();

        Payroll payroll = payrollService.createPayroll(35, 1);

        // Gross 12,500.00 (no attendance or overtime recorded).
        // Contributions 625.00 + 312.50 + 100.00 = 1,037.50; tax 156.83.
        assertThat(payroll.getGrossPay()).isEqualByComparingTo("12500.00");
        assertThat(payroll.getSssContribution()).isEqualByComparingTo("625.00");
        assertThat(payroll.getPhilHealthContribution()).isEqualByComparingTo("312.50");
        assertThat(payroll.getPagIbigContribution()).isEqualByComparingTo("100.00");
        assertThat(payroll.getDeductionAmount()).isEqualByComparingTo("1037.50");
        assertThat(payroll.getTaxableCompensation()).isEqualByComparingTo("11462.50");
        assertThat(payroll.getTaxAmount()).isEqualByComparingTo("156.83");
        assertThat(payroll.getNetPay()).isEqualByComparingTo("11305.67");

        // Employer shares are recorded but do not change net pay.
        assertThat(payroll.getSssEmployerContribution()).isEqualByComparingTo("1250.00");
        assertThat(payroll.getSssEcContribution()).isEqualByComparingTo("15.00");
        assertThat(payroll.getPhilHealthEmployerContribution()).isEqualByComparingTo("312.50");
        assertThat(payroll.getPagIbigEmployerContribution()).isEqualByComparingTo("100.00");
    }

    @Test
    void createPayroll_minimumWageEarnerHasContributionsButNoWithholdingTax() {
        enableStatutoryDeductions();
        employee.setMinimumWageEarner(true);

        Payroll payroll = payrollService.createPayroll(35, 1);

        // Same pay as above; regular pay is exempt from withholding tax.
        assertThat(payroll.getGrossPay()).isEqualByComparingTo("12500.00");
        assertThat(payroll.getDeductionAmount()).isEqualByComparingTo("1037.50");
        assertThat(payroll.getTaxableCompensation()).isEqualByComparingTo("0.00");
        assertThat(payroll.getTaxAmount()).isEqualByComparingTo("0.00");
        assertThat(payroll.getNetPay()).isEqualByComparingTo("11462.50");
        assertThat(payroll.getCalculationNotes()).contains("Minimum wage earner");
    }

    private void enableStatutoryDeductions() {
        lenient().when(systemSettingService.findSettingValue("statutory_deductions_enabled"))
                .thenReturn(Optional.of("true"));
        Map.ofEntries(
                Map.entry("sss_employee_rate", "0.05"),
                Map.entry("sss_msc_minimum", "5000"),
                Map.entry("sss_msc_maximum", "35000"),
                Map.entry("sss_msc_step", "500"),
                Map.entry("philhealth_premium_rate", "0.05"),
                Map.entry("philhealth_salary_floor", "10000"),
                Map.entry("philhealth_salary_ceiling", "100000"),
                Map.entry("philhealth_employee_share", "0.50"),
                Map.entry("pagibig_employee_rate", "0.02"),
                Map.entry("pagibig_low_salary_rate", "0.01"),
                Map.entry("pagibig_low_salary_threshold", "1500"),
                Map.entry("pagibig_max_fund_salary", "10000"),
                Map.entry("sss_employer_rate", "0.10"),
                Map.entry("sss_ec_msc_threshold", "15000"),
                Map.entry("sss_ec_amount_low", "10"),
                Map.entry("sss_ec_amount_high", "30"),
                Map.entry("pagibig_employer_rate", "0.02"))
                .forEach((name, value) -> lenient().when(systemSettingService.getSettingValue(name)).thenReturn(value));
    }

    @Test
    void calculation_excludesOvertimeAlreadyPaidByAnotherPayroll() {
        Payroll otherPayroll = payroll(99, firstHalf, Payroll.Status.PAID);
        Overtime alreadyPaid = approvedOvertime("500.00");
        alreadyPaid.setPayroll(otherPayroll);
        Overtime unpaid = approvedOvertime("100.00");

        when(overtimeRepository.findByEmployeeEmployeeIdAndOvertimeDateBetweenAndStatus(
                35, OCT_1, OCT_15, ApprovalRequest.Status.APPROVED)).thenReturn(List.of(alreadyPaid, unpaid));

        Payroll payroll = payrollService.createPayroll(35, 1);

        assertThat(payroll.getOvertimePay()).isEqualByComparingTo("100.00");
        assertThat(alreadyPaid.getPayroll()).isSameAs(otherPayroll);
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
        assertThat((BigDecimal) preview.get("attendanceDeduction")).isEqualByComparingTo("1149.43");
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

        assertThat(updated.getNetPay()).isEqualByComparingTo("15190.38");
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

    private List<Overtime> stubOctoberAttendanceAndOvertime() {
        // The calculation also reads two weeks back (the day-before-holiday rule).
        when(attendanceRepository.findByEmployeeEmployeeIdAndAttendanceDateBetween(eq(35), any(), eq(OCT_15))).thenReturn(List.of(
                attendance(LocalDate.of(2026, 10, 3), Attendance.Status.PRESENT, "8.00"),
                attendance(LocalDate.of(2026, 10, 4), Attendance.Status.LATE, "7.75"),
                attendance(LocalDate.of(2026, 10, 5), Attendance.Status.ABSENT, "0.00")));

        List<Overtime> overtime = List.of(
                approvedOvertime("0.00"),
                approvedOvertime("179.60"),
                approvedOvertime("359.20"),
                approvedOvertime("359.20"));

        when(overtimeRepository.findByEmployeeEmployeeIdAndOvertimeDateBetweenAndStatus(
                35, OCT_1, OCT_15, ApprovalRequest.Status.APPROVED)).thenReturn(overtime);

        return overtime;
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
        ReflectionTestUtils.setField(payroll, "status", status);
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
        ReflectionTestUtils.setField(overtime, "status", ApprovalRequest.Status.APPROVED);
        overtime.setOvertimeDate(LocalDate.of(2026, 10, 3));
        overtime.setOvertimeAmount(new BigDecimal(amount));
        return overtime;
    }
}
