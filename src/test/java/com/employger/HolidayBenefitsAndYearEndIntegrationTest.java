package com.employger;

import com.employger.entity.ApprovalRequest;
import com.employger.entity.Attendance;
import com.employger.entity.Earning;
import com.employger.entity.Employee;
import com.employger.entity.Holiday;
import com.employger.entity.Overtime;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.PriorEmployerCompensation;
import com.employger.entity.SystemSetting;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EarningRepository;
import com.employger.repository.DepartmentRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.HolidayRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PriorEmployerCompensationRepository;
import com.employger.repository.SystemSettingRepository;
import com.employger.entity.User;
import com.employger.repository.UserRepository;
import com.employger.service.EarningService;
import com.employger.service.ExecutiveDashboardService;
import com.employger.service.PayrollService;
import com.employger.service.ThirteenthMonthStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holiday, rest-day and night premiums, earnings and the 90,000 cap, year-end
 * annualization and 13th month generation, against the in-memory database.
 *
 * Salary 26,100 a month gives an hourly rate of exactly 150.00
 * (26,100 x 12 / 261 / 8). October 1, 2026 is a Thursday, so Oct 10-11 is a
 * weekend and Oct 13-14 are a Tuesday and Wednesday.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("unchecked")
class HolidayBenefitsAndYearEndIntegrationTest {

    private static final LocalDate OCT_10_SAT = LocalDate.of(2026, 10, 10);
    private static final LocalDate OCT_11_SUN = LocalDate.of(2026, 10, 11);
    private static final LocalDate OCT_13_TUE = LocalDate.of(2026, 10, 13);
    private static final LocalDate OCT_14_WED = LocalDate.of(2026, 10, 14);

    @Autowired
    private PayrollService payrollService;
    @Autowired
    private EarningService earningService;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ExecutiveDashboardService executiveDashboardService;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private PayrollRepository payrollRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private OvertimeRepository overtimeRepository;
    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private EarningRepository earningRepository;
    @Autowired
    private PriorEmployerCompensationRepository priorEmployerRepository;
    @Autowired
    private SystemSettingRepository systemSettingRepository;

    private Employee employee;
    private PayCycle october;

    @BeforeEach
    void setUp() {
        setting("factor_rate", "261");
        setting("regular_hours_per_day", "8");
        setting("default_overtime_rate", "1.25");
        setting("standard_start_time", "08:00");
        setting("standard_end_time", "17:00");
        setting("unpaid_break_minutes", "60");
        setting("late_grace_period_minutes", "0");

        employee = employee("worker@example.test");
        october = cycle("October 1-15, 2026", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15),
                PayCycle.CycleType.SEMI_MONTHLY);
    }

    // ---------- holiday and rest-day premiums ----------

    @Test
    void workedRegularHolidayAddsOneHundredPercent() {
        holiday(OCT_14_WED, Holiday.Type.REGULAR);
        attendance(OCT_14_WED, Attendance.Status.PRESENT, "8", "08:00", "17:00");

        // 8 h x 150 x (2.00 - 1.00 already in base pay)
        assertThat(preview().get("holidayPremiumPay")).isEqualTo(money("1200.00"));
    }

    @Test
    void workedSpecialDayAddsThirtyPercentAndUnworkedIsNotDeducted() {
        holiday(OCT_14_WED, Holiday.Type.SPECIAL);
        attendance(OCT_14_WED, Attendance.Status.PRESENT, "8", "08:00", "17:00");
        holiday(OCT_13_TUE, Holiday.Type.SPECIAL);

        Map<String, Object> preview = preview();

        // Oct 14: 8 h x 150 x 0.30. Oct 13 not worked: no deduction.
        assertThat(preview.get("holidayPremiumPay")).isEqualTo(money("360.00"));
        assertThat(preview.get("attendanceDeduction")).isEqualTo(money("0.00"));
        assertThat((List<LocalDate>) preview.get("missingAttendanceDates")).doesNotContain(OCT_13_TUE);
    }

    @Test
    void unworkedDoubleHolidayPaysTwoHundredPercent() {
        holiday(OCT_14_WED, Holiday.Type.DOUBLE);

        // Base pay covers 100%; 8 h x 150 x (2.00 - 1.00) is added.
        assertThat(preview().get("holidayPremiumPay")).isEqualTo(money("1200.00"));
    }

    @Test
    void absenceOnTheDayBeforeForfeitsRegularHolidayPay() {
        holiday(OCT_14_WED, Holiday.Type.REGULAR);
        attendance(OCT_13_TUE, Attendance.Status.ABSENT, "0", null, null);

        Map<String, Object> preview = preview();

        // Absent Oct 13 (8 h) plus the forfeited holiday (8 h) = 16 h x 150.
        assertThat(preview.get("attendanceDeduction")).isEqualTo(money("2400.00"));
        assertThat((List<String>) preview.get("warnings"))
                .anySatisfy(note -> assertThat(note).contains("No holiday pay for 2026-10-14"));
    }

    @Test
    void restDayWorkIsPaidInFullAtTheRestDayRate() {
        attendance(OCT_10_SAT, Attendance.Status.PRESENT, "8", "08:00", "17:00");
        holiday(OCT_11_SUN, Holiday.Type.REGULAR);
        attendance(OCT_11_SUN, Attendance.Status.PRESENT, "8", "08:00", "17:00");

        // Sat: 8 x 150 x 1.30 = 1,560. Sun regular holiday: 8 x 150 x 2.60 = 3,120.
        assertThat(preview().get("holidayPremiumPay")).isEqualTo(money("4680.00"));
    }

    // ---------- night shift differential ----------

    @Test
    void nightShiftEarnsTenPercentForHoursBetweenTenAndSix() {
        // 22:00 to 07:00, 8 regular hours plus the 1 h break.
        attendance(OCT_14_WED, Attendance.Status.PRESENT, "8", "22:00", "07:00");

        // 22:00-06:00 = 8 night hours x 150 x 10%
        assertThat(preview().get("nightDifferentialPay")).isEqualTo(money("120.00"));
    }

    @Test
    void nightDifferentialOnOvertimeUsesTheOvertimeRate() {
        Attendance day = attendance(OCT_14_WED, Attendance.Status.PRESENT, "8", "08:00", "23:00");
        approvedOvertime(day, "6", "1125.00");

        // 22:00-23:00 is overtime: 1 h x 150 x 1.25 x 10%
        Map<String, Object> preview = preview();
        assertThat(preview.get("nightDifferentialPay")).isEqualTo(money("18.75"));
        assertThat(preview.get("overtimePay")).isEqualTo(money("1125.00"));
    }

    // ---------- earnings, de minimis and the 90,000 cap ----------

    @Test
    void riceAboveTheMonthlyLimitCountsTowardTheBenefitsCap() {
        earning(Earning.Type.DE_MINIMIS_RICE, "2500.00", OCT_13_TUE);

        Map<String, Object> preview = preview();

        // 2,000 exempt as de minimis; the 500 excess is exempt under the cap.
        assertThat(preview.get("deMinimisPay")).isEqualTo(money("2500.00"));
        assertThat(preview.get("nonTaxableEarnings")).isEqualTo(money("2500.00"));
        assertThat((List<String>) preview.get("warnings"))
                .anySatisfy(note -> assertThat(note).contains("exceeds the de minimis limit"));
    }

    @Test
    void benefitsAboveNinetyThousandAreTaxableIncludingPriorEmployerUse() {
        prior(2026, "50000.00", "10000.00", "0.00");
        earning(Earning.Type.THIRTEENTH_MONTH, "85000.00", OCT_13_TUE);
        earning(Earning.Type.TAXABLE_ALLOWANCE, "1000.00", OCT_13_TUE);

        Map<String, Object> preview = preview();

        // Cap 90,000 - 10,000 used at the previous employer = 80,000 exempt.
        assertThat(preview.get("bonusAmount")).isEqualTo(money("86000.00"));
        assertThat(preview.get("nonTaxableEarnings")).isEqualTo(money("80000.00"));
    }

    @Test
    void processedPayrollStaysInItsDepartmentAfterTheEmployeeMoves() {
        employee.assignPosition(TestChoices.department(departmentRepository, "Finance"), null);
        employeeRepository.save(employee);
        PayCycle november = cycle("November 2026", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                PayCycle.CycleType.MONTHLY);

        Payroll payroll = payrollService.createPayroll(employee.getEmployeeId(), november.getPayCycleId());
        payrollService.processPayroll(payroll.getPayrollId(), processor().getUsername());

        employee.assignPosition(TestChoices.department(departmentRepository, "Sales"), null);
        employeeRepository.save(employee);

        ExecutiveDashboardService.ExecutiveSummary summary =
                executiveDashboardService.summary(java.time.YearMonth.of(2026, 11));

        assertThat(summary.departments())
                .filteredOn(row -> row.department().equals("Finance"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.activeEmployees()).isZero();
                    assertThat(row.amounts().get("grossPay")).isEqualByComparingTo(payroll.getGrossPay());
                });
        assertThat(summary.departments())
                .filteredOn(row -> row.department().equals("Sales"))
                .singleElement()
                .satisfies(row -> assertThat(row.amounts().get("grossPay")).isEqualByComparingTo("0"));
    }

    @Test
    void payrollForAnEmployeeWithoutADepartmentStaysUnassigned() {
        employee.assignPosition(null, null);
        employeeRepository.save(employee);
        PayCycle november = cycle("November 2026", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                PayCycle.CycleType.MONTHLY);

        Payroll payroll = payrollService.createPayroll(employee.getEmployeeId(), november.getPayCycleId());
        payrollService.processPayroll(payroll.getPayrollId(), processor().getUsername());

        employee.assignPosition(TestChoices.department(departmentRepository, "Sales"), null);
        employeeRepository.save(employee);

        assertThat(executiveDashboardService.summary(java.time.YearMonth.of(2026, 11)).departments())
                .filteredOn(row -> row.department().equals("Unassigned"))
                .singleElement()
                .satisfies(row -> assertThat(row.amounts().get("grossPay"))
                        .isEqualByComparingTo(payroll.getGrossPay()));
    }

    // ---------- year-end annualization ----------

    @Test
    void decemberPayrollCollectsTheYearsTaxShortfall() {
        enableStatutoryDeductions();
        PayCycle december = cycle("December 2026", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31),
                PayCycle.CycleType.MONTHLY);
        earlierPaidPayroll("250000.00", "0.00");

        Payroll payroll = payrollService.createPayroll(employee.getEmployeeId(), december.getPayCycleId());

        // This month: 26,100 - contributions (1,300 + 652.50 + 200) = 23,947.50.
        // Year: 273,947.50 -> 15% x 23,947.50 = 3,592.13 due, nothing withheld.
        assertThat(payroll.getTaxableCompensation()).isEqualByComparingTo("23947.50");
        assertThat(payroll.getAnnualizedTaxDue()).isEqualByComparingTo("3592.13");
        assertThat(payroll.getTaxAmount()).isEqualByComparingTo("3592.13");
        assertThat(payroll.getTaxRefund()).isEqualByComparingTo("0.00");
    }

    @Test
    void decemberPayrollRefundsExcessTaxWithheld() {
        enableStatutoryDeductions();
        PayCycle december = cycle("December 2026", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31),
                PayCycle.CycleType.MONTHLY);
        earlierPaidPayroll("250000.00", "5000.00");

        Payroll payroll = payrollService.createPayroll(employee.getEmployeeId(), december.getPayCycleId());

        // 3,592.13 due - 5,000.00 withheld = 1,407.87 refunded with this pay.
        assertThat(payroll.getTaxAmount()).isEqualByComparingTo("0.00");
        assertThat(payroll.getTaxRefund()).isEqualByComparingTo("1407.87");
        assertThat(payroll.getNetPay()).isEqualByComparingTo(
                payroll.getGrossPay().subtract(payroll.getDeductionAmount()).add(new BigDecimal("1407.87")));
    }

    @Test
    void annualizationIncludesThePreviousEmployer() {
        enableStatutoryDeductions();
        PayCycle december = cycle("December 2026", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31),
                PayCycle.CycleType.MONTHLY);
        earlierPaidPayroll("250000.00", "0.00");
        prior(2026, "100000.00", "0.00", "2000.00");

        Payroll payroll = payrollService.createPayroll(employee.getEmployeeId(), december.getPayCycleId());

        // 373,947.50 taxable -> 15% x 123,947.50 = 18,592.13; less 2,000 withheld.
        assertThat(payroll.getAnnualizedTaxDue()).isEqualByComparingTo("18592.13");
        assertThat(payroll.getTaxAmount()).isEqualByComparingTo("16592.13");
    }

    @Test
    void cyclesNotEndingOnDecemberThirtyFirstAreNotAnnualized() {
        enableStatutoryDeductions();
        Payroll payroll = payrollService.createPayroll(employee.getEmployeeId(), october.getPayCycleId());

        assertThat(payroll.getAnnualizedTaxDue()).isNull();
    }

    // ---------- 13th month generation ----------

    @Test
    void thirteenthMonthIsBasicSalaryEarnedOverTwelveAndToppedUpWithoutDoubling() {
        Employee manager = employee("manager@example.test");
        manager.setManagerial(true);
        employeeRepository.save(manager);

        paidPayroll(employee, cycle("Jan 2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                PayCycle.CycleType.MONTHLY), "26100.00");
        paidPayroll(employee, cycle("Feb 2026", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                PayCycle.CycleType.MONTHLY), "26100.00");

        List<EarningService.ThirteenthMonthLine> lines =
                earningService.generateThirteenthMonth(2026, LocalDate.of(2026, 12, 10));

        // (26,100 + 26,100) / 12 = 4,350.00
        assertThat(lines).anySatisfy(line -> {
            assertThat(line.employeeId()).isEqualTo(employee.getEmployeeId());
            assertThat(line.amountEntered()).isEqualByComparingTo("4350.00");
        });
        assertThat(lines).anySatisfy(line -> {
            assertThat(line.employeeId()).isEqualTo(manager.getEmployeeId());
            assertThat(line.result()).contains("managerial");
        });

        // Running it again updates the same entry instead of adding another.
        earningService.generateThirteenthMonth(2026, LocalDate.of(2026, 12, 10));

        assertThat(earningRepository.findByEmployeeEmployeeIdAndEarningTypeAndDateGivenBetween(
                employee.getEmployeeId(), Earning.Type.THIRTEENTH_MONTH,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).hasSize(1);
    }

    @Test
    void thirteenthMonthReportShowsEarnedEnteredPaidAndBalance() {
        Employee manager = employee("boss@example.test");
        manager.setManagerial(true);
        employeeRepository.save(manager);

        paidPayroll(employee, cycle("Jan 2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                PayCycle.CycleType.MONTHLY), "26100.00");

        // Before generating: 26,100 / 12 = 2,175.00 earned, nothing entered.
        ThirteenthMonthStatus before = statusOf(earningService.thirteenthMonthReport(2026), employee);
        assertThat(before.getEarned()).isEqualByComparingTo("2175.00");
        assertThat(before.getEntered()).isEqualByComparingTo("0.00");
        assertThat(before.getBalanceDue()).isEqualByComparingTo("2175.00");
        assertThat(before.getStatus()).contains("Not entered yet");

        earningService.generateThirteenthMonth(2026, LocalDate.of(2026, 12, 10));

        // Entered but not yet paid: nothing left to enter.
        EarningService.ThirteenthMonthReport report = earningService.thirteenthMonthReport(2026);
        ThirteenthMonthStatus after = statusOf(report, employee);
        assertThat(after.getEntered()).isEqualByComparingTo("2175.00");
        assertThat(after.getPaid()).isEqualByComparingTo("0.00");
        assertThat(after.getBalanceDue()).isEqualByComparingTo("0.00");
        assertThat(report.dueDate()).isEqualTo(LocalDate.of(2026, 12, 24));

        ThirteenthMonthStatus boss = statusOf(report, manager);
        assertThat(boss.isEligible()).isFalse();
        assertThat(boss.getEarned()).isEqualByComparingTo("0.00");
        assertThat(boss.getStatus()).contains("managerial");

        // The report only reads: still exactly one entry.
        assertThat(earningRepository.findByEmployeeEmployeeIdAndEarningTypeAndDateGivenBetween(
                employee.getEmployeeId(), Earning.Type.THIRTEENTH_MONTH,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).hasSize(1);
    }

    // ---------- helpers ----------

    private static ThirteenthMonthStatus statusOf(EarningService.ThirteenthMonthReport report, Employee who) {
        return report.employees().stream()
                .filter(status -> status.getEmployeeId().equals(who.getEmployeeId()))
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> preview() {
        return payrollService.previewPayroll(employee.getEmployeeId(), october.getPayCycleId());
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private void setting(String name, String value) {
        SystemSetting setting = systemSettingRepository.findBySettingName(name).orElseGet(SystemSetting::new);
        setting.setSettingName(name);
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }

    private void enableStatutoryDeductions() {
        Map.ofEntries(
                Map.entry("statutory_deductions_enabled", "true"),
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
                .forEach(this::setting);
    }

    private User processor() {
        User user = new User();
        user.setUsername("payroll.processor");
        user.setPasswordHash("not-used");
        user.setRole(User.Role.HR_MANAGER);
        return userRepository.save(user);
    }

    private Employee employee(String email) {
        Employee employee = new Employee();
        employee.setFirstName("Test");
        employee.setLastName(email.substring(0, email.indexOf('@')));
        employee.setEmail(email);
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2025, 1, 1));
        employee.setBaseSalary(new BigDecimal("26100.00"));
        return employeeRepository.save(employee);
    }

    private PayCycle cycle(String name, LocalDate start, LocalDate end, PayCycle.CycleType type) {
        PayCycle cycle = new PayCycle();
        cycle.setCycleName(name);
        cycle.setStartDate(start);
        cycle.setEndDate(end);
        cycle.setPayDate(end);
        cycle.setCycleType(type);
        return payCycleRepository.save(cycle);
    }

    private void holiday(LocalDate date, Holiday.Type type) {
        Holiday holiday = new Holiday();
        holiday.setHolidayDate(date);
        holiday.setName("Test holiday");
        holiday.setHolidayType(type);
        holidayRepository.save(holiday);
    }

    private Attendance attendance(LocalDate date, Attendance.Status status, String hours, String in, String out) {
        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(date);
        attendance.setStatus(status);
        attendance.setRegularHours(new BigDecimal(hours));
        attendance.setTimeIn(in != null ? LocalTime.parse(in) : null);
        attendance.setTimeOut(out != null ? LocalTime.parse(out) : null);
        return attendanceRepository.save(attendance);
    }

    private void approvedOvertime(Attendance attendance, String hours, String amount) {
        Overtime overtime = new Overtime();
        overtime.setEmployee(employee);
        overtime.setAttendance(attendance);
        overtime.setOvertimeDate(attendance.getAttendanceDate());
        overtime.setHours(new BigDecimal(hours));
        overtime.setOvertimeRate(new BigDecimal("187.50"));
        overtime.setOvertimeAmount(new BigDecimal(amount));
        ReflectionTestUtils.setField(overtime, "status", ApprovalRequest.Status.APPROVED);
        overtimeRepository.save(overtime);
    }

    private void earning(Earning.Type type, String amount, LocalDate date) {
        Earning earning = new Earning();
        earning.setEmployee(employee);
        earning.setEarningType(type);
        earning.setAmount(new BigDecimal(amount));
        earning.setDateGiven(date);
        earningRepository.save(earning);
    }

    private void prior(int year, String taxable, String exemptBenefits, String withheld) {
        PriorEmployerCompensation prior = new PriorEmployerCompensation();
        prior.setEmployee(employee);
        prior.setTaxYear(year);
        prior.setEmployerName("Previous Co.");
        prior.setTaxableCompensation(new BigDecimal(taxable));
        prior.setExemptBenefits(new BigDecimal(exemptBenefits));
        prior.setTaxWithheld(new BigDecimal(withheld));
        priorEmployerRepository.save(prior);
    }

    /** A PAID payroll earlier in 2026 with the given taxable compensation and tax. */
    private void earlierPaidPayroll(String taxable, String tax) {
        PayCycle earlier = cycle("Earlier 2026", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                PayCycle.CycleType.MONTHLY);
        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayCycle(earlier);
        ReflectionTestUtils.setField(payroll, "status", Payroll.Status.PAID);
        payroll.setTaxableCompensation(new BigDecimal(taxable));
        payroll.setTaxAmount(new BigDecimal(tax));
        payrollRepository.save(payroll);
    }

    private void paidPayroll(Employee owner, PayCycle cycle, String regularPay) {
        Payroll payroll = new Payroll();
        payroll.setEmployee(owner);
        payroll.setPayCycle(cycle);
        ReflectionTestUtils.setField(payroll, "status", Payroll.Status.PAID);
        payroll.setRegularPay(new BigDecimal(regularPay));
        payrollRepository.save(payroll);
    }
}
