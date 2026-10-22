-- Rollback for V5. Run only if V5 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V5__rollback.sql"
--
-- WARNING: this discards the employer shares recorded since V5 was applied.
-- Deploy the application version from before V5 at the same time, or the
-- app will fail schema validation at startup.

DELETE FROM system_settings WHERE setting_name IN (
    'sss_employer_rate',
    'sss_ec_msc_threshold',
    'sss_ec_amount_low',
    'sss_ec_amount_high',
    'pagibig_employer_rate');

ALTER TABLE payroll
    DROP COLUMN sss_employer_contribution,
    DROP COLUMN sss_ec_contribution,
    DROP COLUMN philhealth_employer_contribution,
    DROP COLUMN pagibig_employer_contribution;
