
package com.employger.service;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.SystemSetting;
import com.employger.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;

    private static final Set<String> ALLOWED_SETTINGS = Set.of(
            "application_name",
            "company_name",
            "currency_code",
            "currency_symbol",
            "default_overtime_rate",
            "factor_rate",
            "late_deduction_amount",
            "absent_deduction_amount",
            "standard_start_time",
            "standard_end_time",
            "unpaid_break_minutes",
            "regular_hours_per_day",
            "late_grace_period_minutes",
            "night_differential_rate",
            "night_differential_start",
            "night_differential_end",
            "rest_days",
            "statutory_deductions_enabled",
            "sss_employee_rate",
            "sss_msc_minimum",
            "sss_msc_maximum",
            "sss_msc_step",
            "philhealth_premium_rate",
            "philhealth_salary_floor",
            "philhealth_salary_ceiling",
            "philhealth_employee_share",
            "pagibig_employee_rate",
            "pagibig_low_salary_rate",
            "pagibig_low_salary_threshold",
            "pagibig_max_fund_salary");

    private final AuditService auditService;

    public SystemSettingService(
            SystemSettingRepository systemSettingRepository,
            AuditService auditService) {
        this.systemSettingRepository = systemSettingRepository;
        this.auditService = auditService;
    }

    public List<SystemSetting> getAllSettings() {
        return systemSettingRepository.findAll();
    }

    public Optional<SystemSetting> getSettingByName(
            String settingName) {
        return systemSettingRepository.findBySettingName(settingName);
    }

    @Transactional
    public SystemSetting updateSetting(
            String settingName,
            String settingValue) {

        if (settingName == null ||
                !ALLOWED_SETTINGS.contains(settingName)) {
            throw new IllegalArgumentException(
                    "Unsupported setting: " + settingName);
        }

        if (settingValue == null || settingValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Setting value cannot be empty");
        }

        validateSetting(settingName, settingValue.trim());

        SystemSetting setting = systemSettingRepository
                .findBySettingName(settingName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Setting not found: " + settingName));

        String previousValue = setting.getSettingValue();

        setting.setSettingValue(settingValue.trim());
        setting.setUpdatedBy(auditService.currentUser().orElse(null));

        auditService.record("SETTING_UPDATED",
                "Changed " + settingName + ": " + previousValue + " -> " + setting.getSettingValue());

        return systemSettingRepository.save(setting);
    }

    public Optional<String> findSettingValue(String settingName) {
        return systemSettingRepository
                .findBySettingName(settingName)
                .map(SystemSetting::getSettingValue);
    }

    public String getSettingValue(String settingName) {
        return systemSettingRepository
                .findBySettingName(settingName)
                .map(SystemSetting::getSettingValue)
                .orElseThrow(() -> new IllegalStateException(
                        "System setting is not configured: " + settingName));
    }

    private void validateSetting(String name, String value) {
        switch (name) {
            case "application_name":
            case "company_name":
                if (value.length() > 100) {
                    throw new IllegalArgumentException(
                            name + " cannot exceed 100 characters");
                }
                break;

            case "currency_code":
                if (!value.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException(
                            "Currency code must be three uppercase letters");
                }
                break;

            case "currency_symbol":
                if (value.length() > 10) {
                    throw new IllegalArgumentException(
                            "Currency symbol cannot exceed 10 characters");
                }
                break;

            case "default_overtime_rate":
                validateDecimal(name, value,
                        new BigDecimal("0.01"),
                        new BigDecimal("10"));
                break;

            case "factor_rate":
                validateInteger(name, value, 1, 366);
                break;

            case "late_deduction_amount":
            case "absent_deduction_amount":
                validateDecimal(name, value,
                        BigDecimal.ZERO, new BigDecimal("999999999"));
                break;

            case "unpaid_break_minutes":
                validateInteger(name, value, 0, 360);
                break;

            case "late_grace_period_minutes":
                validateInteger(name, value, 0, 120);
                break;

            case "regular_hours_per_day":
                validateDecimal(name, value,
                        BigDecimal.ONE, new BigDecimal("24"));
                break;

            case "night_differential_rate":
                validateDecimal(name, value,
                        BigDecimal.ZERO, new BigDecimal("5"));
                break;

            case "standard_start_time":
            case "standard_end_time":
            case "night_differential_start":
            case "night_differential_end":
                try {
                    LocalTime.parse(value);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            name + " must use HH:mm time format");
                }
                break;

            case "statutory_deductions_enabled":
                if (!value.equals("true") && !value.equals("false")) {
                    throw new IllegalArgumentException(
                            name + " must be true or false");
                }
                break;

            case "sss_employee_rate":
            case "philhealth_premium_rate":
            case "pagibig_employee_rate":
            case "pagibig_low_salary_rate":
                validateDecimal(name, value,
                        BigDecimal.ZERO, new BigDecimal("0.5"));
                break;

            case "philhealth_employee_share":
                validateDecimal(name, value,
                        BigDecimal.ZERO, BigDecimal.ONE);
                break;

            case "sss_msc_minimum":
            case "sss_msc_maximum":
            case "sss_msc_step":
            case "philhealth_salary_floor":
            case "philhealth_salary_ceiling":
            case "pagibig_low_salary_threshold":
            case "pagibig_max_fund_salary":
                validateDecimal(name, value,
                        BigDecimal.ONE, new BigDecimal("10000000"));
                break;

            case "rest_days":
                // Comma-separated day names, e.g. SATURDAY,SUNDAY, or NONE.
                if (PayrollCalculationService.parseRestDays(value).size() == 7) {
                    throw new IllegalArgumentException(
                            "rest_days cannot include every day of the week");
                }
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported setting: " + name);
        }
    }

    private void validateInteger(
            String name, String value, int min, int max) {
        try {
            int number = Integer.parseInt(value);

            if (number < min || number > max) {
                throw new IllegalArgumentException(
                        name + " must be between " + min + " and " + max);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    name + " must be a whole number");
        }
    }

    private void validateDecimal(
            String name,
            String value,
            BigDecimal min,
            BigDecimal max) {
        try {
            BigDecimal number = new BigDecimal(value);

            if (number.compareTo(min) < 0 ||
                    number.compareTo(max) > 0) {
                throw new IllegalArgumentException(
                        name + " must be between " + min + " and " + max);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    name + " must be a valid number");
        }
    }
}
