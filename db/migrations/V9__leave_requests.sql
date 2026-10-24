-- V9: leave requests. Approved by the project owner on 2026-10-25.
--
-- Employees file Paid or Unpaid leave for a range of dates; HR approves or
-- rejects it. On approval each working day (Monday to Friday, not a
-- holiday) is marked in attendance: LEAVE for paid leave (no deduction),
-- ABSENT for unpaid leave (a day deducted). Paid leave is limited per
-- employee per year by the new setting paid_leave_days_per_year
-- (5: Service Incentive Leave, Labor Code Art. 95).
--
-- Additive only: one new table and one new settings row. No existing value
-- changes. Apply AFTER a backup, as root, BEFORE starting the new app
-- version (it validates the schema at startup):
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V9__leave_requests.sql"
-- Undo: V9__rollback.sql

CREATE TABLE leave_requests (
    leave_id INT NOT NULL AUTO_INCREMENT,
    employee_id INT NOT NULL,
    leave_type ENUM('PAID', 'UNPAID') NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    working_days INT NOT NULL COMMENT 'Weekdays in the range that are not holidays',
    reason VARCHAR(255) NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    approved_by INT NULL COMMENT 'User who approved or rejected it',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (leave_id),
    KEY idx_leave_employee_dates (employee_id, start_date, end_date),
    KEY idx_leave_status (status),
    CONSTRAINT fk_leave_employee FOREIGN KEY (employee_id)
        REFERENCES employees (employee_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_leave_approved_by FOREIGN KEY (approved_by)
        REFERENCES users (user_id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO system_settings (setting_name, setting_value, updated_at) VALUES
    ('paid_leave_days_per_year', '5', NOW());
