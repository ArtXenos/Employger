-- Rollback for V10. Run only if V10 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V10__rollback.sql"
--
-- APPROVED payroll goes back to PROCESSED (the step before sign-off), and
-- who approved it and any review notes are forgotten. Deploy the
-- application version from before V10 at the same time, or the app will
-- fail schema validation at startup.

UPDATE payroll SET status = 'PROCESSED' WHERE status = 'APPROVED';

ALTER TABLE payroll
    DROP FOREIGN KEY fk_payroll_approved_by;

ALTER TABLE payroll
    DROP COLUMN review_note,
    DROP COLUMN approved_at,
    DROP COLUMN approved_by;

ALTER TABLE payroll
    MODIFY COLUMN status ENUM('DRAFT', 'PROCESSED', 'PAID') NOT NULL DEFAULT 'DRAFT';
