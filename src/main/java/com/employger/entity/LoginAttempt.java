package com.employger.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A failed login that counted toward the guessing limit (migration V7).
 * If it was the failure that reached the limit, lockedUntil says how long
 * the username or address is locked. Times are UTC.
 *
 * Kept in the database so a restart doesn't forget counts and locks.
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Long attemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    // A lower-cased username or a client address.
    @Column(name = "attempt_key", nullable = false, length = 255)
    private String attemptKey;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    public enum Kind {
        USERNAME,
        ADDRESS
    }

    protected LoginAttempt() {
        // for JPA
    }

    public LoginAttempt(Kind kind, String attemptKey, LocalDateTime failedAt, LocalDateTime lockedUntil) {
        this.kind = kind;
        this.attemptKey = attemptKey;
        this.failedAt = failedAt;
        this.lockedUntil = lockedUntil;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getAttemptKey() {
        return attemptKey;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
