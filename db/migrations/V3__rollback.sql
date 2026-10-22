-- Rollback for V3. Run only after a backup, together with deploying the
-- application version from before V3 (or the app fails schema validation).
-- Discards the per-payroll contribution breakdown recorded since V3; the
-- totals in tax_amount and deduction_amount are kept.

DELETE FROM system_settings WHERE setting_name IN (
    'statutory_deductions_enabled',
    'sss_employee_rate', 'sss_msc_minimum', 'sss_msc_maximum', 'sss_msc_step',
    'philhealth_premium_rate', 'philhealth_salary_floor', 'philhealth_salary_ceiling',
    'philhealth_employee_share',
    'pagibig_employee_rate', 'pagibig_low_salary_rate', 'pagibig_low_salary_threshold',
    'pagibig_max_fund_salary');

ALTER TABLE payroll
    DROP COLUMN sss_contribution,
    DROP COLUMN philhealth_contribution,
    DROP COLUMN pagibig_contribution,
    DROP COLUMN taxable_compensation;
