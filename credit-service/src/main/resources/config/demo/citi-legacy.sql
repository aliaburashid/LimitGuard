-- Citi-only relationships extracted from the original pre-reset archive.
-- LG24 Security Test is excluded. Only archived approved limits are imported.
INSERT INTO seed_groups VALUES
    ('Bahrain Holding Group', 6000000.00),
    ('Gulf Investment Group', 2000000.00);
INSERT INTO seed_companies (name, group_name, status, approved, profile) VALUES
    ('Al Dana Petrochemicals', NULL, 'ACTIVE', 2500000.00, NULL),
    ('Bahrain Fresh Foods', NULL, 'ACTIVE', 400000.00, NULL),
    ('Bahrain Gateway Warehousing', 'Gulf Investment Group', 'ACTIVE', 1000000.00, 'B'),
    ('Bahrain Trading Company', 'Bahrain Holding Group', 'ACTIVE', 2000000.00, 'LEGACY_BTC'),
    ('Crescent Healthcare Group', NULL, 'ACTIVE', 2200000.00, NULL),
    ('Crescent Maritime Holdings', NULL, 'CLOSED', 900000.00, NULL),
    ('Falcon Freight Services', 'Gulf Investment Group', 'ACTIVE', 1000000.00, 'C'),
    ('Golden Dhow Trading', NULL, 'CLOSED', 550000.00, NULL),
    ('Gulf Meridian Logistics W.L.L.', 'Gulf Investment Group', 'ACTIVE', 750000.00, 'A'),
    ('Gulf Nexus Technology', NULL, 'ACTIVE', 800000.00, NULL),
    ('Horizon Energy Services', NULL, 'FROZEN', 1600000.00, NULL),
    ('Pearl Coast Shipping Co.', 'Gulf Investment Group', 'FROZEN', 1250000.00, 'LEGACY_PEARL'),
    ('Seef Landmark Properties', NULL, 'ACTIVE', 3200000.00, NULL);
INSERT INTO seed_profiles VALUES
    ('LEGACY_BTC', 8.5, 12, 1.5, 45, 4.2, 2.8),
    ('LEGACY_PEARL', 5.2, 7.5, 1.3, 55, 3, 4.2);
