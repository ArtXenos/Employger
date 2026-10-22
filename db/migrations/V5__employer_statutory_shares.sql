-- V5: employer shares of SSS (with EC), PhilHealth and Pag-IBIG, recorded
-- on each payroll. Approved by the project owner on 2026-10-23.
-- Employer shares are a cost to the company, not deductions from pay:
-- net pay does not change.
--
-- Additive only: new NULL-able payroll columns (existing payroll keeps NULL,
-- since its employer shares were never calculated) and new settings rows.
-- Requires V3. Apply AFTER a backup, as root, on the MariaDB 10.11 server:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V5__employer_statutory_shares.sql"
-- Undo: V5__rollback.sql
--
-- Rates verified 2026-10-23 against official sources (docs/STATUTORY_DEDUCTIONS.md):
--   SSS        Circular 2024-006 schedule table (effective Jan 2025): employer
--              10% of the total MSC (regular SS + MPF); EC 10.00 for MSC
--              below 15,000 and 30.00 from MSC 15,000.
--   PhilHealth premium shared equally (RA 11223): employer pays the premium
--              less the employee share; no new setting.
--   Pag-IBIG   RA 9679 Sec. 7: all employers 2% of monthly compensation,
--              also for employees earning 1,500 or less; maximum fund
--              salary 10,000 (Circular 460, existing setting).

ALTER TABLE payroll
    ADD COLUMN sss_employer_contribution DECIMAL(12,2) NULL
        COMMENT 'Employer SSS share (regular SS + MPF) for this cycle; not deducted from pay',
    ADD COLUMN sss_ec_contribution DECIMAL(12,2) NULL
        COMMENT 'Employer Employees Compensation (EC) contribution for this cycle',
    ADD COLUMN philhealth_employer_contribution DECIMAL(12,2) NULL
        COMMENT 'Employer PhilHealth share for this cycle',
    ADD COLUMN pagibig_employer_contribution DECIMAL(12,2) NULL
        COMMENT 'Employer Pag-IBIG share for this cycle';

INSERT IGNORE INTO system_settings (setting_name, setting_value, updated_at) VALUES
    ('sss_employer_rate', '0.10', NOW()),
    ('sss_ec_msc_threshold', '15000', NOW()),
    ('sss_ec_amount_low', '10', NOW()),
    ('sss_ec_amount_high', '30', NOW()),
    ('pagibig_employer_rate', '0.02', NOW());
