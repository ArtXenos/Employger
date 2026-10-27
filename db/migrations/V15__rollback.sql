-- Rollback for V15. Run only if V15 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V15__rollback.sql"
--
-- This deletes the password reset requests. A login that still has to
-- replace a temporary password can then keep using it. Deploy the
-- application version from before V15 at the same time, or the app will
-- fail schema validation at startup.

DROP TABLE password_reset_requests;

ALTER TABLE users DROP COLUMN must_change_password;
