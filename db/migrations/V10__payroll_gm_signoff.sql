-- V10: General Manager sign-off on payroll. Approved by the project owner
-- on 2026-10-25.
--
-- Payroll now goes DRAFT -> PROCESSED -> APPROVED -> PAID. HR processes it,
-- the General Manager (or an administrator) approves it, and only then can
-- it be marked as paid. The reviewer may instead send it back to DRAFT with
-- a reason, which is kept in review_note until it is processed again. The
-- person who processed a payroll can't approve it.
--
-- Additive: one new status value and three NULL-able columns. Existing rows
-- keep their status. PROCESSED payroll already in the database must now be
-- approved before it can be paid. Apply AFTER a backup, as root, BEFORE
-- starting the new app version:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V10__payroll_gm_signoff.sql"
-- Undo: V10__rollback.sql

ALTER TABLE payroll
    MODIFY COLUMN status ENUM('DRAFT', 'PROCESSED', 'APPROVED', 'PAID') NOT NULL DEFAULT 'DRAFT';

ALTER TABLE payroll
    ADD COLUMN approved_by INT NULL COMMENT 'User who signed off (General Manager or administrator)'
        AFTER processed_by,
    ADD COLUMN approved_at DATETIME NULL AFTER approved_by,
    ADD COLUMN review_note VARCHAR(255) NULL COMMENT 'Why it was last sent back to DRAFT'
        AFTER approved_at,
    ADD CONSTRAINT fk_payroll_approved_by FOREIGN KEY (approved_by)
        REFERENCES users (user_id) ON DELETE SET NULL ON UPDATE CASCADE;
