-- Citi-only relationships extracted from the original pre-reset archive.
-- LG24 Security Test is excluded. Only archived approved limits are imported.
INSERT INTO seed_groups VALUES
    ('Lufthansa Group', 6000000.00),
    ('Tata Group', 2000000.00);
INSERT INTO seed_companies (name, group_name, status, approved, profile) VALUES
    ('Siemens AG', NULL, 'ACTIVE', 2500000.00, NULL),
    ('Costco Wholesale Corporation', NULL, 'ACTIVE', 400000.00, NULL),
    ('Tata Consultancy Services Limited', 'Tata Group', 'ACTIVE', 1000000.00, 'B'),
    ('Lufthansa Technik AG', 'Lufthansa Group', 'ACTIVE', 2000000.00, 'LEGACY_BTC'),
    ('HSBC Holdings plc', NULL, 'ACTIVE', 2200000.00, NULL),
    ('FedEx Corporation', NULL, 'CLOSED', 900000.00, NULL),
    ('Tata Steel Limited', 'Tata Group', 'ACTIVE', 1000000.00, 'C'),
    ('The Home Depot, Inc.', NULL, 'CLOSED', 550000.00, NULL),
    ('Tata Consumer Products Limited', 'Tata Group', 'ACTIVE', 750000.00, 'A'),
    ('ABB Ltd', NULL, 'ACTIVE', 800000.00, NULL),
    ('The Boeing Company', NULL, 'FROZEN', 1600000.00, NULL),
    ('Titan Company Limited', 'Tata Group', 'FROZEN', 1250000.00, 'LEGACY_PEARL'),
    ('Lufthansa Cargo AG', 'Lufthansa Group', 'ACTIVE', 3200000.00, NULL);
INSERT INTO seed_profiles VALUES
    ('LEGACY_BTC', 8.5, 12, 1.5, 45, 4.2, 2.8),
    ('LEGACY_PEARL', 5.2, 7.5, 1.3, 55, 3, 4.2);
