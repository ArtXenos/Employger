-- V1: the original Em-Ployger tables, as they stood before V2 (taken from
-- the 2026-10-22 backup made just before V2 and V3 were applied).
--
-- Only for a NEW, empty database. It holds no employees, users or payroll,
-- just the 16 default settings the app shipped with. The tables are created
-- with plain CREATE TABLE, so run against an existing employger_db it stops
-- at the first table and changes nothing.
--
-- New install: scripts\setup-db.ps1 creates the database and applies V1 to
-- V11 in order. By hand (as root, after CREATE DATABASE):
--   mysql.exe -u root -p -P 3307 employger_db < db\migrations\V1__base_schema.sql
-- Then V2, V3, ... V11 the same way. Undo: drop the new database.

CREATE TABLE `employees` (
  `employee_id` int(11) NOT NULL AUTO_INCREMENT,
  `first_name` varchar(100) NOT NULL,
  `last_name` varchar(100) NOT NULL,
  `date_of_birth` date NOT NULL,
  `email` varchar(150) NOT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `department` varchar(100) DEFAULT NULL,
  `profile_image` longblob DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `base_salary` decimal(12,2) NOT NULL DEFAULT 0.00,
  `postal_code` varchar(20) DEFAULT NULL,
  `designation` varchar(100) DEFAULT NULL,
  `employment_status` enum('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `date_hired` date NOT NULL,
  `job_title` varchar(100) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`employee_id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `user_id` int(11) NOT NULL AUTO_INCREMENT,
  `employee_id` int(11) DEFAULT NULL,
  `username` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('HR_MANAGER','GENERAL_MANAGER','ADMINISTRATOR','EMPLOYEE') NOT NULL,
  `status` enum('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_login` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username` (`username`),
  KEY `fk_users_employee` (`employee_id`),
  CONSTRAINT `fk_users_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`employee_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `attendance` (
  `attendance_id` int(11) NOT NULL AUTO_INCREMENT,
  `employee_id` int(11) NOT NULL,
  `attendance_date` date NOT NULL,
  `time_in` time DEFAULT NULL,
  `time_out` time DEFAULT NULL,
  `regular_hours` decimal(5,2) NOT NULL DEFAULT 0.00,
  `status` enum('PRESENT','ABSENT','LATE','LEAVE') NOT NULL DEFAULT 'PRESENT',
  `remarks` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`attendance_id`),
  UNIQUE KEY `uq_attendance_employee_date` (`employee_id`,`attendance_date`),
  CONSTRAINT `fk_attendance_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`employee_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `audit_logs` (
  `audit_id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) DEFAULT NULL,
  `action` varchar(100) NOT NULL,
  `description` text NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`audit_id`),
  KEY `fk_audit_user` (`user_id`),
  CONSTRAINT `fk_audit_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `bonuses` (
  `bonus_id` int(11) NOT NULL AUTO_INCREMENT,
  `employee_id` int(11) NOT NULL,
  `bonus_type` varchar(100) NOT NULL,
  `amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `description` varchar(255) DEFAULT NULL,
  `date_given` date NOT NULL,
  `created_by` int(11) DEFAULT NULL,
  PRIMARY KEY (`bonus_id`),
  KEY `fk_bonuses_employee` (`employee_id`),
  KEY `fk_bonuses_creator` (`created_by`),
  CONSTRAINT `fk_bonuses_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_bonuses_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`employee_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `deductions` (
  `deduction_id` int(11) NOT NULL AUTO_INCREMENT,
  `employee_id` int(11) NOT NULL,
  `deduction_type` varchar(100) NOT NULL,
  `amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `reason` varchar(255) DEFAULT NULL,
  `deduction_date` date NOT NULL,
  `created_by` int(11) DEFAULT NULL,
  PRIMARY KEY (`deduction_id`),
  KEY `fk_deductions_employee` (`employee_id`),
  KEY `fk_deductions_creator` (`created_by`),
  CONSTRAINT `fk_deductions_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_deductions_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`employee_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `overtime` (
  `overtime_id` int(11) NOT NULL AUTO_INCREMENT,
  `employee_id` int(11) NOT NULL,
  `attendance_id` int(11) DEFAULT NULL,
  `overtime_date` date NOT NULL,
  `hours` decimal(5,2) NOT NULL DEFAULT 0.00,
  `overtime_rate` decimal(10,2) NOT NULL DEFAULT 0.00,
  `overtime_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `status` enum('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `approved_by` int(11) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`overtime_id`),
  KEY `fk_overtime_employee` (`employee_id`),
  KEY `fk_overtime_attendance` (`attendance_id`),
  KEY `fk_overtime_approver` (`approved_by`),
  CONSTRAINT `fk_overtime_approver` FOREIGN KEY (`approved_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_overtime_attendance` FOREIGN KEY (`attendance_id`) REFERENCES `attendance` (`attendance_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_overtime_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`employee_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pay_cycles` (
  `pay_cycle_id` int(11) NOT NULL AUTO_INCREMENT,
  `cycle_name` varchar(100) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `pay_date` date NOT NULL,
  `status` enum('OPEN','PROCESSING','PROCESSED','CLOSED') NOT NULL DEFAULT 'OPEN',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `cycle_type` enum('WEEKLY','BI_WEEKLY','SEMI_MONTHLY','MONTHLY') NOT NULL DEFAULT 'SEMI_MONTHLY',
  PRIMARY KEY (`pay_cycle_id`),
  CONSTRAINT `chk_pay_cycle_dates` CHECK (`end_date` >= `start_date`),
  CONSTRAINT `chk_pay_date` CHECK (`pay_date` >= `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payroll` (
  `payroll_id` int(11) NOT NULL AUTO_INCREMENT,
  `employee_id` int(11) NOT NULL,
  `pay_cycle_id` int(11) NOT NULL,
  `regular_pay` decimal(12,2) NOT NULL DEFAULT 0.00,
  `overtime_pay` decimal(12,2) NOT NULL DEFAULT 0.00,
  `bonus_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `gross_pay` decimal(12,2) NOT NULL DEFAULT 0.00,
  `tax_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `deduction_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `net_pay` decimal(12,2) NOT NULL DEFAULT 0.00,
  `status` enum('DRAFT','PROCESSED','PAID') NOT NULL DEFAULT 'DRAFT',
  `processed_at` timestamp NULL DEFAULT NULL,
  `processed_by` int(11) DEFAULT NULL,
  `payment_date` datetime DEFAULT NULL,
  `payment_method` varchar(50) DEFAULT NULL,
  `payment_reference` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`payroll_id`),
  UNIQUE KEY `uq_payroll_employee_cycle` (`employee_id`,`pay_cycle_id`),
  KEY `fk_payroll_cycle` (`pay_cycle_id`),
  KEY `fk_payroll_processor` (`processed_by`),
  CONSTRAINT `fk_payroll_cycle` FOREIGN KEY (`pay_cycle_id`) REFERENCES `pay_cycles` (`pay_cycle_id`) ON UPDATE CASCADE,
  CONSTRAINT `fk_payroll_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`employee_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_payroll_processor` FOREIGN KEY (`processed_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payslips` (
  `payslip_id` int(11) NOT NULL AUTO_INCREMENT,
  `payroll_id` int(11) NOT NULL,
  `generated_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `file_path` varchar(255) DEFAULT NULL,
  `generated_by` int(11) DEFAULT NULL,
  PRIMARY KEY (`payslip_id`),
  UNIQUE KEY `payroll_id` (`payroll_id`),
  KEY `fk_payslips_generator` (`generated_by`),
  CONSTRAINT `fk_payslips_generator` FOREIGN KEY (`generated_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_payslips_payroll` FOREIGN KEY (`payroll_id`) REFERENCES `payroll` (`payroll_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `system_settings` (
  `setting_id` int(11) NOT NULL AUTO_INCREMENT,
  `setting_name` varchar(100) NOT NULL,
  `setting_value` varchar(255) NOT NULL,
  `updated_by` int(11) DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`setting_id`),
  UNIQUE KEY `setting_name` (`setting_name`),
  KEY `fk_settings_user` (`updated_by`),
  CONSTRAINT `fk_settings_user` FOREIGN KEY (`updated_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The original default settings. V2 onwards add the rest.
INSERT INTO system_settings (setting_name, setting_value) VALUES
    ('application_name', 'Em-Ployger'),
    ('currency_code', 'PHP'),
    ('currency_symbol', '₱'),
    ('company_name', 'Em-Ployger Company'),
    ('default_overtime_rate', '1.25'),
    ('late_deduction_amount', '0.00'),
    ('absent_deduction_amount', '0.00'),
    ('factor_rate', '261'),
    ('standard_start_time', '08:00'),
    ('standard_end_time', '17:00'),
    ('unpaid_break_minutes', '60'),
    ('late_grace_period_minutes', '0'),
    ('regular_hours_per_day', '8'),
    ('night_differential_rate', '0.10'),
    ('night_differential_end', '06:00'),
    ('night_differential_start', '22:00');
