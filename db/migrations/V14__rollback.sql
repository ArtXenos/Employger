-- Rollback for V14. Run only if V14 must be undone, and only after a backup:
--   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V14__rollback.sql"
--
-- This deletes the rejection reasons and cancellation details. Cancelled
-- overtime or leave can't be represented without V14: the check below
-- says so, and each ALTER then fails as a whole (strict mode refuses to
-- truncate 'CANCELLED') and changes nothing. Decide what each should
-- become first (for example REJECTED) and update it by hand. Deploy the application version from
-- before V14 at the same time, or the app will fail schema validation.

SELECT IF(
    (SELECT COUNT(*) FROM overtime WHERE status = 'CANCELLED')
        + (SELECT COUNT(*) FROM leave_requests WHERE status = 'CANCELLED') > 0,
    'STOP: cancelled overtime or leave exists; convert it first',
    'OK: nothing is cancelled') AS check_before_rollback;

ALTER TABLE overtime
    DROP FOREIGN KEY fk_overtime_cancelled_by,
    DROP KEY fk_overtime_cancelled_by,
    DROP COLUMN decision_remarks,
    DROP COLUMN cancel_reason,
    DROP COLUMN cancelled_by,
    DROP COLUMN cancelled_at,
    MODIFY COLUMN status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING';

ALTER TABLE leave_requests
    DROP FOREIGN KEY fk_leave_cancelled_by,
    DROP KEY fk_leave_cancelled_by,
    DROP COLUMN decision_remarks,
    DROP COLUMN cancel_reason,
    DROP COLUMN cancelled_by,
    DROP COLUMN cancelled_at,
    MODIFY COLUMN status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING';
