-- Read-only health check for employger_db. Changes nothing.
--
-- Run from the project folder (adjust the path and port for the server):
--   $mysql = "C:\Program Files\MariaDB 10.11\bin\mysql.exe"
--   & $mysql -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source scripts/verify-db.sql"

SELECT VERSION() AS server_version;

CHECK TABLE attendance, audit_logs, bonuses, deductions, employees, overtime,
            pay_cycles, payroll, payslips, system_settings, users EXTENDED;

SELECT (SELECT COUNT(*) FROM employees)       AS employees,
       (SELECT COUNT(*) FROM attendance)      AS attendance,
       (SELECT COUNT(*) FROM overtime)        AS overtime,
       (SELECT COUNT(*) FROM payroll)         AS payroll,
       (SELECT COUNT(*) FROM payslips)        AS payslips,
       (SELECT COUNT(*) FROM system_settings) AS settings,
       (SELECT COUNT(*) FROM users)           AS users;

-- V2 + V3 columns: expect 14.
SELECT COUNT(*) AS v2_v3_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'employger_db'
  AND ((TABLE_NAME = 'payroll' AND COLUMN_NAME IN (
          'base_pay', 'attendance_deduction', 'hourly_rate', 'factor_rate',
          'regular_hours_per_day', 'working_days_in_cycle', 'working_days_employed',
          'calculation_notes', 'calculated_at', 'sss_contribution',
          'philhealth_contribution', 'pagibig_contribution', 'taxable_compensation'))
    OR (TABLE_NAME = 'overtime' AND COLUMN_NAME = 'payroll_id'));

-- Expect fk_overtime_payroll SET NULL and fk_payroll_employee RESTRICT.
SELECT CONSTRAINT_NAME, DELETE_RULE
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = 'employger_db'
  AND CONSTRAINT_NAME IN ('fk_payroll_employee', 'fk_overtime_payroll');

SELECT payroll_id, status, gross_pay, net_pay FROM payroll ORDER BY payroll_id;
