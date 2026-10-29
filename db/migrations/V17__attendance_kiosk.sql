-- V17: Attendance kiosk. Agreed with the owner on 2026-10-29.
--
-- Employees clock in and out by showing a printed QR badge to the webcam of
-- the kiosk computer at the entrance. The kiosk page logs in with its own
-- KIOSK account, which can only record scans; the server takes the time
-- from its own clock and fills the existing attendance.time_in/time_out,
-- so hours, LATE, payroll locks and payroll itself work as before.
--
-- 1. employees.badge_code: the random code printed on the employee's badge
--    (e.g. EMP-7Q4K-M2XP-9D3H-K8RT). NULL until HR issues a badge.
--    Reissuing a lost badge replaces the code, so the old card stops
--    working. UNIQUE, so one card always means one employee.
-- 2. users.role gets KIOSK: a device login, not linked to an employee
--    (users.employee_id is already NULL-able).
--
-- Additive only: one new NULL-able column and one new role value. No
-- existing value changes and attendance is untouched. Apply AFTER a
-- backup, as root, BEFORE starting the new app version (it validates the
-- schema at startup):
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V17__attendance_kiosk.sql"
-- Undo: V17__rollback.sql

ALTER TABLE employees
    ADD COLUMN badge_code VARCHAR(32) NULL
        COMMENT 'Code on the attendance badge; NULL = no badge issued'
        AFTER designation,
    ADD UNIQUE KEY uq_employee_badge_code (badge_code);

ALTER TABLE users
    MODIFY COLUMN role ENUM('HR_MANAGER', 'GENERAL_MANAGER', 'ADMINISTRATOR', 'EMPLOYEE', 'KIOSK') NOT NULL;
