-- V2: employee photos are stored in the database instead of on the server's disk,
-- which is wiped on every deploy.

CREATE TABLE IF NOT EXISTS employee_photos (
    employee_id   bigint PRIMARY KEY REFERENCES employees (id) ON DELETE CASCADE,
    content_type  varchar(50)  NOT NULL,
    data          bytea        NOT NULL,
    updated_at    timestamp(6)
);

-- The old values were file names on a disk that no longer holds those files.
-- Clear them so pages show initials until a photo is uploaded again.
UPDATE employees SET profile_photo = NULL WHERE profile_photo IS NOT NULL;
