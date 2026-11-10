-- Capture hashes for verification only. No authentication records are exported.
CREATE TEMP TABLE scope_hashes (relation_name text PRIMARY KEY, digest text NOT NULL);
CREATE TEMP TABLE protected_rows (relation_name text, row_key text, digest text, PRIMARY KEY (relation_name, row_key));

CREATE FUNCTION pg_temp.reset_tables()
RETURNS text[] LANGUAGE sql AS $$
    SELECT ARRAY['approval_decisions','audit_logs','corporate_group_memberships','corporate_groups',
        'counterparties','counterparty_financial_profiles','credit_limits','credit_requests','credit_exposure_snapshots',
        'email_verification_codes','financial_institutions','group_credit_limits','password_reset_codes','user_tokens','users'];
$$;

DO $$
DECLARE
    relation text;
    key_expression text;
    in_scope text;
BEGIN
    FOREACH relation IN ARRAY pg_temp.reset_tables() LOOP
        IF relation = 'credit_exposure_snapshots' THEN
            key_expression := 'ROW(t.institution_id,t.facility_id,t.reporting_date)::text';
            in_scope := 'EXISTS (SELECT 1 FROM pg_temp.reset_snapshots s WHERE (s.institution_id,s.facility_id,s.reporting_date) = (t.institution_id,t.facility_id,t.reporting_date))';
        ELSE
            key_expression := 't.id::text';
            in_scope := format('EXISTS (SELECT 1 FROM pg_temp.reset_scope s WHERE s.relation_name = %L AND s.row_id = t.id)', relation);
        END IF;
        EXECUTE format('INSERT INTO pg_temp.scope_hashes SELECT %L, md5(coalesce(string_agg(to_jsonb(t)::text, E''\n'' ORDER BY %s), '''')) FROM public.%I t WHERE %s',
            relation, key_expression, relation, in_scope);
        EXECUTE format('INSERT INTO pg_temp.protected_rows SELECT %L, %s, md5(to_jsonb(t)::text) FROM public.%I t WHERE NOT (%s)',
            relation, key_expression, relation, in_scope);
    END LOOP;
END;
$$;

SELECT md5(string_agg(relation_name || ':' || digest, ',' ORDER BY relation_name)) AS reviewed_token
FROM scope_hashes \gset
\echo 'Scope token:' :reviewed_token

SELECT length(:'expected_token') > 0 AS compare_token \gset
\if :compare_token
SELECT pg_temp.require(:'reviewed_token' = :'expected_token',
    'A reviewed row changed. Obtain a fresh inventory and approval for this exact scope.');
\endif

CREATE FUNCTION pg_temp.verify_protected() RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    relation text;
    key_expression text;
    changed boolean;
BEGIN
    FOREACH relation IN ARRAY pg_temp.reset_tables() LOOP
        key_expression := CASE WHEN relation = 'credit_exposure_snapshots'
            THEN 'ROW(t.institution_id,t.facility_id,t.reporting_date)::text' ELSE 't.id::text' END;
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM pg_temp.protected_rows before
            LEFT JOIN public.%I t ON before.row_key = %s
            WHERE before.relation_name = %L AND before.digest IS DISTINCT FROM md5(to_jsonb(t)::text))',
            relation, key_expression, relation) INTO changed;
        PERFORM pg_temp.require(NOT changed, 'Protected rows changed in ' || relation);
    END LOOP;
END;
$$;
