-- Rollback for V8. Run only if V8 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V8__rollback.sql"
--
-- This forgets which department each payroll was calculated in; the
-- company overview goes back to using each employee's current department.
-- Deploy the application version from before V8 at the same time, or the
-- app will fail schema validation at startup.

ALTER TABLE payroll
    DROP COLUMN department;
