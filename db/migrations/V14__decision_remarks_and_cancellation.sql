-- V14: reasons for decisions, and cancelling instead of deleting.
-- Requested by the project manager on 2026-10-28.
--
-- 1. Rejecting overtime or leave needs a reason, stored in decision_remarks
--    and shown to the employee.
-- 2. Overtime and attendance can no longer be deleted: they are history.
--    Approved overtime that won't be worked is CANCELLED instead, with a
--    reason, who cancelled it and when. Rejected means "never allowed";
--    cancelled means "allowed, then called off", so the approval stays on
--    record. An employee may cancel their own overtime; HR cancels
--    approved overtime. Overtime already in processed payroll can't be
--    cancelled (record a correction instead).
--    Leave gets the same columns, because both share one lifecycle in the
--    code (ApprovalRequest); leave cancellation isn't offered yet.
--
-- Additive only: new NULL-able columns, and CANCELLED added to the status
-- lists. No existing value changes; requests decided before V14 have no
-- remarks. Apply AFTER a backup, as root, BEFORE starting the new app
-- version (it validates the schema at startup):
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V14__decision_remarks_and_cancellation.sql"
-- Undo: V14__rollback.sql

ALTER TABLE overtime
    MODIFY COLUMN status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    ADD COLUMN decision_remarks VARCHAR(255) NULL COMMENT 'Why it was rejected; NULL before V14',
    ADD COLUMN cancel_reason VARCHAR(255) NULL COMMENT 'Why it was cancelled',
    ADD COLUMN cancelled_by INT NULL,
    ADD COLUMN cancelled_at DATETIME NULL,
    ADD KEY fk_overtime_cancelled_by (cancelled_by),
    ADD CONSTRAINT fk_overtime_cancelled_by FOREIGN KEY (cancelled_by)
        REFERENCES users (user_id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE leave_requests
    MODIFY COLUMN status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    ADD COLUMN decision_remarks VARCHAR(255) NULL COMMENT 'Why it was rejected; NULL before V14',
    ADD COLUMN cancel_reason VARCHAR(255) NULL COMMENT 'Why it was cancelled',
    ADD COLUMN cancelled_by INT NULL,
    ADD COLUMN cancelled_at DATETIME NULL,
    ADD KEY fk_leave_cancelled_by (cancelled_by),
    ADD CONSTRAINT fk_leave_cancelled_by FOREIGN KEY (cancelled_by)
        REFERENCES users (user_id) ON DELETE SET NULL ON UPDATE CASCADE;
