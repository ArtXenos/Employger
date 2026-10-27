package com.employger.repository;

import com.employger.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Integer>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findTop200ByOrderByAuditIdDesc();

    /** Every action recorded so far, for the Audit Log page's filter. */
    @Query("select distinct a.action from AuditLog a order by a.action")
    List<String> findDistinctActions();
}
