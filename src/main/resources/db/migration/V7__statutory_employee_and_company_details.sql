-- V7: optional employee statutory identifiers and a single employer profile.
-- Existing employees and historical payroll snapshots are not modified.
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS kra_pin varchar(11),
    ADD COLUMN IF NOT EXISTS nssf_number varchar(50),
    ADD COLUMN IF NOT EXISTS sha_number varchar(50);

CREATE UNIQUE INDEX IF NOT EXISTS uq_employees_kra_pin
    ON employees (upper(kra_pin))
    WHERE kra_pin IS NOT NULL AND btrim(kra_pin) <> '';
CREATE UNIQUE INDEX IF NOT EXISTS uq_employees_nssf_number
    ON employees (nssf_number)
    WHERE nssf_number IS NOT NULL AND btrim(nssf_number) <> '';
CREATE UNIQUE INDEX IF NOT EXISTS uq_employees_sha_number
    ON employees (sha_number)
    WHERE sha_number IS NOT NULL AND btrim(sha_number) <> '';

CREATE TABLE IF NOT EXISTS company_profile (
    id smallint PRIMARY KEY CHECK (id = 1),
    company_name varchar(150) NOT NULL,
    kra_pin varchar(11),
    nssf_employer_number varchar(50),
    sha_employer_number varchar(50),
    address varchar(300),
    phone_number varchar(30),
    email varchar(255),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamp(6),
    updated_at timestamp(6)
);
INSERT INTO company_profile (id, company_name, version)
VALUES (1, 'EMS Workforce', 0)
ON CONFLICT (id) DO NOTHING;
