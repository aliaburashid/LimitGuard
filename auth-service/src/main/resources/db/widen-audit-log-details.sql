BEGIN;

SET LOCAL lock_timeout = '5s';

ALTER TABLE public.audit_logs
    ALTER COLUMN details TYPE TEXT;

COMMIT;
