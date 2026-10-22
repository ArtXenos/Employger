-- V2: payroll calculation snapshot, overtime-to-payroll link, and no more
-- cascading payroll deletes. Approved by the project owner on 2026-10-22.
--
-- Additive only, apart from the approved foreign-key change:
--   * new columns are NULL-able; existing rows are NOT back-filled (their
--     historical calculation inputs are unknown, so they stay NULL),
--   * no existing value is modified or deleted.
--
-- Apply AFTER a backup:
--   C:\xampp\mysql\bin\mysql.exe -u root -p employger_db < db\migrations\V2__payroll_snapshot_overtime_link_restrict_delete.sql
-- Undo: V2__rollback.sql

-- 1. Deleting an employee must never delete their payroll history.
ALTER TABLE payroll
    DROP FOREIGN KEY fk_payroll_employee,
    ADD CONSTRAINT fk_payroll_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- 2. How each payroll was calculated, captured when it is calculated.
ALTER TABLE payroll
    ADD COLUMN base_pay DECIMAL(12,2) NULL
        COMMENT 'Cycle base pay after hire-date proration, before attendance deduction',
    ADD COLUMN attendance_deduction DECIMAL(12,2) NULL
        COMMENT 'Unpaid hours x hourly rate, deducted from base_pay',
    ADD COLUMN hourly_rate DECIMAL(14,6) NULL
        COMMENT 'Unrounded hourly rate used',
    ADD COLUMN factor_rate DECIMAL(10,2) NULL
        COMMENT 'factor_rate setting used',
    ADD COLUMN regular_hours_per_day DECIMAL(5,2) NULL
        COMMENT 'regular_hours_per_day setting used',
    ADD COLUMN working_days_in_cycle INT NULL,
    ADD COLUMN working_days_employed INT NULL,
    ADD COLUMN calculation_notes TEXT NULL
        COMMENT 'Warnings at calculation time, e.g. working days without attendance',
    ADD COLUMN calculated_at DATETIME NULL;

-- 3. Which payroll paid each overtime record. Deleting a DRAFT payroll
--    releases its overtime (SET NULL) so it can be paid by a new payroll.
ALTER TABLE overtime
    ADD COLUMN payroll_id INT NULL,
    ADD KEY fk_overtime_payroll (payroll_id),
    ADD CONSTRAINT fk_overtime_payroll
        FOREIGN KEY (payroll_id) REFERENCES payroll (payroll_id)
        ON DELETE SET NULL ON UPDATE CASCADE;

-- 4. Rest days (no attendance deductions). Matches factor_rate 261.
INSERT IGNORE INTO system_settings (setting_name, setting_value, updated_at)
VALUES ('rest_days', 'SATURDAY,SUNDAY', NOW());
