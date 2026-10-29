-- Rollback for V17. Run only if V17 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V17__rollback.sql"
--
-- Deletes the kiosk logins (the role list can't drop KIOSK while a login
-- has it; their past audit entries keep the text, with user_id set to
-- NULL), then drops the badge codes, so every printed badge stops working.
-- Attendance recorded at the kiosk stays: it is ordinary attendance.
-- Deploy the application version from before V17 at the same time, or the
-- app will fail schema validation at startup.

DELETE FROM users WHERE role = 'KIOSK';

ALTER TABLE users
    MODIFY COLUMN role ENUM('HR_MANAGER', 'GENERAL_MANAGER', 'ADMINISTRATOR', 'EMPLOYEE') NOT NULL;

ALTER TABLE employees
    DROP KEY uq_employee_badge_code,
    DROP COLUMN badge_code;
