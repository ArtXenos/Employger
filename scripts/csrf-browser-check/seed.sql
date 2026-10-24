INSERT INTO employees (first_name, last_name, email, date_of_birth, date_hired, base_salary, employment_status, minimum_wage_earner, managerial, created_at, updated_at)
VALUES ('Browser', 'Check', 'browser.check@example.test', DATE '1990-01-01', DATE '2026-01-01', 25000.00, 'ACTIVE', FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO users (employee_id, username, password_hash, role, status, created_at)
VALUES (1, 'browser.admin', '$2a$10$4sTD/RIndrSxoCly1cL7N.Jeg1PGWnFJlmMe7XEjUT7u4vS/UgL96', 'ADMINISTRATOR', 'ACTIVE', CURRENT_TIMESTAMP);
