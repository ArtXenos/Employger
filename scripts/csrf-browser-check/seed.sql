INSERT INTO employees (first_name, last_name, email, date_of_birth, date_hired, base_salary, employment_status, minimum_wage_earner, managerial, created_at, updated_at)
VALUES ('Browser', 'Check', 'browser.check@example.test', DATE '1990-01-01', DATE '2026-01-01', 25000.00, 'ACTIVE', FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (employee_id, username, password_hash, role, status, must_change_password, created_at)
VALUES (1, 'browser.admin', '$2a$10$4sTD/RIndrSxoCly1cL7N.Jeg1PGWnFJlmMe7XEjUT7u4vS/UgL96', 'ADMINISTRATOR', 'ACTIVE', FALSE, CURRENT_TIMESTAMP);
INSERT INTO employees (first_name, last_name, email, date_of_birth, date_hired, base_salary, employment_status, minimum_wage_earner, managerial, created_at, updated_at)
VALUES ('Gina', 'Manager', 'browser.gm@example.test', DATE '1985-01-01', DATE '2026-01-01', 60000.00, 'ACTIVE', FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (employee_id, username, password_hash, role, status, must_change_password, created_at)
VALUES (2, 'browser.gm', '$2a$10$4sTD/RIndrSxoCly1cL7N.Jeg1PGWnFJlmMe7XEjUT7u4vS/UgL96', 'GENERAL_MANAGER', 'ACTIVE', FALSE, CURRENT_TIMESTAMP);
INSERT INTO system_settings (setting_name, setting_value, updated_at) VALUES ('factor_rate', '261', CURRENT_TIMESTAMP);
INSERT INTO system_settings (setting_name, setting_value, updated_at) VALUES ('regular_hours_per_day', '8', CURRENT_TIMESTAMP);
INSERT INTO system_settings (setting_name, setting_value, updated_at) VALUES ('default_overtime_rate', '1.25', CURRENT_TIMESTAMP);
