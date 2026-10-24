-- V7: failed logins and lockouts survive an application restart.
-- Approved by the project owner on 2026-10-25.
--
-- Before, LoginAttemptService kept its counts only in memory, so restarting
-- the app cleared a 15-minute lockout early. Each row is a failed login that
-- counted toward the limit; the failure that reached the limit carries
-- locked_until. Times are UTC. The app deletes rows once they no longer
-- matter (a failure older than 15 minutes, or a lock that has ended).
--
-- Additive only: one new table, nothing existing changes. The app account
-- already has SELECT, INSERT, UPDATE and DELETE on employger_db.*, so no
-- GRANT is needed. Apply AFTER a backup, as root, BEFORE starting the new
-- app version (it validates the schema at startup):
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V7__login_attempts.sql"
-- Undo: V7__rollback.sql

CREATE TABLE login_attempts (
    attempt_id BIGINT NOT NULL AUTO_INCREMENT,
    kind ENUM('USERNAME', 'ADDRESS') NOT NULL
        COMMENT 'USERNAME = counted per lower-cased username, ADDRESS = per client IP address',
    attempt_key VARCHAR(255) NOT NULL,
    failed_at DATETIME NOT NULL COMMENT 'UTC',
    locked_until DATETIME NULL COMMENT 'UTC; set on the failure that reached the limit',
    PRIMARY KEY (attempt_id),
    KEY idx_login_attempts_key (kind, attempt_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
