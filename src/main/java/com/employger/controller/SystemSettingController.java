package com.employger.controller;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.SystemSetting;
import com.employger.service.SystemSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    public SystemSettingController(
            SystemSettingService systemSettingService) {

        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    public List<SystemSetting> getAllSettings() {
        return systemSettingService.getAllSettings();
    }

    @GetMapping("/{settingName}")
    public ResponseEntity<SystemSetting> getSettingByName(
            @PathVariable String settingName) {

        return systemSettingService
                .getSettingByName(settingName)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{settingName}")
    public ResponseEntity<SystemSetting> updateSetting(
            @PathVariable String settingName,
            @RequestBody SystemSetting settingDetails) {

        try {
            SystemSetting updatedSetting = systemSettingService.updateSetting(
                    settingName,
                    settingDetails.getSettingValue());

            return ResponseEntity.ok(updatedSetting);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}