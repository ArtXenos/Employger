-- V4: per-employee minimum wage earner (MWE) flag for the withholding tax
-- exemption. Approved by the project owner on 2026-10-23.
--
-- RR 11-2018 (amending RR 2-98 Sec. 2.78.1(B)(13) and 2.79(A)): the
-- statutory minimum wage, holiday pay, overtime pay, night shift
-- differential and hazard pay of an MWE are exempt from income tax and
-- withholding tax; other compensation from the same employer is still
-- withheld using the table.
--
-- Additive only: every existing employee gets 0 (not an MWE), so no
-- existing calculation changes. Paid payroll is never recalculated.
--
-- Apply AFTER a backup, as root, on the MariaDB 10.11 server:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V4__minimum_wage_earner.sql"
-- Undo: V4__rollback.sql

ALTER TABLE employees
    ADD COLUMN minimum_wage_earner BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'Paid the regional statutory minimum wage: regular and overtime pay exempt from withholding tax'
        AFTER base_salary;
