-- Rollback for V2. Run only if V2 must be undone, and only after a backup:
--   C:\xampp\mysql\bin\mysql.exe -u root -p employger_db < db\migrations\V2__rollback.sql
--
-- WARNING: this discards the calculation snapshots and overtime-to-payroll
-- links recorded since V2 was applied. Deploy the matching application
-- version (git commit before V2) at the same time, or the app will fail
-- schema validation at startup.

DELETE FROM system_settings WHERE setting_name = 'rest_days';

ALTER TABLE overtime
    DROP FOREIGN KEY fk_overtime_payroll,
    DROP KEY fk_overtime_payroll,
    DROP COLUMN payroll_id;

ALTER TABLE payroll
    DROP COLUMN base_pay,
    DROP COLUMN attendance_deduction,
    DROP COLUMN hourly_rate,
    DROP COLUMN factor_rate,
    DROP COLUMN regular_hours_per_day,
    DROP COLUMN working_days_in_cycle,
    DROP COLUMN working_days_employed,
    DROP COLUMN calculation_notes,
    DROP COLUMN calculated_at;

ALTER TABLE payroll
    DROP FOREIGN KEY fk_payroll_employee;

ALTER TABLE payroll
    ADD CONSTRAINT fk_payroll_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id)
        ON DELETE CASCADE ON UPDATE CASCADE;
