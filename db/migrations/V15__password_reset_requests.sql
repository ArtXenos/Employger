-- V15: forgot password. Requested by the project owner on 2026-10-28.
--
-- A user who forgot their password asks for a reset from the login page.
-- Each request is a row in password_reset_requests and shows up for the
-- administrators (a badge on the Users link and a list on the Users page).
-- An administrator checks who is asking, sets a temporary password and
-- gives it to the person in person. users.must_change_password then makes
-- that person choose their own password before they can do anything else.
--
-- Nothing is e-mailed: the system has no mail server, and a temporary
-- password handed over in person can't be intercepted on the way.
--
-- Additive only: one new column (FALSE for every existing login) and one
-- new table. Apply AFTER a backup, as root, BEFORE starting the new app
-- version (it validates the schema at startup):
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V15__password_reset_requests.sql"
-- Undo: V15__rollback.sql

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'TRUE after an administrator set a temporary password'
        AFTER status;

CREATE TABLE password_reset_requests (
    request_id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    status ENUM('PENDING', 'COMPLETED', 'DISMISSED') NOT NULL DEFAULT 'PENDING'
        COMMENT 'COMPLETED = a temporary password was set; DISMISSED = closed without one',
    requested_at DATETIME NOT NULL,
    requested_from VARCHAR(45) NULL COMMENT 'Client IP address of the request',
    handled_by INT NULL,
    handled_at DATETIME NULL,
    PRIMARY KEY (request_id),
    KEY idx_password_reset_status (status, requested_at),
    KEY fk_password_reset_user (user_id),
    KEY fk_password_reset_handled_by (handled_by),
    -- A request belongs to its login and goes with it.
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_password_reset_handled_by FOREIGN KEY (handled_by)
        REFERENCES users (user_id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
