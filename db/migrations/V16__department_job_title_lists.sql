-- V16: Department and Job Title choice lists. Requested by the project
-- manager (item F) on 2026-10-28: dropdowns instead of free text, job
-- titles depend on the department, and HR and administrators add new
-- entries with "+ Add new...".
--
-- Two new tables, departments and job_titles (each job title belongs to
-- one department), and two new foreign keys on employees. The values
-- already typed on employees become the first entries:
--   * spaces around a name are dropped, and names that differ only in
--     upper/lower case become one entry ("Finance" and "finance").
--     Real typos ("Finanace") stay separate entries: after applying, rename
--     or retire them on the Employees page (Manage lists) and move the
--     employees to the right one.
--   * a job title without a department is put under a department called
--     "Unassigned" (created only if needed), because every job title now
--     needs one.
--
-- The old text columns employees.department and employees.job_title are
-- NOT dropped. The app stops reading and writing them, so they keep the
-- values from before V16 as a copy until the new lists are confirmed; a
-- later migration can drop them. Payroll keeps its own department
-- snapshot (payroll.department, V8), so past payroll is unchanged.
--
-- To see beforehand what the lists will contain (read-only):
--   SELECT TRIM(department) AS department, TRIM(job_title) AS job_title, COUNT(*) AS employees
--   FROM employees GROUP BY TRIM(department), TRIM(job_title) ORDER BY 1, 2;
--
-- Apply AFTER a backup, as root, BEFORE starting the new app version (it
-- validates the schema at startup), after V13, V14 and V15:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V16__department_job_title_lists.sql"
-- Undo: V16__rollback.sql

CREATE TABLE departments (
    department_id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'FALSE = retired: kept by employees who have it, not offered for new choices',
    created_at DATETIME NOT NULL,
    PRIMARY KEY (department_id),
    UNIQUE KEY uq_department_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_titles (
    job_title_id INT NOT NULL AUTO_INCREMENT,
    department_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'FALSE = retired: kept by employees who have it, not offered for new choices',
    created_at DATETIME NOT NULL,
    PRIMARY KEY (job_title_id),
    UNIQUE KEY uq_job_title_department_name (department_id, name),
    -- A department with job titles can't be deleted: retire it instead.
    CONSTRAINT fk_job_title_department FOREIGN KEY (department_id)
        REFERENCES departments (department_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE employees
    ADD COLUMN department_id INT NULL AFTER department,
    ADD COLUMN job_title_id INT NULL AFTER job_title,
    ADD KEY fk_employee_department (department_id),
    ADD KEY fk_employee_job_title (job_title_id),
    -- An entry in use can't be deleted: retire it instead.
    ADD CONSTRAINT fk_employee_department FOREIGN KEY (department_id)
        REFERENCES departments (department_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT fk_employee_job_title FOREIGN KEY (job_title_id)
        REFERENCES job_titles (job_title_id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- ---------- the first entries, from the values already typed ----------

-- The collation ignores case, so "Finance" and "finance" group together.
INSERT INTO departments (name, active, created_at)
SELECT MIN(TRIM(department)), TRUE, NOW()
FROM employees
WHERE department IS NOT NULL AND TRIM(department) <> ''
GROUP BY TRIM(department);

INSERT INTO departments (name, active, created_at)
SELECT 'Unassigned', TRUE, NOW()
FROM DUAL
WHERE EXISTS (SELECT 1 FROM employees
              WHERE (department IS NULL OR TRIM(department) = '')
                AND job_title IS NOT NULL AND TRIM(job_title) <> '')
  AND NOT EXISTS (SELECT 1 FROM departments WHERE name = 'Unassigned');

UPDATE employees e
JOIN departments d ON d.name = TRIM(e.department)
SET e.department_id = d.department_id
WHERE e.department IS NOT NULL AND TRIM(e.department) <> '';

UPDATE employees e
JOIN departments d ON d.name = 'Unassigned'
SET e.department_id = d.department_id
WHERE e.department_id IS NULL
  AND e.job_title IS NOT NULL AND TRIM(e.job_title) <> '';

INSERT INTO job_titles (department_id, name, active, created_at)
SELECT department_id, MIN(TRIM(job_title)), TRUE, NOW()
FROM employees
WHERE department_id IS NOT NULL AND job_title IS NOT NULL AND TRIM(job_title) <> ''
GROUP BY department_id, TRIM(job_title);

UPDATE employees e
JOIN job_titles j ON j.department_id = e.department_id AND j.name = TRIM(e.job_title)
SET e.job_title_id = j.job_title_id
WHERE e.job_title IS NOT NULL AND TRIM(e.job_title) <> '';

-- Check: every employee with a typed department or job title now has the
-- entry (both counts should be 0).
SELECT
    SUM(department IS NOT NULL AND TRIM(department) <> '' AND department_id IS NULL) AS departments_missed,
    SUM(job_title IS NOT NULL AND TRIM(job_title) <> '' AND job_title_id IS NULL) AS job_titles_missed
FROM employees;
