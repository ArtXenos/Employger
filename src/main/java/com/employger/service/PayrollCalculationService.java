package com.employger.service;

import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PayrollCalculationService {

        private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

        private static final int INTERMEDIATE_SCALE = 6;

        private static final int FINAL_MONETARY_SCALE = 2;

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

        public BigDecimal calculateHourlyRate(
                        Employee employee) {

                if (employee.getBaseSalary() == null) {
                        return BigDecimal.ZERO.setScale(
                                        FINAL_MONETARY_SCALE,
                                        RoundingMode.HALF_UP);
                }

                BigDecimal annualSalary = calculateAnnualSalary(employee);

                BigDecimal factorRate = calculateFactorRate();

                BigDecimal dailyRate = annualSalary.divide(
                                factorRate,
                                INTERMEDIATE_SCALE,
                                RoundingMode.HALF_UP);

                BigDecimal regularHoursPerDay = calculateRegularHoursPerDay();

                BigDecimal hourlyRate = dailyRate.divide(
                                regularHoursPerDay,
                                INTERMEDIATE_SCALE,
                                RoundingMode.HALF_UP);

                return hourlyRate.setScale(
                                FINAL_MONETARY_SCALE,
                                RoundingMode.HALF_UP);
        }

        public BigDecimal calculateCycleBasePay(
                        Employee employee,
                        PayCycle.CycleType cycleType) {

                if (employee.getBaseSalary() == null) {
                        return BigDecimal.ZERO.setScale(
                                        FINAL_MONETARY_SCALE,
                                        RoundingMode.HALF_UP);
                }

                BigDecimal annualSalary = calculateAnnualSalary(employee);

                int periodsPerYear;

                switch (cycleType) {

                        case WEEKLY:
                                periodsPerYear = 52;
                                break;

                        case BI_WEEKLY:
                                periodsPerYear = 26;
                                break;

                        case SEMI_MONTHLY:
                                periodsPerYear = 24;
                                break;

                        case MONTHLY:
                                periodsPerYear = 12;
                                break;

                        default:
                                throw new IllegalArgumentException(
                                                "Unsupported pay cycle type: "
                                                                + cycleType);
                }

                return annualSalary.divide(
                                BigDecimal.valueOf(periodsPerYear),
                                FINAL_MONETARY_SCALE,
                                RoundingMode.HALF_UP);
        }

        public BigDecimal calculateOvertimeRate(
                        Employee employee,
                        BigDecimal overtimeMultiplier) {

                BigDecimal hourlyRate = calculateHourlyRate(employee);

                return hourlyRate
                                .multiply(overtimeMultiplier)
                                .setScale(
                                                FINAL_MONETARY_SCALE,
                                                RoundingMode.HALF_UP);
        }

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

                BigDecimal overtimeRate = calculateOvertimeRate(
                                employee,
                                overtimeMultiplier);

                return overtimeRate
                                .multiply(overtimeHours)
                                .setScale(
                                                FINAL_MONETARY_SCALE,
                                                RoundingMode.HALF_UP);
        }
}