package com.employger.service.kiosk;

import com.employger.entity.Attendance;

import java.time.LocalTime;

/**
 * Leaving: closes the open day. The hours are derived when the record is
 * saved (AttendanceService), the same way as for times entered by HR.
 */
final class ClockOut extends Punch {

    // Closes a shift that started the day before (a night shift).
    private final boolean overnight;

    ClockOut(LocalTime time, boolean overnight) {
        super(time);
        this.overnight = overnight;
    }

    @Override
    public void writeTo(Attendance attendance) {
        if (attendance.getTimeIn() == null) {
            throw new IllegalStateException("There is no clock-in to close.");
        }
        attendance.setTimeOut(time());
    }

    @Override
    public String direction() {
        return "OUT";
    }

    @Override
    public String auditAction() {
        return "ATTENDANCE_CLOCK_OUT";
    }

    @Override
    public String describe(Attendance saved) {
        return "Clocked out at " + TIME.format(time())
                + (overnight ? " (shift started yesterday)" : "")
                + " · " + saved.getRegularHours().toPlainString() + " regular hours";
    }
}
