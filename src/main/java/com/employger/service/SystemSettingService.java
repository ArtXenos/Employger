
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
            "night_differential_end");

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
