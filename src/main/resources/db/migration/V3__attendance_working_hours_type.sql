-- V3: attendance.working_hours holds a Java Duration as nanoseconds, stored as
-- numeric(21,0), which is what Hibernate expects.
--
-- V1 created the column as an interval on new databases. Convert it there.
-- A database that Hibernate created already has the right type, so nothing happens.

DO $$
DECLARE
    column_type text;
BEGIN
    SELECT data_type INTO column_type
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'attendance'
      AND column_name = 'working_hours';

    IF column_type = 'interval' THEN
        ALTER TABLE attendance
            ALTER COLUMN working_hours TYPE numeric(21, 0)
            USING round(extract(epoch FROM working_hours) * 1000000000);
    END IF;
END $$;
