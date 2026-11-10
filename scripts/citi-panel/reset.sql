\set ON_ERROR_STOP on
\pset pager off
\if :approve_write
BEGIN;
\else
BEGIN ISOLATION LEVEL REPEATABLE READ;
\endif
SET LOCAL search_path = pg_temp, public;
SET LOCAL TIME ZONE 'UTC';
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '45s';

-- A rehearsal accepts only the private socket test instance. Live mode accepts only the approved local target.
SELECT (
    NOT pg_is_in_recovery()
    AND CASE WHEN :'rehearsal'::boolean
        THEN current_database() = 'limitguard_citi_rehearsal'
             AND inet_server_addr() IS NULL AND current_setting('port') = '55492'
        ELSE current_database() = 'limitguard' AND inet_server_addr() = '127.0.0.1'::inet
             AND inet_server_port() = 5432
    END
) AS correct_target \gset
\if :correct_target
\else
\echo 'STOP: database identity does not match the approved local target.'
SELECT 1/0;
\endif

\if :approve_write
-- No normal app, scheduler or other user session may be active at the maintenance boundary.
SELECT NOT EXISTS (
    SELECT 1 FROM pg_stat_activity
    WHERE datname = current_database() AND pid <> pg_backend_pid() AND backend_type = 'client backend'
) AS no_other_clients \gset
\if :no_other_clients
\else
\echo 'STOP: close Spring Boot and all other sessions to this database, then recheck scope.'
SELECT 1/0;
\endif
SELECT pg_advisory_xact_lock(724211, 1);
LOCK TABLE public.approval_decisions, public.audit_logs, public.corporate_group_memberships,
    public.corporate_groups, public.counterparties, public.counterparty_financial_profiles,
    public.credit_limits, public.credit_requests, public.credit_exposure_snapshots,
    public.email_verification_codes, public.financial_institutions, public.group_credit_limits,
    public.password_reset_codes, public.user_tokens, public.users IN SHARE ROW EXCLUSIVE MODE;
\endif

\ir fixtures.sql
\ir scope.sql
\ir fingerprints.sql
\ir schema-guard.sql

\if :approve_write
\ir seed.sql
COMMIT;
\echo 'Citi panel reset committed. Protected rows were checked in the same transaction.'
\else
ROLLBACK;
\echo 'Read-only review finished. No persistent changes were made.'
\endif
