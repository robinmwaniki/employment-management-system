-- V5: extend optimistic locking to mutable employee, attendance and payroll
-- records, and make the payroll natural key a mandatory database invariant.
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE attendance
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE payroll
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM payroll
        GROUP BY employee_id, payroll_month
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce one payroll per employee/month: duplicate payroll rows exist. Reconcile duplicates before retrying V5.';
    END IF;
END $$;

-- V1 may already have created this index for a clean legacy database.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payroll_employee_month
    ON payroll (employee_id, payroll_month);
