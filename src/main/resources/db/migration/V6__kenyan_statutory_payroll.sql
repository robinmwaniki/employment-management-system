-- Kenyan statutory payroll calculation snapshot. Defaults preserve historical payroll amounts.
ALTER TABLE payroll
    ADD COLUMN IF NOT EXISTS gross_pay numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS taxable_pay numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS nssf numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shif numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS housing_levy numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS employer_nssf numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS employer_housing_levy numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pension_contribution numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS personal_relief numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS insurance_relief numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS resident_taxpayer boolean NOT NULL DEFAULT true;

UPDATE payroll
SET gross_pay = COALESCE(basic_salary,0) + COALESCE(house_allowance,0)
    + COALESCE(transport_allowance,0) + COALESCE(bonus,0)
WHERE gross_pay = 0;

UPDATE payroll SET taxable_pay = gross_pay WHERE taxable_pay = 0;
