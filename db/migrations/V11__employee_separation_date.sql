-- V11: an employee's last day of work. Approved by the project owner on
-- 2026-10-25.
--
-- HR enters the separation date when someone leaves. The final pay cycle's
-- base pay is prorated up to that day, the same way a new hire's first
-- cycle is prorated from the date hired; days after it get no attendance
-- deductions, holiday pay or missing-attendance warnings. A leaver set to
-- INACTIVE can still get payroll for cycles that start on or before their
-- last day. HR still sets the employee INACTIVE themselves.
--
-- Additive only: one NULL-able column (NULL = still employed). Apply AFTER
-- a backup, as root, BEFORE starting the new app version:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V11__employee_separation_date.sql"
-- Undo: V11__rollback.sql

ALTER TABLE employees
    ADD COLUMN separation_date DATE NULL COMMENT 'Last day of work; NULL while employed'
        AFTER date_hired;
