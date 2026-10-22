-- V3: Philippine statutory deductions (employee shares) and withholding tax.
-- Approved by the project owner on 2026-10-22 ("verified rates").
-- Additive only: new NULL-able payroll columns and new settings rows.
-- Requires V2. Apply AFTER a backup:
--   C:\xampp\mysql\bin\mysql.exe -u root -p employger_db < db\migrations\V3__statutory_deductions.sql
-- Undo: V3__rollback.sql
--
-- Rates verified 2026-10-22 against official sources (see docs/STATUTORY_DEDUCTIONS.md):
--   SSS        Circular 2024-006 (effective Jan 2025): employee 5% of MSC,
--              MSC 5,000-35,000 in 500 steps.
--   PhilHealth Advisory 2026-0042 / Circular 2020-0005 Rev. 1: 5% of monthly
--              basic salary, floor 10,000, ceiling 100,000; employee pays half.
--   Pag-IBIG   Circular 460 (effective Feb 2024): 2%, maximum fund salary
--              10,000; 1% for monthly compensation of 1,500 and below.
--   BIR        RR 11-2018 Annex E (effective Jan 1, 2023): held in code,
--              PhilippineWithholdingTax.java.

ALTER TABLE payroll
    ADD COLUMN sss_contribution DECIMAL(12,2) NULL
        COMMENT 'Employee SSS share (regular SS + MPF) for this cycle',
    ADD COLUMN philhealth_contribution DECIMAL(12,2) NULL
        COMMENT 'Employee PhilHealth share for this cycle',
    ADD COLUMN pagibig_contribution DECIMAL(12,2) NULL
        COMMENT 'Employee Pag-IBIG share for this cycle',
    ADD COLUMN taxable_compensation DECIMAL(12,2) NULL
        COMMENT 'Gross pay less employee contributions, basis of tax_amount';

INSERT IGNORE INTO system_settings (setting_name, setting_value, updated_at) VALUES
    ('statutory_deductions_enabled', 'true', NOW()),
    ('sss_employee_rate', '0.05', NOW()),
    ('sss_msc_minimum', '5000', NOW()),
    ('sss_msc_maximum', '35000', NOW()),
    ('sss_msc_step', '500', NOW()),
    ('philhealth_premium_rate', '0.05', NOW()),
    ('philhealth_salary_floor', '10000', NOW()),
    ('philhealth_salary_ceiling', '100000', NOW()),
    ('philhealth_employee_share', '0.50', NOW()),
    ('pagibig_employee_rate', '0.02', NOW()),
    ('pagibig_low_salary_rate', '0.01', NOW()),
    ('pagibig_low_salary_threshold', '1500', NOW()),
    ('pagibig_max_fund_salary', '10000', NOW());
