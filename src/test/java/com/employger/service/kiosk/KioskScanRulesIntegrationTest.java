package com.employger.service.kiosk;

import com.employger.dto.KioskScanResponse;
import com.employger.entity.Attendance;
import com.employger.entity.AuditLog;
import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.SystemSetting;
import com.employger.entity.User;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.AuditLogRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.SystemSettingRepository;
import com.employger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The kiosk's clock-in/clock-out rules, against the in-memory H2 database.
 * Schedule 08:00-17:00, 60-minute unpaid break, 10-minute grace period,
 * 8 regular hours per day.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "kiosk", roles = "KIOSK")
class KioskScanRulesIntegrationTest {

    private static final LocalDate OCT_6 = LocalDate.of(2026, 10, 6);
    private static final LocalDate OCT_7 = LocalDate.of(2026, 10, 7);

    @Autowired
    private KioskService kioskService;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private PayrollRepository payrollRepository;
    @Autowired
    private SystemSettingRepository systemSettingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private Employee employee;
    private String badge;

    @BeforeEach
    void setUp() {
        setting("standard_start_time", "08:00");
        setting("standard_end_time", "17:00");
        setting("unpaid_break_minutes", "60");
        setting("late_grace_period_minutes", "10");
        setting("regular_hours_per_day", "8");

        User kiosk = new User();
        kiosk.setUsername("kiosk");
        kiosk.setPasswordHash("not-used-by-these-tests");
        kiosk.setRole(User.Role.KIOSK);
        userRepository.save(kiosk);

        employee = new Employee();
        employee.setFirstName("Juan");
        employee.setLastName("Dela Cruz");
        employee.setEmail("kiosk.scan@example.test");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        badge = BadgeCode.generate().value();
        employee.issueBadge(badge);
        employee = employeeRepository.save(employee);
    }

    @Test
    void firstScanClocksInAndTheNextClocksOutWithDerivedHours() {
        KioskScanResponse in = scan(badge, OCT_6, 7, 55);

        assertThat(in.employeeName()).isEqualTo("Juan Dela Cruz");
        assertThat(in.direction()).isEqualTo("IN");
        assertThat(in.status()).isEqualTo("PRESENT");
        assertThat(in.message()).isEqualTo("Clocked in at 7:55 AM");

        KioskScanResponse out = scan(badge, OCT_6, 17, 5);

        assertThat(out.direction()).isEqualTo("OUT");
        assertThat(out.message()).isEqualTo("Clocked out at 5:05 PM · 8.00 regular hours");

        Attendance day = day(OCT_6);
        assertThat(day.getTimeIn()).isEqualTo(LocalTime.of(7, 55));
        assertThat(day.getTimeOut()).isEqualTo(LocalTime.of(17, 5));
        assertThat(day.getRegularHours()).isEqualByComparingTo("8.00");
        assertThat(day.getStatus()).isEqualTo(Attendance.Status.PRESENT);
    }

    @Test
    void arrivingAfterTheGracePeriodIsLate() {
        assertThat(scan(badge, OCT_6, 8, 10).status()).isEqualTo("PRESENT");

        Employee other = employeeRepository.save(employeeWithBadge("late.one@example.test"));
        KioskScanResponse late = scan(other.getBadgeCode(), OCT_6, 8, 11);

        assertThat(late.status()).isEqualTo("LATE");
        assertThat(late.message()).isEqualTo("Clocked in at 8:11 AM (late)");
    }

    @Test
    void aSecondScanWithinFiveMinutesDoesNotClockOut() {
        scan(badge, OCT_6, 7, 58);

        assertThatThrownBy(() -> scan(badge, OCT_6, 8, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You already clocked in at 7:58 AM. Scan again when you leave.");

        assertThat(day(OCT_6).getTimeOut()).isNull();
    }

    @Test
    void afterClockingOutFurtherScansAreRefused() {
        scan(badge, OCT_6, 8, 0);
        scan(badge, OCT_6, 17, 0);

        assertThatThrownBy(() -> scan(badge, OCT_6, 17, 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You already clocked out today at 5:00 PM.");
    }

    @Test
    void aDayRecordedAsLeaveIsLeftToHr() {
        Attendance leave = new Attendance();
        leave.setEmployee(employee);
        leave.setAttendanceDate(OCT_6);
        leave.setStatus(Attendance.Status.LEAVE);
        leave.setRegularHours(BigDecimal.ZERO);
        attendanceRepository.save(leave);

        assertThatThrownBy(() -> scan(badge, OCT_6, 8, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Today is recorded as LEAVE for you. Please see HR.");
    }

    @Test
    void unknownMalformedAndFormerEmployeesCardsAreRefused() {
        assertThatThrownBy(() -> scan(BadgeCode.generate().value(), OCT_6, 8, 0))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(KioskService.UNKNOWN_CARD);
        assertThatThrownBy(() -> scan("hello", OCT_6, 8, 0))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(KioskService.UNKNOWN_CARD);

        employee.setEmploymentStatus(Employee.EmploymentStatus.INACTIVE);
        employeeRepository.save(employee);

        assertThatThrownBy(() -> scan(badge, OCT_6, 8, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This card is no longer active. Please see HR.");
        assertThat(attendanceRepository.findByEmployeeEmployeeId(employee.getEmployeeId())).isEmpty();
    }

    @Test
    void aTypedCodeWorksLikeAScannedOne() {
        String typed = badge.toLowerCase().replace("-", " ");

        assertThat(scan(typed, OCT_6, 8, 0).direction()).isEqualTo("IN");
    }

    @Test
    void theMorningScanClosesANightShiftFromYesterday() {
        scan(badge, OCT_6, 22, 0);

        KioskScanResponse out = scan(badge, OCT_7, 6, 0);

        assertThat(out.direction()).isEqualTo("OUT");
        assertThat(out.attendanceDate()).isEqualTo(OCT_6);
        assertThat(out.message()).startsWith("Clocked out at 6:00 AM (shift started yesterday)");

        Attendance night = day(OCT_6);
        assertThat(night.getTimeOut()).isEqualTo(LocalTime.of(6, 0));
        // 8 hours, less the unpaid break.
        assertThat(night.getRegularHours()).isEqualByComparingTo("7.00");
        assertThat(attendanceRepository.findByEmployeeEmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), OCT_7)).isEmpty();
    }

    @Test
    void aForgottenClockOutStaysOpenForHrAndTodayStartsFresh() {
        scan(badge, OCT_6, 8, 0);

        // 23 h 50 min later: too long for a shift, so a new day.
        KioskScanResponse next = scan(badge, OCT_7, 7, 50);

        assertThat(next.direction()).isEqualTo("IN");
        assertThat(next.attendanceDate()).isEqualTo(OCT_7);
        assertThat(day(OCT_6).getTimeOut()).isNull();
    }

    @Test
    void aDayInProcessedPayrollCantBeClockedInto() {
        PayCycle cycle = new PayCycle();
        cycle.setCycleName("October 1-15, 2026");
        cycle.setStartDate(LocalDate.of(2026, 10, 1));
        cycle.setEndDate(LocalDate.of(2026, 10, 15));
        cycle.setPayDate(LocalDate.of(2026, 10, 15));
        cycle.setCycleType(PayCycle.CycleType.SEMI_MONTHLY);
        cycle = payCycleRepository.save(cycle);

        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayCycle(cycle);
        ReflectionTestUtils.setField(payroll, "status", Payroll.Status.PROCESSED);
        payrollRepository.save(payroll);

        assertThatThrownBy(() -> scan(badge, OCT_6, 8, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has already been processed or paid");
    }

    @Test
    void everyPunchIsAuditedUnderTheKioskLogin() {
        scan(badge, OCT_6, 8, 0);

        AuditLog entry = auditLogRepository.findTop200ByOrderByAuditIdDesc().getFirst();

        assertThat(entry.getAction()).isEqualTo("ATTENDANCE_CLOCK_IN");
        assertThat(entry.getDescription()).isEqualTo("Employee #" + employee.getEmployeeId()
                + " clocked in at 08:00 for 2026-10-06 (PRESENT) at the kiosk");
        assertThat(entry.getUser().getUsername()).isEqualTo("kiosk");
    }

    private KioskScanResponse scan(String code, LocalDate date, int hour, int minute) {
        return kioskService.scan(code, LocalDateTime.of(date, LocalTime.of(hour, minute, 30)));
    }

    private Attendance day(LocalDate date) {
        return attendanceRepository
                .findByEmployeeEmployeeIdAndAttendanceDate(employee.getEmployeeId(), date)
                .orElseThrow();
    }

    private static Employee employeeWithBadge(String email) {
        Employee other = new Employee();
        other.setFirstName("Late");
        other.setLastName("Comer");
        other.setEmail(email);
        other.setDateOfBirth(LocalDate.of(1990, 1, 1));
        other.setDateHired(LocalDate.of(2026, 1, 1));
        other.setBaseSalary(new BigDecimal("25000.00"));
        other.issueBadge(BadgeCode.generate().value());
        return other;
    }

    private void setting(String name, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingName(name);
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }
}
