-- V4: protect leave decisions against stale concurrent updates and ensure
-- an employee can only have one attendance row per calendar day.
ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM attendance
        GROUP BY employee_id, attendance_date
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot add attendance uniqueness: duplicate employee/date rows exist. Reconcile duplicates before retrying V4.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_attendance_employee_date
    ON attendance (employee_id, attendance_date);
