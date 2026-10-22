package com.employger.service;

import com.employger.entity.Employee;
import com.employger.entity.Holiday;
import com.employger.entity.PayCycle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pay-rate building blocks and the payroll settings they depend on.
 *
 * Rounding policy (decided 2026-10-22): intermediate values such as the
 * hourly rate keep INTERMEDIATE_SCALE decimals; only final money amounts
 * are rounded to centavos.
 */
@Service
public class PayrollCalculationService {

        private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

        public static final int INTERMEDIATE_SCALE = 6;

        public static final int FINAL_MONETARY_SCALE = 2;

        private final SystemSettingService systemSettingService;

        public PayrollCalculationService(
                        SystemSettingService systemSettingService) {

                this.systemSettingService = systemSettingService;
        }

        public BigDecimal calculateAnnualSalary(
                        Employee employee) {

                if (employee.getBaseSalary() == null) {
                        return BigDecimal.ZERO;
                }

                return employee.getBaseSalary()
                                .multiply(MONTHS_PER_YEAR);
        }

        public BigDecimal calculateFactorRate() {

                String factorRateValue = systemSettingService.getSettingValue(
                                "factor_rate");

                try {

                        BigDecimal factorRate = new BigDecimal(factorRateValue);

                        if (factorRate.compareTo(BigDecimal.ZERO) <= 0) {
                                throw new IllegalArgumentException(
                                                "Factor rate must be greater than zero");
                        }

                        return factorRate;

                } catch (NumberFormatException e) {

                        throw new IllegalArgumentException(
                                        "Invalid factor rate setting");
                }
        }

        public BigDecimal calculateRegularHoursPerDay() {
                String value = systemSettingService.getSettingValue(
                                "regular_hours_per_day");

                try {
                        BigDecimal hours = new BigDecimal(value);

                        if (hours.compareTo(BigDecimal.ZERO) <= 0
                                        || hours.compareTo(new BigDecimal("24")) > 0) {
                                throw new IllegalArgumentException(
                                                "Regular hours per day must be greater than zero "
                                                                + "and no more than 24");
                        }

                        return hours;

                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                        "Invalid regular hours per day setting");
                }
        }

        public BigDecimal calculateOvertimeMultiplier() {
                String value = systemSettingService.getSettingValue(
                                "default_overtime_rate");

                try {
                        BigDecimal multiplier = new BigDecimal(value);

                        if (multiplier.compareTo(BigDecimal.ZERO) <= 0) {
                                throw new IllegalArgumentException(
                                                "Overtime multiplier must be greater than zero");
                        }

                        return multiplier;

                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                        "Invalid default overtime rate setting");
                }
        }

        /**
         * Hourly rate = monthly salary x 12 / factor rate / regular hours per
         * day, kept at INTERMEDIATE_SCALE decimals (not rounded to centavos).
         */
        public BigDecimal calculateHourlyRate(
                        Employee employee) {

                if (employee.getBaseSalary() == null) {
                        return BigDecimal.ZERO.setScale(INTERMEDIATE_SCALE);
                }

                BigDecimal annualSalary = calculateAnnualSalary(employee);

                BigDecimal factorRate = calculateFactorRate();

                BigDecimal regularHoursPerDay = calculateRegularHoursPerDay();

                return annualSalary.divide(
                                factorRate.multiply(regularHoursPerDay),
                                INTERMEDIATE_SCALE,
                                RoundingMode.HALF_UP);
        }

        public int periodsPerYear(PayCycle.CycleType cycleType) {

                switch (cycleType) {

                        case WEEKLY:
                                return 52;

                        case BI_WEEKLY:
                                return 26;

                        case SEMI_MONTHLY:
                                return 24;

                        case MONTHLY:
                                return 12;

                        default:
                                throw new IllegalArgumentException(
                                                "Unsupported pay cycle type: "
                                                                + cycleType);
                }
        }

        /**
         * Full base pay for one cycle (annual salary / periods per year),
         * kept at INTERMEDIATE_SCALE decimals so proration is applied before
         * rounding.
         */
        public BigDecimal calculateCycleBasePay(
                        Employee employee,
                        PayCycle.CycleType cycleType) {

                if (employee.getBaseSalary() == null) {
                        return BigDecimal.ZERO.setScale(INTERMEDIATE_SCALE);
                }

                return calculateAnnualSalary(employee).divide(
                                BigDecimal.valueOf(periodsPerYear(cycleType)),
                                INTERMEDIATE_SCALE,
                                RoundingMode.HALF_UP);
        }

        /** Overtime hourly rate rounded to centavos, for display and storage. */
        public BigDecimal calculateOvertimeRate(
                        Employee employee,
                        BigDecimal overtimeMultiplier) {

                return calculateHourlyRate(employee)
                                .multiply(overtimeMultiplier)
                                .setScale(
                                                FINAL_MONETARY_SCALE,
                                                RoundingMode.HALF_UP);
        }

        /**
         * Overtime pay = hourly rate x multiplier x hours, rounded once at the
         * end (not computed from the rounded overtime rate).
         */
        public BigDecimal calculateOvertimePay(
                        Employee employee,
                        BigDecimal overtimeHours,
                        BigDecimal overtimeMultiplier) {

                if (overtimeHours == null
                                || overtimeHours.compareTo(
                                                BigDecimal.ZERO) <= 0) {

                        return BigDecimal.ZERO.setScale(
                                        FINAL_MONETARY_SCALE,
                                        RoundingMode.HALF_UP);
                }

                return calculateHourlyRate(employee)
                                .multiply(overtimeMultiplier)
                                .multiply(overtimeHours)
                                .setScale(
                                                FINAL_MONETARY_SCALE,
                                                RoundingMode.HALF_UP);
        }

        // ---------- holiday, rest-day and night premiums ----------

        /**
         * Multipliers of the hourly basic rate for one day (migration V6,
         * rules in docs/STATUTORY_DEDUCTIONS.md):
         *  - worked: pay for each regular hour worked that day,
         *  - unworked: pay for each regular hour NOT worked (non-rest days
         *    only; base pay already covers 1.00 of it),
         *  - overtime: pay for each overtime hour.
         * Base pay (factor 261) already pays 1.00 for every non-rest day, so
         * only the part above 1.00 is added there; rest days are not covered
         * by base pay and are paid in full.
         */
        public record DayRates(BigDecimal worked, BigDecimal unworked, BigDecimal overtime) {
        }

        public DayRates dayRates(Holiday.Type holidayType, boolean restDay) {

                BigDecimal premiumFactor = decimalSetting("overtime_premium_factor", "1.30");

                if (holidayType == null) {

                        if (!restDay) {
                                return new DayRates(BigDecimal.ONE, BigDecimal.ONE, calculateOvertimeMultiplier());
                        }

                        BigDecimal rest = decimalSetting("rate_rest_day", "1.30");
                        return new DayRates(rest, BigDecimal.ZERO, rest.multiply(premiumFactor));
                }

                BigDecimal worked = switch (holidayType) {
                        case REGULAR -> restDay
                                        ? decimalSetting("rate_regular_holiday_rest_day", "2.60")
                                        : decimalSetting("rate_regular_holiday", "2.00");
                        case SPECIAL -> restDay
                                        ? decimalSetting("rate_special_day_rest_day", "1.50")
                                        : decimalSetting("rate_special_day", "1.30");
                        case DOUBLE -> restDay
                                        ? decimalSetting("rate_double_holiday_rest_day", "3.90")
                                        : decimalSetting("rate_double_holiday", "3.00");
                };

                // An unworked special day is not deducted (policy 2026-10-23):
                // factor 261 already pays for it. An unworked holiday on a rest
                // day is not paid: base pay does not cover rest days.
                BigDecimal unworked = restDay
                                ? BigDecimal.ZERO
                                : holidayType == Holiday.Type.DOUBLE
                                                ? decimalSetting("rate_double_holiday_unworked", "2.00")
                                                : BigDecimal.ONE;

                return new DayRates(worked, unworked, worked.multiply(premiumFactor));
        }

        public BigDecimal getNightDifferentialRate() {
                return decimalSetting("night_differential_rate", "0.10");
        }

        public LocalTime getNightDifferentialStart() {
                return systemSettingService.findSettingValue("night_differential_start")
                                .map(LocalTime::parse).orElse(LocalTime.of(22, 0));
        }

        public LocalTime getNightDifferentialEnd() {
                return systemSettingService.findSettingValue("night_differential_end")
                                .map(LocalTime::parse).orElse(LocalTime.of(6, 0));
        }

        /**
         * A numeric setting added by migration V6 or used by the night
         * differential. The default is the value the migration seeds, so a
         * missing row cannot break payroll.
         */
        public BigDecimal decimalSetting(String settingName, String defaultValue) {
                String value = systemSettingService.findSettingValue(settingName).orElse(defaultValue);
                try {
                        return new BigDecimal(value.trim());
                } catch (NumberFormatException e) {
                        throw new IllegalStateException(
                                        "Invalid " + settingName + " setting; expected a number");
                }
        }

        // ---------- work schedule ----------

        /** Rest days from the rest_days setting; defaults to Saturday and Sunday. */
        public Set<DayOfWeek> getRestDays() {

                String value = systemSettingService
                                .findSettingValue("rest_days")
                                .orElse("SATURDAY,SUNDAY");

                return parseRestDays(value);
        }

        public static Set<DayOfWeek> parseRestDays(String value) {

                Set<DayOfWeek> restDays = EnumSet.noneOf(DayOfWeek.class);

                if (value == null || value.isBlank()
                                || value.trim().equalsIgnoreCase("NONE")) {
                        return restDays;
                }

                for (String part : value.split(",")) {
                        try {
                                restDays.add(DayOfWeek.valueOf(part.trim().toUpperCase()));
                        } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException(
                                                "Invalid rest day: " + part.trim());
                        }
                }

                return restDays;
        }

        public boolean isWorkingDay(LocalDate date, Set<DayOfWeek> restDays) {
                return !restDays.contains(date.getDayOfWeek());
        }

        /** Working (non-rest) days from start to end, inclusive. */
        public int countWorkingDays(
                        LocalDate start,
                        LocalDate end,
                        Set<DayOfWeek> restDays) {

                int count = 0;

                for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                        if (isWorkingDay(date, restDays)) {
                                count++;
                        }
                }

                return count;
        }

        public LocalTime getStandardStartTime() {
                return parseTime("standard_start_time");
        }

        public LocalTime getStandardEndTime() {
                return parseTime("standard_end_time");
        }

        public int getUnpaidBreakMinutes() {
                return parseMinutes("unpaid_break_minutes");
        }

        public int getLateGracePeriodMinutes() {
                return parseMinutes("late_grace_period_minutes");
        }

        private LocalTime parseTime(String settingName) {
                try {
                        return LocalTime.parse(systemSettingService.getSettingValue(settingName));
                } catch (java.time.format.DateTimeParseException e) {
                        throw new IllegalStateException(
                                        "Invalid " + settingName + " setting; expected HH:mm");
                }
        }

        private int parseMinutes(String settingName) {
                try {
                        int minutes = Integer.parseInt(
                                        systemSettingService.getSettingValue(settingName).trim());

                        if (minutes < 0) {
                                throw new IllegalStateException(
                                                settingName + " cannot be negative");
                        }

                        return minutes;

                } catch (NumberFormatException e) {
                        throw new IllegalStateException(
                                        "Invalid " + settingName + " setting; expected whole minutes");
                }
        }
}
