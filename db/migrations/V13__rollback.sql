-- Rollback for V13. Run only if V13 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V13__rollback.sql"
--
-- This deletes the salary history. Each employee keeps their latest base
-- salary, and payroll already calculated keeps its amounts. Deploy the
-- application version from before V13 at the same time, or the app will
-- fail schema validation at startup.

DROP TABLE salary_changes;
