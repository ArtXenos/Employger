-- V13: salary history. Requested by the project owner on 2026-10-28.
--
-- Each change of an employee's base salary is recorded with the date it
-- takes effect. Payroll uses the salary in effect on each day: a change in
-- the middle of a pay cycle splits that cycle's base pay by working days,
-- and attendance deductions, premiums and overtime use the hourly rate of
-- their own day. employees.base_salary keeps the latest salary entered.
--
-- previous_salary is the salary before the change, so no history has to be
-- invented for existing employees: before their first recorded change, the
-- salary is that change's previous_salary.
--
-- Additive only: one new table. No existing value changes. Apply AFTER a
-- backup, as root, BEFORE starting the new app version (it validates the
-- schema at startup):
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V13__salary_changes.sql"
-- Undo: V13__rollback.sql

CREATE TABLE salary_changes (
    salary_change_id INT NOT NULL AUTO_INCREMENT,
    employee_id INT NOT NULL,
    previous_salary DECIMAL(12,2) NULL COMMENT 'Monthly salary before the change',
    new_salary DECIMAL(12,2) NOT NULL COMMENT 'Monthly salary from effective_date',
    effective_date DATE NOT NULL,
    reason VARCHAR(255) NULL,
    changed_by INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (salary_change_id),
    KEY idx_salary_change_employee_date (employee_id, effective_date),
    KEY fk_salary_change_user (changed_by),
    CONSTRAINT chk_salary_change_new CHECK (new_salary > 0),
    -- CASCADE like attendance and overtime: the app deletes an employee only
    -- before they have any payroll, and their history goes with them.
    CONSTRAINT fk_salary_change_employee FOREIGN KEY (employee_id)
        REFERENCES employees (employee_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_salary_change_user FOREIGN KEY (changed_by)
        REFERENCES users (user_id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
