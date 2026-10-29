package com.employger.service.kiosk;

import com.employger.dto.KioskScanResponse;
import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.service.AttendanceService;
import com.employger.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Turns a badge scan at the kiosk into a clock-in or clock-out:
 *
 *   no record today            -> clock in (PRESENT, or LATE after the grace period)
 *   clocked in, not out        -> clock out, and the hours are derived
 *   scanned again within 5 min -> refused, so a double scan doesn't clock out
 *   already clocked out        -> refused
 *   today is LEAVE or ABSENT   -> refused: HR decides
 *
 * A night shift (clocked in yesterday, still open, within 16 hours) is
 * closed by the morning scan instead of starting a new day. An open day
 * older than that is a forgotten clock-out: it stays open for HR to fix,
 * and the scan starts today.
 *
 * The time is always the server's own clock. The kiosk sends only the code.
 */
@Service
public class KioskService {

    static final Duration REPEAT_SCAN_WINDOW = Duration.ofMinutes(5);
    static final Duration LONGEST_SHIFT = Duration.ofHours(16);

    // The same answer for a code that is malformed and one nobody has, so
    // the kiosk can't be used to test which codes exist.
    static final String UNKNOWN_CARD = "Card not recognized. Please see HR.";

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceService attendanceService;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public KioskService(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            AttendanceService attendanceService,
            AuditService auditService) {

        this(employeeRepository, attendanceRepository, attendanceService, auditService,
                Clock.systemDefaultZone());
    }

    KioskService(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            AttendanceService attendanceService,
            AuditService auditService,
            Clock clock) {

        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.attendanceService = attendanceService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public KioskScanResponse scan(String scannedText) {
        return scan(scannedText, LocalDateTime.now(clock));
    }

    // Package-private: tests choose the time; the kiosk never can.
    @Transactional
    KioskScanResponse scan(String scannedText, LocalDateTime now) {

        Employee employee = BadgeCode.parse(scannedText)
                .flatMap(code -> employeeRepository.findByBadgeCode(code.value()))
                .orElseThrow(() -> new ResourceNotFoundException(UNKNOWN_CARD));

        if (!employee.isEmployed()) {
            throw new IllegalStateException("This card is no longer active. Please see HR.");
        }

        // To the minute throughout, so 08:10:30 is judged as the 08:10 recorded.
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);

        Attendance day;
        Punch punch;

        Optional<Attendance> todays = attendanceRepository
                .findByEmployeeEmployeeIdAndAttendanceDate(employee.getEmployeeId(), today);

        if (todays.isPresent()) {
            day = todays.get();
            punch = nextPunch(day, time);
        } else {
            Optional<Attendance> nightShift = openNightShift(employee, today, time);

            if (nightShift.isPresent()) {
                day = nightShift.get();
                punch = new ClockOut(time, true);
            } else {
                day = new Attendance();
                day.setEmployee(employee);
                day.setAttendanceDate(today);
                punch = new ClockIn(time, attendanceService.isLateArrival(time));
            }
        }

        punch.writeTo(day);
        Attendance saved = attendanceService.saveClockedDay(day);

        String name = employee.getFirstName() + " " + employee.getLastName();

        auditService.record(punch.auditAction(),
                "Employee #" + employee.getEmployeeId() + " clocked " + punch.direction().toLowerCase()
                        + " at " + punch.time() + " for " + saved.getAttendanceDate()
                        + " (" + saved.getStatus() + ") at the kiosk");

        return new KioskScanResponse(
                name,
                punch.direction(),
                saved.getAttendanceDate(),
                punch.time(),
                saved.getStatus().name(),
                punch.describe(saved));
    }

    // Today already has a record: what this scan means for it.
    private Punch nextPunch(Attendance day, LocalTime time) {

        if (day.getTimeIn() == null) {
            boolean decidedByHr = day.getStatus() == Attendance.Status.LEAVE
                    || day.getStatus() == Attendance.Status.ABSENT;

            if (decidedByHr) {
                throw new IllegalStateException("Today is recorded as " + day.getStatus()
                        + " for you. Please see HR.");
            }

            return new ClockIn(time, attendanceService.isLateArrival(time));
        }

        if (day.getTimeOut() != null) {
            throw new IllegalStateException("You already clocked out today at "
                    + Punch.TIME.format(day.getTimeOut()) + ".");
        }

        Duration sinceClockIn = Duration.between(day.getTimeIn(), time);

        if (!sinceClockIn.isNegative() && sinceClockIn.compareTo(REPEAT_SCAN_WINDOW) < 0) {
            throw new IllegalStateException("You already clocked in at "
                    + Punch.TIME.format(day.getTimeIn()) + ". Scan again when you leave.");
        }

        return new ClockOut(time, false);
    }

    // Yesterday's day, if it is still open and short enough to be a night
    // shift ending now.
    private Optional<Attendance> openNightShift(Employee employee, LocalDate today, LocalTime time) {

        return attendanceRepository
                .findByEmployeeEmployeeIdAndAttendanceDate(employee.getEmployeeId(), today.minusDays(1))
                .filter(yesterday -> yesterday.getTimeIn() != null && yesterday.getTimeOut() == null)
                .filter(yesterday -> {
                    Duration shift = Duration.between(
                            today.minusDays(1).atTime(yesterday.getTimeIn()), today.atTime(time));
                    return shift.compareTo(LONGEST_SHIFT) <= 0;
                });
    }
}
