-- Rollback for V16. Run only if V16 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V16__rollback.sql"
--
-- First copies each employee's current department and job title back into
-- the text columns, so changes made since V16 aren't lost (an employee
-- placed under "Unassigned" gets that as the department text). Then drops
-- the choice lists. Deploy the application version from before V16 at the
-- same time, or the app will fail schema validation at startup.

UPDATE employees e
LEFT JOIN departments d ON d.department_id = e.department_id
LEFT JOIN job_titles j ON j.job_title_id = e.job_title_id
SET e.department = d.name,
    e.job_title = j.name;

ALTER TABLE employees
    DROP FOREIGN KEY fk_employee_department,
    DROP FOREIGN KEY fk_employee_job_title,
    DROP KEY fk_employee_department,
    DROP KEY fk_employee_job_title,
    DROP COLUMN department_id,
    DROP COLUMN job_title_id;

DROP TABLE job_titles;

DROP TABLE departments;
