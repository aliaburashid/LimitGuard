CREATE TEMP TABLE reset_options (repeat_mode boolean, expected_token text);
INSERT INTO reset_options VALUES (:'repeat_mode'::boolean, :'expected_token');
CREATE TEMP TABLE reset_scope (relation_name text, row_id bigint, PRIMARY KEY (relation_name, row_id));
CREATE TEMP TABLE reset_snapshots (institution_id bigint, facility_id bigint, reporting_date date, PRIMARY KEY (institution_id, facility_id, reporting_date));

CREATE FUNCTION pg_temp.require(condition boolean, failure text) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    IF condition IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'STOP: %', failure;
    END IF;
END;
$$;

SELECT pg_temp.require(
    (SELECT count(*) = 4 FROM public.users u
     WHERE u.financial_institution_id = 5 AND u.status = 'ACTIVE'
       AND ((u.id = 15 AND lower(u.email) = 'aliaburashid@hotmail.com' AND u.role = 'RELATIONSHIP_MANAGER')
         OR (u.id = 58 AND lower(u.email) = 'sara.alhaddad@example.com' AND u.role = 'RELATIONSHIP_MANAGER')
         OR (u.id IN (23,59) AND u.role = 'RISK_OFFICER')))
    AND (SELECT name = 'Citi' FROM public.financial_institutions WHERE id = 5),
    'The approved Citi institution or presenter identities changed.'
);

-- Select only the approved global rows, or exact names from a prior versioned seed.
INSERT INTO reset_scope
SELECT 'counterparties', c.id FROM public.counterparties c
WHERE CASE WHEN (SELECT repeat_mode FROM reset_options)
      THEN EXISTS (SELECT 1 FROM seed_companies s WHERE s.name = c.name)
      ELSE c.id IN (1,2) END
UNION ALL
SELECT 'corporate_groups', g.id FROM public.corporate_groups g
WHERE CASE WHEN (SELECT repeat_mode FROM reset_options)
      THEN EXISTS (SELECT 1 FROM seed_groups s WHERE s.name = g.name)
      ELSE g.id = 2 END
UNION ALL
SELECT 'credit_limits', f.id FROM public.credit_limits f
WHERE f.financial_institution_id = 5 AND
      (NOT (SELECT repeat_mode FROM reset_options) OR EXISTS (
          SELECT 1 FROM seed_companies s JOIN public.counterparties c USING (name)
          WHERE c.id = f.counterparty_id AND s.approved IS NOT NULL))
UNION ALL
SELECT 'group_credit_limits', l.id FROM public.group_credit_limits l
WHERE l.financial_institution_id = 5 AND
      (NOT (SELECT repeat_mode FROM reset_options) OR EXISTS (
          SELECT 1 FROM seed_groups s JOIN public.corporate_groups g USING (name)
          WHERE g.id = l.corporate_group_id));

INSERT INTO reset_scope
SELECT 'credit_requests', r.id FROM public.credit_requests r
WHERE r.credit_limit_id IN (SELECT row_id FROM reset_scope WHERE relation_name = 'credit_limits')
UNION ALL
SELECT 'corporate_group_memberships', m.id FROM public.corporate_group_memberships m
WHERE m.corporate_group_id IN (SELECT row_id FROM reset_scope WHERE relation_name = 'corporate_groups')
UNION ALL
SELECT 'counterparty_financial_profiles', p.id FROM public.counterparty_financial_profiles p
WHERE p.counterparty_id IN (SELECT row_id FROM reset_scope WHERE relation_name = 'counterparties');

INSERT INTO reset_scope
SELECT 'approval_decisions', d.id FROM public.approval_decisions d
WHERE d.credit_request_id IN (SELECT row_id FROM reset_scope WHERE relation_name = 'credit_requests');

INSERT INTO reset_scope
SELECT 'audit_logs', a.id
FROM public.audit_logs a
JOIN reset_scope target ON a.entity_id = target.row_id
 AND a.entity_type = CASE target.relation_name
     WHEN 'credit_limits' THEN 'CREDIT_LIMIT'
     WHEN 'credit_requests' THEN 'CREDIT_REQUEST'
     WHEN 'group_credit_limits' THEN 'GROUP_CREDIT_LIMIT'
     WHEN 'corporate_groups' THEN 'CORPORATE_GROUP'
     WHEN 'corporate_group_memberships' THEN 'CORPORATE_GROUP_MEMBERSHIP'
     WHEN 'counterparties' THEN 'COUNTERPARTY'
     WHEN 'counterparty_financial_profiles' THEN 'COUNTERPARTY_FINANCIAL_PROFILE'
 END
WHERE a.financial_institution_id = 5 OR a.financial_institution_id IS NULL;

INSERT INTO reset_snapshots
SELECT institution_id, facility_id, reporting_date FROM public.credit_exposure_snapshots
WHERE institution_id = 5 AND
      (NOT (SELECT repeat_mode FROM reset_options)
       OR facility_id IN (SELECT row_id FROM reset_scope WHERE relation_name = 'credit_limits'));

-- Protect global records with any relationship outside this reset.
SELECT pg_temp.require(NOT EXISTS (
    SELECT 1 FROM public.credit_limits f
    WHERE f.counterparty_id IN (SELECT row_id FROM reset_scope WHERE relation_name = 'counterparties')
      AND f.id NOT IN (SELECT row_id FROM reset_scope WHERE relation_name = 'credit_limits')
    UNION ALL
    SELECT 1 FROM public.corporate_group_memberships m
    WHERE m.counterparty_id IN (SELECT row_id FROM reset_scope WHERE relation_name = 'counterparties')
      AND m.id NOT IN (SELECT row_id FROM reset_scope WHERE relation_name = 'corporate_group_memberships')
    UNION ALL
    SELECT 1 FROM public.corporate_group_memberships m
    WHERE m.id IN (SELECT row_id FROM reset_scope WHERE relation_name = 'corporate_group_memberships')
      AND m.counterparty_id NOT IN (SELECT row_id FROM reset_scope WHERE relation_name = 'counterparties')
    UNION ALL
    SELECT 1 FROM public.group_credit_limits l
    WHERE l.corporate_group_id IN (SELECT row_id FROM reset_scope WHERE relation_name = 'corporate_groups')
      AND l.id NOT IN (SELECT row_id FROM reset_scope WHERE relation_name = 'group_credit_limits')
), 'A candidate group or company now has an outside relationship.');

\if :repeat_mode
SELECT pg_temp.require(
    (SELECT count(*) = 46 FROM reset_scope WHERE relation_name = 'counterparties') AND
    (SELECT count(*) = 8 FROM reset_scope WHERE relation_name = 'corporate_groups') AND
    (SELECT count(*) = 25 FROM reset_scope WHERE relation_name = 'corporate_group_memberships') AND
    (SELECT count(*) = 8 FROM reset_scope WHERE relation_name = 'group_credit_limits') AND
    (SELECT count(*) = 44 FROM reset_scope WHERE relation_name = 'credit_limits') AND
    NOT EXISTS (
        SELECT 1 FROM reset_scope s WHERE s.relation_name = 'credit_limits'
          AND NOT EXISTS (
              SELECT 1 FROM public.credit_requests r
              WHERE r.credit_limit_id = s.row_id AND r.seed_key LIKE 'citi-panel-v1-%'
          )
    ), 'Could not identify the complete v1 seeded portfolio. Re-inventory before resetting.'
);
\else
CREATE TEMP TABLE approved_ids AS
SELECT name AS relation_name, unnest(ids)::bigint AS row_id
FROM (VALUES
    ('credit_limits', ARRAY[1,2,33,34,35,36,37,38,39,40,77,78,79,80]),
    ('credit_requests', ARRAY(SELECT generate_series(165,200)) || ARRAY(SELECT generate_series(399,408))),
    ('approval_decisions', ARRAY(SELECT generate_series(23,35)) || ARRAY(SELECT generate_series(113,119))),
    ('group_credit_limits', ARRAY[1,2]),
    ('corporate_groups', ARRAY[2]),
    ('corporate_group_memberships', ARRAY[4]),
    ('counterparties', ARRAY[1,2]),
    ('counterparty_financial_profiles', ARRAY[1]),
    ('audit_logs', ARRAY(SELECT generate_series(6,17)) || ARRAY[21,22,45] || ARRAY(SELECT generate_series(138,178)))
) AS approved(name, ids);

SELECT pg_temp.require(
    NOT EXISTS (TABLE reset_scope EXCEPT TABLE approved_ids) AND
    NOT EXISTS (TABLE approved_ids EXCEPT TABLE reset_scope),
    'The Citi deletion IDs or counts changed. Request a fresh approval.'
);
CREATE TEMP TABLE approved_snapshot_keys AS
SELECT 5::bigint AS institution_id, row_id AS facility_id, DATE '2026-11-07' AS reporting_date
FROM approved_ids WHERE relation_name = 'credit_limits';

CREATE TEMP TABLE snapshot_key_differences AS
SELECT 'ADDED' AS difference, added.*
FROM (TABLE reset_snapshots EXCEPT TABLE approved_snapshot_keys) added
UNION ALL
SELECT 'REMOVED', removed.*
FROM (TABLE approved_snapshot_keys EXCEPT TABLE reset_snapshots) removed;

SELECT difference, institution_id, facility_id, reporting_date
FROM snapshot_key_differences ORDER BY reporting_date, facility_id, difference;

SELECT pg_temp.require(NOT EXISTS (TABLE snapshot_key_differences),
    'The approved Citi snapshot keys changed. Request a fresh approval.'
);
SELECT pg_temp.require(
    (SELECT array_agg(id ORDER BY id) = ARRAY(
        SELECT generate_series(3,32) UNION ALL SELECT generate_series(41,76)
    )::bigint[] FROM public.credit_limits WHERE financial_institution_id <> 5)
    AND (SELECT array_agg(id ORDER BY id) = ARRAY[5,6,7,8]::bigint[]
         FROM public.corporate_group_memberships WHERE corporate_group_id = 3),
    'The approved other-institution or shared Gulf dependencies changed.'
);
\endif

SELECT relation_name, count(*) AS rows, array_agg(row_id ORDER BY row_id) AS ids
FROM reset_scope GROUP BY relation_name ORDER BY relation_name;
SELECT * FROM reset_snapshots ORDER BY facility_id, reporting_date;
