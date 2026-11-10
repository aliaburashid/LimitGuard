-- Serialize first-time schema setup across application instances.
BEGIN;
SELECT pg_advisory_xact_lock(724211, 1);

CREATE TABLE IF NOT EXISTS credit_exposure_snapshots (
    institution_id BIGINT NOT NULL,
    facility_id BIGINT NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reporting_date DATE NOT NULL,
    limit_amount NUMERIC(19, 2) NOT NULL,
    used_amount NUMERIC(19, 2) NOT NULL,
    reserved_amount NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (institution_id, facility_id, reporting_date)
);

CREATE INDEX IF NOT EXISTS credit_exposure_snapshots_reporting_date_idx
    ON credit_exposure_snapshots (reporting_date);

COMMIT;
