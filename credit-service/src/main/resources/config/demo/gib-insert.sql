INSERT INTO public.counterparties (name, status, created_at, updated_at)
SELECT name, status, seed.local_now, seed.local_now
FROM gib_companies CROSS JOIN gib_seed_context seed ORDER BY code;

INSERT INTO public.corporate_groups (name, created_at, updated_at)
SELECT 'North Reef Holdings', local_now, local_now FROM gib_seed_context;

INSERT INTO public.corporate_group_memberships (corporate_group_id, counterparty_id)
SELECT g.id, c.id FROM gib_companies s
JOIN public.counterparties c USING (name)
CROSS JOIN public.corporate_groups g
WHERE s.grouped AND g.name = 'North Reef Holdings';

INSERT INTO public.group_credit_limits (financial_institution_id, corporate_group_id, amount, created_at, updated_at)
SELECT seed.bank_id, g.id, 2400000, seed.local_now, seed.local_now
FROM public.corporate_groups g CROSS JOIN gib_seed_context seed WHERE g.name = 'North Reef Holdings';

INSERT INTO public.counterparty_financial_profiles
    (counterparty_id, financial_year_end, return_on_assets, profit_margin, fixed_asset_turnover,
     debt_to_total_capital_percent, interest_coverage, total_debt_to_ebitda, created_at, updated_at)
SELECT c.id, DATE '2025-12-31', s.roa, s.margin, s.turnover, s.capital_debt, s.coverage, s.ebitda_debt,
       seed.local_now, seed.local_now
FROM gib_companies s JOIN public.counterparties c USING (name) CROSS JOIN gib_seed_context seed;

-- Build live balances from request status. Terminal expired and cancelled requests hold no headroom.
INSERT INTO public.credit_limits
    (financial_institution_id, counterparty_id, limit_amount, used_amount, reserved_amount, created_at, updated_at)
SELECT seed.bank_id, c.id, s.capacity,
       COALESCE((SELECT sum(amount) FROM gib_requests r WHERE r.company_code = s.code AND r.status = 'USED'), 0),
       COALESCE((SELECT sum(amount) FROM gib_requests r WHERE r.company_code = s.code AND r.status = 'RESERVED'), 0),
       seed.local_now, seed.local_now
FROM gib_companies s JOIN public.counterparties c USING (name) CROSS JOIN gib_seed_context seed;

CREATE TEMP TABLE gib_request_history ON COMMIT DROP AS
WITH requests AS (
    SELECT r.*, f.id AS facility_id,
           CASE r.rm_number WHEN 1 THEN seed.rm1 ELSE seed.rm2 END AS manager_id,
           CASE r.ro_number WHEN 1 THEN seed.ro1 WHEN 2 THEN seed.ro2 END AS officer_id,
           CASE WHEN r.days_ago = 0 THEN seed.local_now - INTERVAL '3 hours'
                ELSE (seed.reporting_date - r.days_ago) + TIME '09:00' END AS submitted_at
    FROM gib_requests r JOIN gib_companies s ON s.code = r.company_code
    JOIN public.counterparties c USING (name)
    CROSS JOIN gib_seed_context seed
    JOIN public.credit_limits f ON f.counterparty_id = c.id AND f.financial_institution_id = seed.bank_id
), reservation_times AS (
    SELECT requests.*, CASE
        WHEN status IN ('USED','RESERVED','EXPIRED','CANCELLED') THEN
            submitted_at + CASE WHEN officer_id IS NULL THEN INTERVAL '0' ELSE INTERVAL '30 minutes' END
        END AS reserved_at
    FROM requests
)
SELECT reservation_times.*, reserved_at + INTERVAL '24 hours' AS expires_at,
       CASE status WHEN 'USED' THEN reserved_at + INTERVAL '2 hours'
                   WHEN 'EXPIRED' THEN reserved_at + INTERVAL '24 hours'
                   WHEN 'CANCELLED' THEN reserved_at + INTERVAL '6 hours'
                   WHEN 'REJECTED' THEN submitted_at + INTERVAL '30 minutes'
                   ELSE submitted_at END AS last_changed_at
FROM reservation_times;

INSERT INTO public.credit_requests
    (seed_key, credit_limit_id, requester_id, amount, status, created_at, updated_at, expires_at)
SELECT seed_key, facility_id, manager_id, amount, status, submitted_at, last_changed_at, expires_at
FROM gib_request_history ORDER BY seed_key;

INSERT INTO public.approval_decisions (credit_request_id, decided_by_id, decision, reason, created_at)
SELECT r.id, h.officer_id, CASE WHEN h.status = 'REJECTED' THEN 'REJECTED' ELSE 'APPROVED' END,
       h.reason, h.submitted_at + INTERVAL '30 minutes'
FROM gib_request_history h JOIN public.credit_requests r USING (seed_key)
WHERE h.officer_id IS NOT NULL;

-- Reconstruct synthetic history from the same request timeline used for current balances.
INSERT INTO public.credit_exposure_snapshots
    (institution_id, facility_id, captured_at, reporting_date, limit_amount, used_amount, reserved_amount)
SELECT seed.bank_id, f.id, clock.captured_at, clock.reporting_date, f.limit_amount,
       COALESCE(sum(h.amount) FILTER (WHERE h.status = 'USED' AND h.last_changed_at <= clock.local_capture), 0),
       COALESCE(sum(h.amount) FILTER (WHERE h.reserved_at <= clock.local_capture
           AND h.expires_at > clock.local_capture
           AND (h.status = 'RESERVED' OR h.last_changed_at > clock.local_capture)), 0)
FROM gib_seed_context seed
JOIN public.credit_limits f ON f.financial_institution_id = seed.bank_id
JOIN public.counterparties c ON c.id = f.counterparty_id
JOIN gib_companies s ON s.name = c.name
CROSS JOIN (VALUES (14), (10), (8), (7), (3), (1)) AS days(days_ago)
CROSS JOIN LATERAL (
    SELECT seed.reporting_date - days.days_ago AS reporting_date,
           ((seed.reporting_date - days.days_ago) + TIME '20:00') AT TIME ZONE 'UTC' AS captured_at,
           (seed.reporting_date - days.days_ago) + TIME '23:00' AS local_capture
) clock
LEFT JOIN gib_request_history h ON h.facility_id = f.id
GROUP BY seed.bank_id, f.id, clock.captured_at, clock.reporting_date, clock.local_capture, f.limit_amount;
