-- The caller holds the reviewed scope locks for the whole transaction.
DO $$
DECLARE
    relation text;
    removed integer;
BEGIN
    -- Logical children first, then the rows they reference.
    DELETE FROM public.credit_exposure_snapshots t
    USING pg_temp.reset_snapshots s
    WHERE (t.institution_id,t.facility_id,t.reporting_date) = (s.institution_id,s.facility_id,s.reporting_date);
    GET DIAGNOSTICS removed = ROW_COUNT;
    PERFORM pg_temp.require(removed = (SELECT count(*) FROM pg_temp.reset_snapshots), 'Snapshot delete count changed.');

    FOREACH relation IN ARRAY ARRAY['audit_logs','approval_decisions','credit_requests','credit_limits',
            'group_credit_limits','corporate_group_memberships','counterparty_financial_profiles',
            'corporate_groups','counterparties'] LOOP
        EXECUTE format('DELETE FROM public.%I t USING pg_temp.reset_scope s WHERE s.relation_name = %L AND t.id = s.row_id',
            relation, relation);
        GET DIAGNOSTICS removed = ROW_COUNT;
        PERFORM pg_temp.require(removed = (SELECT count(*) FROM pg_temp.reset_scope WHERE relation_name = relation),
            'Delete count changed in ' || relation);
    END LOOP;
END;
$$;

CREATE TEMP TABLE seed_clock AS
SELECT instant, instant AT TIME ZONE 'Asia/Bahrain' AS local_time,
       (instant AT TIME ZONE 'UTC')::date AS reporting_date
FROM (SELECT clock_timestamp() AS instant) AS capture;

INSERT INTO public.corporate_groups (name, created_at, updated_at)
SELECT name, local_time, local_time FROM seed_groups CROSS JOIN seed_clock ORDER BY name;

INSERT INTO public.counterparties (name, status, created_at, updated_at)
SELECT name, status, local_time, local_time FROM seed_companies CROSS JOIN seed_clock ORDER BY name;

INSERT INTO public.corporate_group_memberships (corporate_group_id, counterparty_id)
SELECT g.id, c.id FROM seed_companies s
JOIN public.counterparties c USING (name)
JOIN public.corporate_groups g ON g.name = s.group_name
ORDER BY g.id, c.id;

INSERT INTO public.group_credit_limits (amount, created_at, updated_at, corporate_group_id, financial_institution_id)
SELECT s.approved, local_time, local_time, g.id, 5
FROM seed_groups s JOIN public.corporate_groups g USING (name) CROSS JOIN seed_clock ORDER BY g.id;

INSERT INTO public.counterparty_financial_profiles
    (created_at, updated_at, financial_year_end, counterparty_id, return_on_assets, profit_margin,
     fixed_asset_turnover, debt_to_total_capital_percent, interest_coverage, total_debt_to_ebitda)
SELECT local_time, local_time, DATE '2025-12-31', c.id, p.roa, p.margin, p.turnover,
       p.debt_to_capital, p.coverage, p.debt_to_ebitda
FROM seed_companies s JOIN public.counterparties c USING (name)
JOIN seed_profiles p ON p.code = s.profile CROSS JOIN seed_clock ORDER BY c.id;

INSERT INTO public.credit_limits
    (created_at, updated_at, financial_institution_id, counterparty_id, limit_amount, used_amount, reserved_amount)
SELECT local_time, local_time, 5, c.id, s.approved, s.used, s.reserved
FROM seed_companies s JOIN public.counterparties c USING (name) CROSS JOIN seed_clock
WHERE s.approved IS NOT NULL ORDER BY c.id;

CREATE TEMP TABLE seeded_facilities AS
SELECT f.id, s.*, CASE
    WHEN s.name = 'Falcon Freight Services (Citi Demo)' THEN 15
    WHEN s.name = 'Bahrain Gateway Warehousing (Citi Demo)' THEN 58
    WHEN row_number() OVER (ORDER BY s.name) % 2 = 0 THEN 15 ELSE 58
END AS owner_id
FROM seed_companies s JOIN public.counterparties c USING (name)
JOIN public.credit_limits f ON f.counterparty_id = c.id AND f.financial_institution_id = 5;

-- Seed accounting tranches that could have been immediately reserved under the live rules.
-- The race fixture explicitly keeps the approved BD 450,000 and BD 225,000 setup reservations.
WITH tranches AS (
    SELECT f.id, f.owner_id, 'USED' AS status, n AS part,
           least(500000, f.used - 500000 * (n - 1)) AS amount,
           2 + mod(abs(hashtext(f.name)::bigint) + n, 5)::int AS days_before_seed
    FROM seeded_facilities f CROSS JOIN LATERAL generate_series(1, ceil(f.used / 500000)::int) n
    UNION ALL
    SELECT f.id, f.owner_id, 'RESERVED', n,
           least(500000, f.reserved - 500000 * (n - 1)), NULL
    FROM seeded_facilities f CROSS JOIN LATERAL generate_series(1, ceil(f.reserved / 500000)::int) n
    WHERE f.name <> 'Gulf Meridian Logistics W.L.L. (Citi Demo)'
    UNION ALL
    SELECT f.id, parts.owner_id, 'RESERVED', parts.part, parts.amount, NULL
    FROM seeded_facilities f CROSS JOIN (VALUES (15,1,450000),(58,2,225000)) parts(owner_id,part,amount)
    WHERE f.name = 'Gulf Meridian Logistics W.L.L. (Citi Demo)'
), timing AS (
    -- Synthetic used tranches start in the evening and settle before the 24-hour expiry.
    SELECT tranches.*, coalesce((reporting_date - days_before_seed) + TIME '19:00', local_time) AS opened_at
    FROM tranches CROSS JOIN seed_clock
)
INSERT INTO public.credit_requests
    (amount, created_at, expires_at, status, updated_at, credit_limit_id, requester_id, seed_key)
SELECT amount, opened_at, opened_at + interval '24 hours', status,
       CASE WHEN status = 'USED' THEN opened_at + interval '16 hours' ELSE opened_at END,
       id, owner_id, concat('citi-panel-v1-', id, '-', lower(status), '-', part)
FROM timing ORDER BY id, status, part;

WITH examples(name, owner_id, amount, status, label, created_offset, updated_offset, expiry_offset) AS (VALUES
    ('Visa Inc.',15,700000,'PENDING_APPROVAL','visa-inventory','-1 hour'::interval,'-1 hour'::interval,NULL::interval),
    ('SAP SE',58,650000,'PENDING_APPROVAL','sap-working-capital','-1 hour','-1 hour',NULL),
    ('LinkedIn Corporation',15,600000,'PENDING_APPROVAL','linkedin-receivables','-1 hour','-1 hour',NULL),
    ('Cisco Systems, Inc.',58,200000,'CANCELLED','cisco-released','-5 hours','-4 hours','19 hours'),
    ('Walmart Inc.',15,150000,'EXPIRED','walmart-expired','-48 hours','-24 hours','-24 hours'),
    ('Adobe Inc.',58,600000,'REJECTED','adobe-rejected','-4 hours','-3 hours',NULL)
)
INSERT INTO public.credit_requests
    (amount, created_at, expires_at, status, updated_at, credit_limit_id, requester_id, seed_key)
SELECT e.amount, local_time + e.created_offset, local_time + e.expiry_offset,
       e.status, local_time + e.updated_offset, f.id, e.owner_id, 'citi-panel-v1-' || e.label
FROM examples e JOIN seeded_facilities f USING (name) CROSS JOIN seed_clock ORDER BY e.label;

INSERT INTO public.approval_decisions (created_at, decision, reason, credit_request_id, decided_by_id)
SELECT updated_at, 'REJECTED', '[SYNTHETIC DEMO] Proposed funding did not meet the fictional review criteria.', id, 23
FROM public.credit_requests WHERE seed_key = 'citi-panel-v1-adobe-rejected';

-- Replay the fictional request ledger at six consecutive UTC day-end cutoffs.
-- A request starts reserved and leaves that state when used, cancelled or expired.
WITH cutoffs AS (
    SELECT reporting_date - day_offset AS reporting_date,
           ((reporting_date - day_offset) + TIME '23:59:59') AT TIME ZONE 'UTC' AS instant
    FROM seed_clock CROSS JOIN generate_series(1, 6) AS day_offset
)
INSERT INTO public.credit_exposure_snapshots
    (institution_id, facility_id, captured_at, reporting_date, limit_amount, used_amount, reserved_amount)
SELECT 5, f.id, cutoff.instant, cutoff.reporting_date, f.approved,
       coalesce(sum(r.amount) FILTER (
           WHERE r.status = 'USED' AND r.updated_at <= cutoff.instant AT TIME ZONE 'Asia/Bahrain'), 0),
       coalesce(sum(r.amount) FILTER (
           WHERE r.status IN ('USED','CANCELLED','EXPIRED')
             AND r.updated_at > cutoff.instant AT TIME ZONE 'Asia/Bahrain'
             AND r.expires_at > cutoff.instant AT TIME ZONE 'Asia/Bahrain'), 0)
FROM seeded_facilities f CROSS JOIN cutoffs cutoff
LEFT JOIN public.credit_requests r ON r.credit_limit_id = f.id
    AND r.created_at <= cutoff.instant AT TIME ZONE 'Asia/Bahrain'
GROUP BY f.id, f.approved, cutoff.instant, cutoff.reporting_date;

-- The capture and seed commit together, so a visible snapshot always reflects committed seed balances.
INSERT INTO public.credit_exposure_snapshots
    (institution_id, facility_id, captured_at, reporting_date, limit_amount, used_amount, reserved_amount)
SELECT 5, id, instant, reporting_date, approved, used, reserved
FROM seeded_facilities CROSS JOIN seed_clock;

SELECT pg_temp.require((SELECT count(*) = 46 FROM public.counterparties JOIN seed_companies USING (name))
    AND (SELECT count(*) = 8 FROM public.corporate_groups JOIN seed_groups USING (name))
    AND (SELECT count(*) = 25 FROM public.corporate_group_memberships m
         JOIN public.corporate_groups g ON g.id = m.corporate_group_id JOIN seed_groups s ON s.name = g.name)
    AND (SELECT count(*) = 44 AND sum(approved) = 113350000 AND sum(used) = 44535000
         AND sum(reserved) = 15075000 FROM seeded_facilities),
    'Seed entity counts or approved financial totals did not reconcile.');

SELECT pg_temp.require(NOT EXISTS (
    SELECT 1 FROM seeded_facilities f JOIN public.credit_limits actual USING (id)
    WHERE actual.used_amount <> f.used OR actual.reserved_amount <> f.reserved OR actual.limit_amount <> f.approved
      OR f.used + f.reserved > f.approved
      OR f.used <> (SELECT coalesce(sum(r.amount),0) FROM public.credit_requests r WHERE r.credit_limit_id=f.id AND r.status='USED')
      OR f.reserved <> (SELECT coalesce(sum(r.amount),0) FROM public.credit_requests r WHERE r.credit_limit_id=f.id AND r.status='RESERVED')
), 'Individual balances do not reconcile to the request ledger.');

WITH headroom AS (
    SELECT g.name, g.approved - sum(f.used + f.reserved) AS available
    FROM seed_groups g JOIN seeded_facilities f ON f.group_name=g.name GROUP BY g.name, g.approved
)
SELECT pg_temp.require(count(*) = 8 AND min(available) >= 0
    AND max(available) FILTER (WHERE name = 'Gulf Investment Group (Citi Demo)') = 800000,
    'Shared group balances or the BD 800,000 race headroom did not reconcile.')
FROM headroom;

SELECT pg_temp.require((SELECT count(*) = 2 FROM seeded_facilities
    WHERE (name, owner_id, approved - used - reserved) IN (
        ('Falcon Freight Services (Citi Demo)',15,700000),
        ('Bahrain Gateway Warehousing (Citi Demo)',58,775000))),
    'The two approved RM race facility setups do not match.');

SELECT pg_temp.require(NOT EXISTS (
    SELECT 1 FROM public.credit_requests r JOIN seeded_facilities f ON f.id=r.credit_limit_id CROSS JOIN seed_clock s
    WHERE (r.status='RESERVED' AND (r.amount > 500000 OR r.expires_at <> s.local_time + interval '24 hours'))
       OR (r.status='PENDING_APPROVAL' AND (r.amount <= 500000 OR r.expires_at IS NOT NULL))
       OR (r.status='USED' AND (r.amount > 500000 OR r.updated_at >= r.expires_at))
       OR (r.status='EXPIRED' AND r.updated_at < r.expires_at)
       OR (r.status='CANCELLED' AND r.updated_at >= r.expires_at)
       OR (r.status='REJECTED' AND NOT EXISTS (
          SELECT 1 FROM public.approval_decisions d WHERE d.credit_request_id=r.id
            AND d.decision='REJECTED' AND d.decided_by_id<>r.requester_id
       ))
), 'A seeded request does not match the expected lifecycle.');

-- Check each snapshot's capacity and reconcile the opening point to the live facility row.
SELECT pg_temp.require(NOT EXISTS (
    SELECT 1 FROM seeded_facilities f JOIN public.credit_limits actual USING (id)
    CROSS JOIN seed_clock c
    LEFT JOIN public.credit_exposure_snapshots s ON s.institution_id = 5 AND s.facility_id = f.id
    GROUP BY f.id, actual.limit_amount, actual.used_amount, actual.reserved_amount, c.reporting_date, c.instant
    HAVING count(s.facility_id) <> 7
        OR count(*) FILTER (WHERE s.captured_at = c.instant AND s.reporting_date = c.reporting_date
             AND (s.limit_amount, s.used_amount, s.reserved_amount)
                 = (actual.limit_amount, actual.used_amount, actual.reserved_amount)) <> 1
        OR bool_or(s.limit_amount <> actual.limit_amount OR s.used_amount < 0 OR s.reserved_amount < 0
                   OR s.used_amount + s.reserved_amount > s.limit_amount)
), 'The demo snapshots do not reconcile to opening facility balances.');

SELECT pg_temp.require(NOT EXISTS (
    SELECT 1 FROM public.credit_exposure_snapshots s JOIN seeded_facilities f ON f.id = s.facility_id
    JOIN seed_groups g ON g.name = f.group_name
    WHERE s.institution_id = 5
    GROUP BY g.name, g.approved, s.reporting_date
    HAVING sum(s.used_amount + s.reserved_amount) > g.approved
), 'A demo snapshot exceeds its shared group limit.');

SELECT pg_temp.verify_protected();
SELECT count(*) AS facilities, sum(approved) AS approved, sum(used) AS used, sum(reserved) AS reserved,
       sum(approved-used-reserved) AS individual_headroom
FROM seeded_facilities;
SELECT r.status, count(*) AS requests
FROM public.credit_requests r JOIN seeded_facilities f ON f.id=r.credit_limit_id
GROUP BY r.status ORDER BY r.status;
