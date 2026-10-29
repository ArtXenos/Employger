package com.employger.service.kiosk;

import com.employger.entity.Attendance;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * One badge scan at the kiosk: a clock-in or a clock-out. KioskService
 * decides which one a scan is; the punch itself knows how to write it into
 * the day's attendance record and how to describe it, so the service and the
 * kiosk page never ask which kind they have.
 *
 * Sealed: a scan is one of these two and nothing else.
 */
public abstract sealed class Punch permits ClockIn, ClockOut {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final LocalTime time;

    // To the minute, like the times HR types in.
    protected Punch(LocalTime time) {
        this.time = time.truncatedTo(ChronoUnit.MINUTES);
    }

    public final LocalTime time() {
        return time;
    }

    /** Writes this punch into the day's record (not yet saved). */
    public abstract void writeTo(Attendance attendance);

    /** "IN" or "OUT", for the kiosk page. */
    public abstract String direction();

    /** The audit log action. */
    public abstract String auditAction();

    /** The line under the name on the kiosk, after the record is saved. */
    public abstract String describe(Attendance saved);
}
