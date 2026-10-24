-- Rollback for V9. Run only if V9 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V9__rollback.sql"
--
-- This deletes every leave request. Attendance days already marked LEAVE or
-- ABSENT by approved leave are kept. Deploy the application version from
-- before V9 at the same time, or the app will fail schema validation.

DROP TABLE leave_requests;

DELETE FROM system_settings WHERE setting_name = 'paid_leave_days_per_year';
