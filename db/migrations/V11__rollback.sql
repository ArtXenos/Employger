-- Rollback for V11. Run only if V11 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V11__rollback.sql"
--
-- This forgets every employee's last day. Payroll already calculated keeps
-- its amounts. Deploy the application version from before V11 at the same
-- time, or the app will fail schema validation at startup.

ALTER TABLE employees
    DROP COLUMN separation_date;
