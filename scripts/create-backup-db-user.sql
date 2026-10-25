-- Creates a read-only MariaDB account for backups (scripts\backup-db.ps1),
-- so the scheduled backup never needs the root password.
--
-- It can read employger_db and nothing else: it cannot insert, change,
-- delete or drop anything. SHOW VIEW, TRIGGER, EVENT and LOCK TABLES are
-- what mysqldump needs to dump the schema completely.
--
-- BEFORE RUNNING: replace CHANGE_ME_STRONG_PASSWORD below with a long,
-- unique password. Do not commit the edited file. Put the same password
-- in the options file that schedule-backup.ps1 describes.
--
-- Run as root on the 10.11 server:
--   Get-Content my-create-backup-db-user.sql | & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -P 3307
--
-- Undo:
--   DROP USER 'employger_backup'@'localhost';

CREATE USER IF NOT EXISTS 'employger_backup'@'localhost'
    IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';

GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES
    ON employger_db.*
    TO 'employger_backup'@'localhost';

FLUSH PRIVILEGES;

SHOW GRANTS FOR 'employger_backup'@'localhost';
