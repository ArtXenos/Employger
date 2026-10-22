package com.employger.controller;

import com.employger.dto.SettingValueRequest;
import com.employger.dto.SystemSettingResponse;
import com.employger.exception.ResourceNotFoundException;
import com.employger.service.SystemSettingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Errors (400 invalid input, 404 not found) are turned into plain-text
// responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/settings")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    public SystemSettingController(
            SystemSettingService systemSettingService) {

        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    public List<SystemSettingResponse> getAllSettings() {
        return systemSettingService.getAllSettings()
                .stream()
                .map(SystemSettingResponse::from)
                .toList();
    }

    @GetMapping("/{settingName}")
    public SystemSettingResponse getSettingByName(
            @PathVariable String settingName) {

        return systemSettingService
                .getSettingByName(settingName)
                .map(SystemSettingResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Setting not found"));
    }

    @PutMapping("/{settingName}")
    public SystemSettingResponse updateSetting(
            @PathVariable String settingName,
            @Valid @RequestBody SettingValueRequest request) {

        return SystemSettingResponse.from(
                systemSettingService.updateSetting(settingName, request.settingValue()));
    }
}
