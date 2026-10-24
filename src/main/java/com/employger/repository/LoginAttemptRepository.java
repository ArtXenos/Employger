package com.employger.repository;

import com.employger.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    List<LoginAttempt> findAllByOrderByFailedAtAsc();

    @Modifying
    void deleteByKindAndAttemptKey(LoginAttempt.Kind kind, String attemptKey);

    @Modifying
    void deleteByKind(LoginAttempt.Kind kind);

    // Failures older than the window that never caused a lock, and locks
    // that have ended, no longer matter.
    @Modifying
    @Query("""
            delete from LoginAttempt a
            where (a.lockedUntil is null and a.failedAt <= :windowStart)
               or (a.lockedUntil is not null and a.lockedUntil <= :now)
            """)
    void deleteExpired(@Param("windowStart") LocalDateTime windowStart, @Param("now") LocalDateTime now);
}
