package com.employger.service.kiosk;

import com.employger.entity.Attendance;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Arriving: starts the day as PRESENT, or LATE after the grace period. */
final class ClockIn extends Punch {

    private final boolean late;

    ClockIn(LocalTime time, boolean late) {
        super(time);
        this.late = late;
    }

    @Override
    public void writeTo(Attendance attendance) {
        attendance.setTimeIn(time());
        attendance.setTimeOut(null);
        attendance.setRegularHours(BigDecimal.ZERO);
        attendance.setStatus(late ? Attendance.Status.LATE : Attendance.Status.PRESENT);
    }

    @Override
    public String direction() {
        return "IN";
    }

    @Override
    public String auditAction() {
        return "ATTENDANCE_CLOCK_IN";
    }

    @Override
    public String describe(Attendance saved) {
        return "Clocked in at " + TIME.format(time()) + (late ? " (late)" : "");
    }
}
