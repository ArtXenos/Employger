-- V12: corrections to PAID payroll. Approved by the project owner on
-- 2026-10-25.
--
-- PAID payroll is never changed. A mistake found later is recorded here as
-- an adjustment and settled by a later payroll of the same employee: the
-- payroll whose pay cycle contains apply_date (as with earnings).
--   UNDERPAYMENT  the employee was paid too little; the amount is added.
--   OVERPAYMENT   the employee was paid too much; the amount is deducted,
--                 only with the employee's written consent (Labor Code
--                 Art. 113) and never beyond that payroll's net pay.
-- Either way it is taxable pay (or a reduction of it) in the cycle it is
-- settled; December annualization settles the year's tax. The later
-- payroll still needs the General Manager's sign-off before it is paid.
--
-- Additive only: one new table and one NULL-able payroll column (NULL on
-- payroll calculated before V12). No existing value changes. Apply AFTER a
-- backup, as root, BEFORE starting the new app version (it validates the
-- schema at startup):
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V12__payroll_adjustments.sql"
-- Undo: V12__rollback.sql

CREATE TABLE payroll_adjustments (
    adjustment_id INT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(20) NOT NULL COMMENT 'UNDERPAYMENT (added) or OVERPAYMENT (recovered)',
    employee_id INT NOT NULL,
    corrected_payroll_id INT NOT NULL COMMENT 'The PAID payroll that was wrong',
    amount DECIMAL(12,2) NOT NULL COMMENT 'Always positive; kind gives the direction',
    reason VARCHAR(255) NOT NULL,
    apply_date DATE NOT NULL COMMENT 'Settled by the payroll whose cycle contains this date',
    employee_consent BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'OVERPAYMENT only: the employee agreed in writing to the deduction',
    payroll_id INT NULL COMMENT 'The payroll that settles it; set by the server',
    created_by INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (adjustment_id),
    KEY idx_adjustment_employee_date (employee_id, apply_date),
    KEY fk_adjustment_corrected (corrected_payroll_id),
    KEY fk_adjustment_payroll (payroll_id),
    KEY fk_adjustment_creator (created_by),
    CONSTRAINT chk_adjustment_kind CHECK (kind IN ('UNDERPAYMENT', 'OVERPAYMENT')),
    CONSTRAINT chk_adjustment_amount CHECK (amount > 0),
    CONSTRAINT fk_adjustment_employee FOREIGN KEY (employee_id)
        REFERENCES employees (employee_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_adjustment_corrected FOREIGN KEY (corrected_payroll_id)
        REFERENCES payroll (payroll_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_adjustment_payroll FOREIGN KEY (payroll_id)
        REFERENCES payroll (payroll_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_adjustment_creator FOREIGN KEY (created_by)
        REFERENCES users (user_id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE payroll
    ADD COLUMN adjustment_amount DECIMAL(12,2) NULL
        COMMENT 'Corrections of earlier PAID payroll settled here: + underpayment, - overpayment; NULL = before V12'
        AFTER bonus_amount;
