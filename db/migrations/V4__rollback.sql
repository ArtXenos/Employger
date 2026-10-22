-- Rollback for V4. Run only if V4 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V4__rollback.sql"
--
-- WARNING: this discards which employees are marked as minimum wage
-- earners. Deploy the application version from before V4 at the same time,
-- or the app will fail schema validation at startup.

ALTER TABLE employees
    DROP COLUMN minimum_wage_earner;
