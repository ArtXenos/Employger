-- Rollback for V7. Run only if V7 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V7__rollback.sql"
--
-- This forgets current failed-login counts and lockouts. Deploy the
-- application version from before V7 at the same time, or the app will
-- fail schema validation at startup.

DROP TABLE login_attempts;
