package com.employger.dto;

import com.employger.entity.SystemSetting;

import java.time.LocalDateTime;

public record SystemSettingResponse(
        Integer settingId,
        String settingName,
        String settingValue,
        UserSummary updatedBy,
        LocalDateTime updatedAt) {

    public static SystemSettingResponse from(SystemSetting setting) {
        return new SystemSettingResponse(
                setting.getSettingId(),
                setting.getSettingName(),
                setting.getSettingValue(),
                UserSummary.from(setting.getUpdatedBy()),
                setting.getUpdatedAt());
    }
}
