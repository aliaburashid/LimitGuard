-- Fictional Citi panel dataset v1. Monetary amounts are in Bahraini dinars.
CREATE TEMP TABLE seed_groups (name text PRIMARY KEY, approved numeric(19,2) NOT NULL);
INSERT INTO seed_groups VALUES
    ('Alphabet', 6000000),
    ('Amazon', 5500000),
    ('Microsoft', 5000000),
    ('Volkswagen Group', 4600000),
    ('Berkshire Hathaway', 5000000),
    ('The Walt Disney Company', 4300000),
    ('PepsiCo', 4400000),
    ('Gulf Investment Group (Citi Demo)', 2000000);

CREATE TEMP TABLE seed_companies (
    name text PRIMARY KEY, group_name text REFERENCES seed_groups(name), status text NOT NULL,
    approved numeric(19,2), used numeric(19,2), reserved numeric(19,2), profile text
);
INSERT INTO seed_companies VALUES
    ('Google LLC', 'Alphabet', 'ACTIVE', 4200000, 1200000, 350000, 'A'),
    ('XXVI Holdings Inc.', 'Alphabet', 'ACTIVE', 1800000, 450000, 150000, 'B'),
    ('Alphabet Capital US LLC', 'Alphabet', 'ACTIVE', 1400000, 250000, 150000, 'B'),
    ('Amazon Web Services, Inc.', 'Amazon', 'ACTIVE', 3100000, 1800000, 600000, 'A'),
    ('Amazon.com Services LLC', 'Amazon', 'ACTIVE', 1900000, 700000, 300000, 'B'),
    ('Amazon Data Services, Inc.', 'Amazon', 'ACTIVE', 1600000, 550000, 200000, 'A'),
    ('Amazon.com Sales, Inc.', 'Amazon', 'ACTIVE', 1500000, 400000, 150000, 'B'),
    ('LinkedIn Corporation', 'Microsoft', 'ACTIVE', 2800000, 1000000, 350000, 'A'),
    ('Activision Blizzard, Inc.', 'Microsoft', 'ACTIVE', 2200000, 800000, 250000, 'B'),
    ('Microsoft Ireland Operations Limited', 'Microsoft', 'ACTIVE', 1800000, 450000, 150000, 'A'),
    ('AUDI AG', 'Volkswagen Group', 'ACTIVE', 2600000, 1550000, 500000, 'B'),
    ('Dr. Ing. h.c. F. Porsche AG', 'Volkswagen Group', 'ACTIVE', 2200000, 1050000, 400000, 'C'),
    ('Škoda Auto a.s.', 'Volkswagen Group', 'ACTIVE', 1700000, 650000, 250000, 'B'),
    ('BNSF Railway Company', 'Berkshire Hathaway', 'ACTIVE', 3400000, 1650000, 500000, 'B'),
    ('Berkshire Hathaway Energy Company', 'Berkshire Hathaway', 'ACTIVE', 2400000, 800000, 250000, 'C'),
    ('McLane Company, Inc.', 'Berkshire Hathaway', 'ACTIVE', 1800000, 400000, 200000, 'B'),
    ('ABC, Inc.', 'The Walt Disney Company', 'ACTIVE', 2200000, 1000000, 300000, 'B'),
    ('ESPN Enterprises, Inc.', 'The Walt Disney Company', 'ACTIVE', 2000000, 800000, 350000, 'A'),
    ('Disney Consumer Products, Inc.', 'The Walt Disney Company', 'ACTIVE', 1500000, 500000, 250000, 'B'),
    ('Pepsi-Cola Metropolitan Bottling Company, Inc.', 'PepsiCo', 'ACTIVE', 2600000, 1050000, 450000, 'A'),
    ('Sabra Dipping Company, LLC', 'PepsiCo', 'ACTIVE', 1300000, 450000, 200000, 'C'),
    ('Quaker Manufacturing, LLC', 'PepsiCo', 'ACTIVE', 1800000, 650000, 250000, 'B'),
    ('Falcon Freight Services (Citi Demo)', 'Gulf Investment Group (Citi Demo)', 'ACTIVE', 1000000, 300000, 0, 'C'),
    ('Bahrain Gateway Warehousing (Citi Demo)', 'Gulf Investment Group (Citi Demo)', 'ACTIVE', 1000000, 225000, 0, 'B'),
    ('Gulf Meridian Logistics W.L.L. (Citi Demo)', 'Gulf Investment Group (Citi Demo)', 'ACTIVE', 750000, 0, 675000, 'A'),
    ('Apple Inc.', NULL, 'ACTIVE', 6200000, 1450000, 450000, NULL),
    ('Meta Platforms, Inc.', NULL, 'ACTIVE', 5000000, 2650000, 650000, NULL),
    ('Broadcom Inc.', NULL, 'ACTIVE', 4200000, 2300000, 600000, NULL),
    ('Tesla, Inc.', NULL, 'ACTIVE', 3400000, 2250000, 800000, NULL),
    ('Walmart Inc.', NULL, 'ACTIVE', 5800000, 1850000, 550000, NULL),
    ('Visa Inc.', NULL, 'ACTIVE', 3800000, 1100000, 400000, NULL),
    ('Cisco Systems, Inc.', NULL, 'ACTIVE', 2800000, 700000, 250000, NULL),
    ('Salesforce, Inc.', NULL, 'ACTIVE', 3200000, 1400000, 450000, NULL),
    ('Adobe Inc.', NULL, 'ACTIVE', 2200000, 950000, 250000, NULL),
    ('Accenture plc', NULL, 'ACTIVE', 4500000, 1650000, 450000, NULL),
    ('SAP SE', NULL, 'ACTIVE', 3600000, 1300000, 500000, NULL),
    ('Spotify Technology S.A.', NULL, 'ACTIVE', 1900000, 980000, 420000, NULL),
    ('ServiceNow, Inc.', NULL, 'ACTIVE', 3000000, 1550000, 400000, NULL),
    ('Airbnb, Inc.', NULL, 'ACTIVE', 2400000, 720000, 280000, NULL),
    ('Snowflake Inc.', NULL, 'ACTIVE', 2700000, 1800000, 550000, NULL),
    ('Harbor Quay Equipment W.L.L. (fictional)', NULL, 'FROZEN', 900000, 150000, 0, NULL),
    ('North Quay Services W.L.L. (fictional)', NULL, 'CLOSED', NULL, NULL, NULL, NULL),
    ('Orchid Quay Trading W.L.L. (fictional)', NULL, 'ACTIVE', NULL, NULL, NULL, NULL),
    ('Dell Technologies Inc.', NULL, 'ACTIVE', 3100000, 1480000, 320000, NULL),
    ('Intuit Inc.', NULL, 'ACTIVE', 2300000, 860000, 290000, NULL),
    ('CrowdStrike Holdings, Inc.', NULL, 'ACTIVE', 1800000, 720000, 240000, NULL);

CREATE TEMP TABLE seed_profiles (
    code text PRIMARY KEY, roa numeric, margin numeric, turnover numeric,
    debt_to_capital numeric, coverage numeric, debt_to_ebitda numeric
);
INSERT INTO seed_profiles VALUES
    ('A', '12.50', '18.00', '2.10', '25.00', '8.50', '1.40'),
    ('B', '8.00', '11.00', '1.80', '40.00', '5.50', '2.60'),
    ('C', '-3.50', '-6.00', '0.60', '85.00', '0.70', '9.50');
