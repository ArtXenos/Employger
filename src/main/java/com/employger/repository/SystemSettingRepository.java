package com.employger.repository;

import com.employger.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemSettingRepository
        extends JpaRepository<SystemSetting, Integer> {

    Optional<SystemSetting> findBySettingName(String settingName);
}