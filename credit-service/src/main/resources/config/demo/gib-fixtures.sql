-- The companies, balances and financial ratios below are fictional demonstration data.
CREATE TEMP TABLE gib_companies (
    code text PRIMARY KEY, name text UNIQUE NOT NULL, grouped boolean NOT NULL,
    status text NOT NULL, capacity numeric NOT NULL, roa numeric NOT NULL, margin numeric NOT NULL,
    turnover numeric NOT NULL, capital_debt numeric NOT NULL, coverage numeric NOT NULL, ebitda_debt numeric NOT NULL
) ON COMMIT DROP;
INSERT INTO gib_companies VALUES
    ('dilmun', 'Dilmun Medical Supplies W.L.L.', false, 'ACTIVE', 3000000, 10.8, 15.2, 1.92, 28, 8.2, 1.55),
    ('saffron', 'Saffron Coast Food Distribution W.L.L.', false, 'ACTIVE', 400000, 5.6, 7.8, 2.05, 52, 3.4, 3.7),
    ('riffa', 'Riffa Industrial Rentals W.L.L.', false, 'FROZEN', 500000, -2.8, -5.4, 0.67, 79, 0.82, 8.2),
    ('cold-chain', 'North Reef Cold Chain W.L.L.', true, 'ACTIVE', 1250000, 9.3, 12.7, 1.6, 34, 6.4, 2),
    ('packaging', 'North Reef Packaging W.L.L.', true, 'ACTIVE', 900000, 3.9, 5.5, 1.24, 57, 2.8, 4.45),
    ('marine', 'North Reef Marine Services W.L.L.', true, 'ACTIVE', 700000, -1.5, -2.6, 0.88, 69, 1.25, 6.4);

CREATE TEMP TABLE gib_requests (
    seed_key text PRIMARY KEY, company_code text NOT NULL, rm_number integer NOT NULL,
    amount numeric NOT NULL, status text NOT NULL, days_ago integer NOT NULL,
    ro_number integer, reason text
) ON COMMIT DROP;
INSERT INTO gib_requests VALUES
    ('gib-demo-v1-dilmun-approval', 'dilmun', 1, 550000, 'PENDING_APPROVAL', 0, NULL, NULL),
    ('gib-demo-v1-dilmun-review', 'dilmun', 2, 600000, 'PENDING_APPROVAL', 0, NULL, NULL),
    ('gib-demo-v1-dilmun-used', 'dilmun', 1, 575000, 'USED', 12, 1,
        'Approved based on strong interest coverage, moderate leverage and available facility headroom.'),
    ('gib-demo-v1-saffron-used', 'saffron', 2, 125000, 'USED', 11, NULL, NULL),
    ('gib-demo-v1-saffron-reserved', 'saffron', 1, 125000, 'RESERVED', 0, NULL, NULL),
    ('gib-demo-v1-saffron-expired', 'saffron', 2, 75000, 'EXPIRED', 8, NULL, NULL),
    ('gib-demo-v1-saffron-cancelled', 'saffron', 1, 50000, 'CANCELLED', 4, NULL, NULL),
    ('gib-demo-v1-riffa-used', 'riffa', 2, 180000, 'USED', 15, NULL, NULL),
    ('gib-demo-v1-cold-chain-used', 'cold-chain', 1, 360000, 'USED', 9, NULL, NULL),
    ('gib-demo-v1-cold-chain-reserved', 'cold-chain', 1, 200000, 'RESERVED', 0, NULL, NULL),
    ('gib-demo-v1-packaging-used', 'packaging', 2, 250000, 'USED', 5, NULL, NULL),
    ('gib-demo-v1-marine-used', 'marine', 2, 80000, 'USED', 6, NULL, NULL),
    ('gib-demo-v1-marine-rejected', 'marine', 2, 525000, 'REJECTED', 2, 2,
        'Declined due to operating losses, elevated debt relative to EBITDA and limited interest coverage.');
