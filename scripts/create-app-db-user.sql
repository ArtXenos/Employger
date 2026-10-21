-- Creates a dedicated, least-privilege MariaDB account for Em-Ployger.
--
-- The application only reads and writes rows (ddl-auto=validate), so it
-- needs SELECT, INSERT, UPDATE and DELETE on employger_db and nothing else:
-- it cannot drop tables, create databases, or touch other schemas.
--
-- BEFORE RUNNING: replace CHANGE_ME_STRONG_PASSWORD below with a long,
-- unique password. Do not commit the edited file.
--
-- Run (you will be prompted for the root password; press Enter if none):
--   C:\xampp\mysql\bin\mysql.exe -u root -p < scripts\create-app-db-user.sql
--
-- Undo:
--   DROP USER 'employger_app'@'localhost';

CREATE USER IF NOT EXISTS 'employger_app'@'localhost'
    IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';

GRANT SELECT, INSERT, UPDATE, DELETE
    ON employger_db.*
    TO 'employger_app'@'localhost';

FLUSH PRIVILEGES;

SHOW GRANTS FOR 'employger_app'@'localhost';
