package com.employger.repository;

import com.employger.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Integer> {

    List<AuditLog> findTop200ByOrderByAuditIdDesc();
}
