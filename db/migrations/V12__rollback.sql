-- Rollback for V12. Run only if V12 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V12__rollback.sql"
--
-- This deletes every recorded payroll correction. Payroll already
-- calculated keeps its gross and net pay, but no longer shows which part
-- was a correction. Deploy the application version from before V12 at the
-- same time, or the app will fail schema validation at startup.

ALTER TABLE payroll
    DROP COLUMN adjustment_amount;

DROP TABLE payroll_adjustments;
