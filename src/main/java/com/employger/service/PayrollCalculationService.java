package com.employger.service;

import com.employger.entity.Employee;
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
