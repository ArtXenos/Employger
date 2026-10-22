-- Rollback for V6. Run only if V6 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V6__rollback.sql"
--
-- WARNING: this discards the holiday calendar, prior-employer data, the
-- managerial flag and government numbers, the payroll links of earnings,
-- and the payroll breakdown columns. Earnings rows in `bonuses` are kept.
-- Deploy the application version from before V6 at the same time.

DELETE FROM system_settings WHERE setting_name IN (
    'rate_regular_holiday', 'rate_regular_holiday_rest_day',
    'rate_special_day', 'rate_special_day_rest_day',
    'rate_double_holiday', 'rate_double_holiday_rest_day',
    'rate_double_holiday_unworked', 'rate_rest_day',
    'overtime_premium_factor', 'benefits_exemption_cap',
    'de_minimis_rice_monthly', 'de_minimis_clothing_annual',
    'de_minimis_medical_semester', 'de_minimis_leave_days');

ALTER TABLE payroll
    DROP COLUMN holiday_premium_pay,
    DROP COLUMN night_differential_pay,
    DROP COLUMN hazard_pay,
    DROP COLUMN thirteenth_month_pay,
    DROP COLUMN other_benefits_pay,
    DROP COLUMN de_minimis_pay,
    DROP COLUMN taxable_allowance_pay,
    DROP COLUMN de_minimis_exempt,
    DROP COLUMN exempt_benefits,
    DROP COLUMN mwe_exempt_pay,
    DROP COLUMN tax_refund,
    DROP COLUMN annualized_tax_due;

ALTER TABLE bonuses
    DROP FOREIGN KEY fk_bonuses_payroll,
    DROP KEY fk_bonuses_payroll,
    DROP COLUMN payroll_id;

ALTER TABLE employees
    DROP COLUMN tin,
    DROP COLUMN pagibig_number,
    DROP COLUMN philhealth_number,
    DROP COLUMN sss_number,
    DROP COLUMN managerial;

DROP TABLE prior_employer_compensation;
DROP TABLE holidays;
