-- The locations.country_code column was created as CHAR(2), but the Location entity maps it
-- to VARCHAR(2) (@Column(length = 2) on a String field). Hibernate's schema validation
-- (ddl-auto: validate) fails hard on this mismatch: "found [bpchar], but expecting [varchar]".
-- Align the column type with what the entity actually expects.
ALTER TABLE locations
ALTER COLUMN country_code TYPE VARCHAR(2);
