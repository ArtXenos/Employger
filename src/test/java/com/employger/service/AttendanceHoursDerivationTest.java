package com.employger.service;

import com.employger.entity.Attendance;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayrollRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Hours derived from time in/out with the default schedule: 08:00-17:00,
 * 60-minute unpaid break, 8 regular hours per day.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceHoursDerivationTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private OvertimeRepository overtimeRepository;
    @Mock
    private PayrollRepository payrollRepository;
    @Mock
    private SystemSettingService systemSettingService;

    private AttendanceService attendanceService;

    @BeforeEach
    void setUp() {
        lenient().when(systemSettingService.getSettingValue("standard_start_time")).thenReturn("08:00");
        lenient().when(systemSettingService.getSettingValue("standard_end_time")).thenReturn("17:00");
        lenient().when(systemSettingService.getSettingValue("unpaid_break_minutes")).thenReturn("60");
        lenient().when(systemSettingService.getSettingValue("regular_hours_per_day")).thenReturn("8");
        grace("0");

        attendanceService = new AttendanceService(
                attendanceRepository,
                employeeRepository,
                overtimeRepository,
                payrollRepository,
                new PayrollCalculationService(systemSettingService));
    }

    @ParameterizedTest(name = "{0}-{1} -> {2} h {3}")
    @CsvSource({
            // full day
            "08:00, 17:00, 8.00, PRESENT",
            // early arrival and late departure do not add regular hours
            "07:30, 18:30, 8.00, PRESENT",
            // late arrival: break still taken
            "09:15, 17:00, 6.75, LATE",
            // existing record #9 (08:30-14:35) if it were saved today
            "08:30, 14:35, 5.08, LATE",
            // morning half day: no break charged
            "08:00, 12:00, 4.00, PRESENT",
            // afternoon half day: no break charged
            "13:00, 17:00, 4.00, LATE",
            // left early after lunch
            "08:00, 15:00, 6.00, PRESENT",
            // overnight: 22:00-06:00 = 8 h span, less break = 7 h, status kept
            "22:00, 06:00, 7.00, PRESENT",
    })
    void derivesHoursAndStatus(String in, String out, String expectedHours, String expectedStatus) {
        Attendance attendance = attendance(in, out, Attendance.Status.PRESENT);

        attendanceService.deriveHoursFromTimes(attendance);

        assertThat(attendance.getRegularHours()).isEqualByComparingTo(expectedHours);
        assertThat(attendance.getStatus()).isEqualTo(Attendance.Status.valueOf(expectedStatus));
    }

    @Test
    void arrivalWithinGracePeriodIsOnTime() {
        grace("15");
        Attendance attendance = attendance("08:10", "17:00", Attendance.Status.LATE);

        attendanceService.deriveHoursFromTimes(attendance);

        assertThat(attendance.getStatus()).isEqualTo(Attendance.Status.PRESENT);
        assertThat(attendance.getRegularHours()).isEqualByComparingTo("8.00");
    }

    @Test
    void daysWithoutBothTimesKeepEnteredHours() {
        Attendance attendance = attendance("08:00", null, Attendance.Status.PRESENT);
        attendance.setRegularHours(new BigDecimal("6.50"));

        attendanceService.deriveHoursFromTimes(attendance);

        assertThat(attendance.getRegularHours()).isEqualByComparingTo("6.50");
    }

    @Test
    void absentAndLeaveDaysAreNotDerived() {
        Attendance leave = attendance("08:00", "17:00", Attendance.Status.LEAVE);
        leave.setRegularHours(BigDecimal.ZERO);

        attendanceService.deriveHoursFromTimes(leave);

        assertThat(leave.getRegularHours()).isEqualByComparingTo("0");
        assertThat(leave.getStatus()).isEqualTo(Attendance.Status.LEAVE);
    }

    private void grace(String minutes) {
        lenient().when(systemSettingService.getSettingValue("late_grace_period_minutes")).thenReturn(minutes);
    }

    private static Attendance attendance(String in, String out, Attendance.Status status) {
        Attendance attendance = new Attendance();
        attendance.setTimeIn(in != null ? LocalTime.parse(in) : null);
        attendance.setTimeOut(out != null ? LocalTime.parse(out) : null);
        attendance.setStatus(status);
        return attendance;
    }
}
