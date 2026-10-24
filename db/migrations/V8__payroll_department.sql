-- V8: each payroll remembers the department it was calculated in.
-- Approved by the project owner on 2026-10-25.
--
-- Before, the company overview counted every payroll under the employee's
-- CURRENT department, so moving an employee also moved their past cost.
-- The app now copies the employee's department onto the payroll each time
-- it is calculated; once processed, a payroll is never recalculated, so the
-- value is fixed from then on.
--
-- Additive only: one NULL-able column. Existing rows are NOT back-filled
-- (their department at the time is unknown) and stay NULL; the overview
-- uses the employee's current department for them, exactly as before, so
-- no total changes. No amount is touched.
--
-- Requires V7 if you are applying both. Apply AFTER a backup, as root,
-- BEFORE starting the new app version (it validates the schema at startup):
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V8__payroll_department.sql"
-- Undo: V8__rollback.sql

ALTER TABLE payroll
    ADD COLUMN department VARCHAR(100) NULL
        COMMENT 'Employee department when this payroll was calculated; NULL = before V8'
        AFTER calculated_at;
