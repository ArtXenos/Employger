-- V6: holiday / rest-day / night premiums, earnings (bonuses, hazard pay,
-- de minimis, 13th month), the 90,000 benefits cap, year-end tax
-- annualization, and data for remittance reports.
-- Requested by the project owner on 2026-10-23 with reviewed rules; policy
-- answers recorded in docs/STATUTORY_DEDUCTIONS.md.
--
-- Additive only: new tables, new NULL-able or defaulted columns, new
-- settings rows. No existing value is changed; paid payroll is never
-- recalculated. Requires V5. Apply AFTER a backup, as root:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V6__holidays_benefits_annualization_reports.sql"
-- Undo: V6__rollback.sql

-- 1. Holiday calendar. HR enters each year's proclaimed holidays.
CREATE TABLE holidays (
    holiday_id INT NOT NULL AUTO_INCREMENT,
    holiday_date DATE NOT NULL,
    name VARCHAR(100) NOT NULL,
    holiday_type ENUM('REGULAR', 'SPECIAL', 'DOUBLE') NOT NULL
        COMMENT 'REGULAR holiday, SPECIAL non-working day, DOUBLE = two regular holidays',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (holiday_id),
    UNIQUE KEY uk_holidays_date (holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Compensation from a previous employer in the same year (from the
--    employee's BIR Form 2316), used by year-end annualization.
CREATE TABLE prior_employer_compensation (
    prior_id INT NOT NULL AUTO_INCREMENT,
    employee_id INT NOT NULL,
    tax_year INT NOT NULL,
    employer_name VARCHAR(150) NOT NULL,
    employer_tin VARCHAR(20) NULL,
    taxable_compensation DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    exempt_benefits DECIMAL(12,2) NOT NULL DEFAULT 0.00
        COMMENT 'Non-taxable 13th month pay and other benefits (counts toward the 90,000 cap)',
    tax_withheld DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (prior_id),
    UNIQUE KEY uk_prior_employee_year (employee_id, tax_year),
    CONSTRAINT fk_prior_employee FOREIGN KEY (employee_id)
        REFERENCES employees (employee_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Employees: 13th-month eligibility and government numbers for reports.
ALTER TABLE employees
    ADD COLUMN managerial BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'Managerial employees are not entitled to mandatory 13th month pay (PD 851)'
        AFTER minimum_wage_earner,
    ADD COLUMN sss_number VARCHAR(20) NULL AFTER managerial,
    ADD COLUMN philhealth_number VARCHAR(20) NULL AFTER sss_number,
    ADD COLUMN pagibig_number VARCHAR(20) NULL AFTER philhealth_number,
    ADD COLUMN tin VARCHAR(20) NULL AFTER pagibig_number;

-- 4. Earnings entries (the existing, empty bonuses table): which payroll
--    paid each one. Deleting a DRAFT payroll releases them (SET NULL).
ALTER TABLE bonuses
    ADD COLUMN payroll_id INT NULL,
    ADD KEY fk_bonuses_payroll (payroll_id),
    ADD CONSTRAINT fk_bonuses_payroll
        FOREIGN KEY (payroll_id) REFERENCES payroll (payroll_id)
        ON DELETE SET NULL ON UPDATE CASCADE;

-- 5. Payroll breakdown. NULL on payroll calculated before V6.
ALTER TABLE payroll
    ADD COLUMN holiday_premium_pay DECIMAL(12,2) NULL
        COMMENT 'Holiday and rest-day pay on regular hours, beyond what base pay covers',
    ADD COLUMN night_differential_pay DECIMAL(12,2) NULL,
    ADD COLUMN hazard_pay DECIMAL(12,2) NULL,
    ADD COLUMN thirteenth_month_pay DECIMAL(12,2) NULL,
    ADD COLUMN other_benefits_pay DECIMAL(12,2) NULL
        COMMENT 'Christmas bonus, productivity incentives, loyalty awards and similar',
    ADD COLUMN de_minimis_pay DECIMAL(12,2) NULL,
    ADD COLUMN taxable_allowance_pay DECIMAL(12,2) NULL
        COMMENT 'Commissions and taxable allowances',
    ADD COLUMN de_minimis_exempt DECIMAL(12,2) NULL
        COMMENT 'Part of de_minimis_pay within the de minimis limits',
    ADD COLUMN exempt_benefits DECIMAL(12,2) NULL
        COMMENT '13th month, other benefits and excess de minimis within the 90,000 yearly cap',
    ADD COLUMN mwe_exempt_pay DECIMAL(12,2) NULL
        COMMENT 'MWE pay exempt from tax: minimum wage, holiday, overtime, night differential, hazard',
    ADD COLUMN tax_refund DECIMAL(12,2) NULL
        COMMENT 'Year-end refund of excess tax withheld, added to net pay',
    ADD COLUMN annualized_tax_due DECIMAL(12,2) NULL
        COMMENT 'Set on the payroll where year-end annualization was applied';

-- 6. Rates and limits. Multipliers are of the hourly basic rate.
INSERT IGNORE INTO system_settings (setting_name, setting_value, updated_at) VALUES
    ('rate_regular_holiday', '2.00', NOW()),
    ('rate_regular_holiday_rest_day', '2.60', NOW()),
    ('rate_special_day', '1.30', NOW()),
    ('rate_special_day_rest_day', '1.50', NOW()),
    ('rate_double_holiday', '3.00', NOW()),
    ('rate_double_holiday_rest_day', '3.90', NOW()),
    ('rate_double_holiday_unworked', '2.00', NOW()),
    ('rate_rest_day', '1.30', NOW()),
    ('overtime_premium_factor', '1.30', NOW()),
    ('benefits_exemption_cap', '90000', NOW()),
    ('de_minimis_rice_monthly', '2000', NOW()),
    ('de_minimis_clothing_annual', '6000', NOW()),
    ('de_minimis_medical_semester', '1500', NOW()),
    ('de_minimis_leave_days', '10', NOW());
