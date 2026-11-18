-- This script only inserts missing rows and completes missing profile fields.
INSERT INTO public.corporate_groups (name, created_at, updated_at)
SELECT s.name, LOCALTIMESTAMP, LOCALTIMESTAMP FROM seed_groups s
WHERE NOT EXISTS (
    SELECT 1 FROM public.corporate_groups g WHERE lower(trim(g.name)) = lower(s.name)
) ORDER BY s.name;

INSERT INTO public.counterparties (name, status, created_at, updated_at)
SELECT s.name, s.status, LOCALTIMESTAMP, LOCALTIMESTAMP FROM seed_companies s
WHERE NOT EXISTS (
    SELECT 1 FROM public.counterparties c WHERE lower(trim(c.name)) = lower(s.name)
) ORDER BY s.name;

CREATE TEMP TABLE citi_seed_catalog ON COMMIT DROP AS
SELECT c.id AS company_id, g.id AS group_id, s.name,
       coalesce(s.approved, CASE s.name
           WHEN 'Delta Air Lines, Inc.' THEN 10000
           WHEN 'United Parcel Service, Inc.' THEN 50000 END) AS capacity,
       coalesce(p.roa, n.roa) AS roa, coalesce(p.margin, n.margin) AS margin,
       coalesce(p.turnover, n.turnover) AS turnover,
       coalesce(p.debt_to_capital, n.debt_to_capital) AS debt_to_capital,
       coalesce(p.coverage, n.coverage) AS coverage,
       coalesce(p.debt_to_ebitda, n.debt_to_ebitda) AS debt_to_ebitda
FROM seed_companies s
JOIN public.counterparties c ON lower(trim(c.name)) = lower(s.name)
LEFT JOIN public.corporate_groups g ON lower(trim(g.name)) = lower(s.group_name)
LEFT JOIN seed_profiles p ON p.code = s.profile
LEFT JOIN seed_named_profiles n ON n.name = s.name;

INSERT INTO public.corporate_group_memberships (corporate_group_id, counterparty_id)
SELECT s.group_id, s.company_id FROM citi_seed_catalog s
WHERE s.group_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM public.corporate_group_memberships m WHERE m.counterparty_id = s.company_id
);

INSERT INTO public.credit_limits
    (financial_institution_id, counterparty_id, limit_amount, used_amount, reserved_amount, created_at, updated_at)
SELECT 5, s.company_id, s.capacity, 0, 0, LOCALTIMESTAMP, LOCALTIMESTAMP FROM citi_seed_catalog s
WHERE NOT EXISTS (
    SELECT 1 FROM public.credit_limits l WHERE l.financial_institution_id = 5 AND l.counterparty_id = s.company_id
);

INSERT INTO public.group_credit_limits
    (financial_institution_id, corporate_group_id, amount, created_at, updated_at)
SELECT 5, g.id, s.approved, LOCALTIMESTAMP, LOCALTIMESTAMP
FROM seed_groups s JOIN public.corporate_groups g ON lower(trim(g.name)) = lower(s.name)
WHERE NOT EXISTS (
    SELECT 1 FROM public.group_credit_limits l WHERE l.financial_institution_id = 5 AND l.corporate_group_id = g.id
);

INSERT INTO public.counterparty_financial_profiles
    (counterparty_id, financial_year_end, return_on_assets, profit_margin, fixed_asset_turnover,
     debt_to_total_capital_percent, interest_coverage, total_debt_to_ebitda, created_at, updated_at)
SELECT company_id, DATE '2025-12-31', roa, margin, turnover, debt_to_capital, coverage,
       debt_to_ebitda, LOCALTIMESTAMP, LOCALTIMESTAMP
FROM citi_seed_catalog s
WHERE NOT EXISTS (
    SELECT 1 FROM public.counterparty_financial_profiles p WHERE p.counterparty_id = s.company_id
);

UPDATE public.counterparty_financial_profiles p
SET return_on_assets = coalesce(p.return_on_assets, s.roa),
    profit_margin = coalesce(p.profit_margin, s.margin),
    fixed_asset_turnover = coalesce(p.fixed_asset_turnover, s.turnover),
    debt_to_total_capital_percent = coalesce(p.debt_to_total_capital_percent, s.debt_to_capital),
    interest_coverage = coalesce(p.interest_coverage, s.coverage),
    total_debt_to_ebitda = coalesce(p.total_debt_to_ebitda, s.debt_to_ebitda),
    financial_year_end = coalesce(p.financial_year_end, DATE '2025-12-31'),
    updated_at = LOCALTIMESTAMP
FROM citi_seed_catalog s
WHERE p.counterparty_id = s.company_id AND num_nonnulls(p.return_on_assets, p.profit_margin,
    p.fixed_asset_turnover, p.debt_to_total_capital_percent, p.interest_coverage,
    p.total_debt_to_ebitda, p.financial_year_end) < 7;
