package com.example.limitguard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Additive SEED_PLAN.md data. Disabled unless explicitly enabled for an approved seed. */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "limitguard.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final List<String> TABLES = List.of("approval_decisions", "audit_logs",
            "corporate_group_memberships", "corporate_groups", "counterparties", "counterparty_financial_profiles",
            "credit_limits", "credit_requests", "financial_institutions", "group_credit_limits", "user_tokens", "users");
    private static final Map<String, Integer> ADDITIONS = Map.of("corporate_group_memberships", 8,
            "corporate_groups", 3, "counterparties", 9, "counterparty_financial_profiles", 9,
            "credit_limits", 9, "credit_requests", 35, "group_credit_limits", 3, "approval_decisions", 1);

    record Company(String name, String group, String limit, String used, String reserved, String[] ratios) { }
    static final List<Company> COMPANIES = List.of(
            new Company("Gulf Aviation Services", "Gulf Aviation Holdings", "4000000", "200000", "0",
                    new String[]{"7.2", "12.4", "1.6", "38", "6.2", "2.1"}),
            new Company("Gulf Cargo Logistics", "Gulf Aviation Holdings", "4000000", "200000", "0",
                    new String[]{"6.4", "10.8", "1.9", "42", "5.4", "2.5"}),
            new Company("Gulf Aircraft Maintenance", "Gulf Aviation Holdings", "4000000", "200000", "0",
                    new String[]{"8.1", "15.2", "1.4", "32", "7.6", "1.8"}),
            new Company("Gulf Airport Technology", "Gulf Aviation Holdings", "4000000", "200000", "0",
                    new String[]{"9.3", "18.1", "2.2", "27", "9.1", "1.4"}),
            new Company("Pearl Construction", "Pearl Infrastructure Group", "1500000", "800000", "0",
                    new String[]{"4.7", "8.2", "1.1", "54", "3.4", "3.8"}),
            new Company("Pearl Engineering", "Pearl Infrastructure Group", "1600000", "800000", "0",
                    new String[]{"6.8", "11.6", "1.7", "45", "4.8", "2.9"}),
            new Company("Arabian Renewable Energy", "Arabian Energy Group", "1600000", "600000", "0",
                    new String[]{"5.8", "14.3", "0.9", "48", "4.2", "3.1"}),
            new Company("Arabian Industrial Utilities", "Arabian Energy Group", "1600000", "600000", "0",
                    new String[]{"6.1", "13.2", "1.2", "46", "4.7", "2.8"}),
            new Company("Bahrain Digital Services", null, "3000000", "250000", "750000",
                    new String[]{"10.4", "19.6", "2.6", "24", "10.2", "1.2"}));

    private final JdbcTemplate jdbc;
    private final DemoCreditRequestSeeder requests;

    public DemoDataSeeder(JdbcTemplate jdbc, DemoCreditRequestSeeder requests) {
        this.jdbc = jdbc;
        this.requests = requests;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        require("limitguard".equals(jdbc.queryForObject("SELECT current_catalog", String.class)),
                "Expected the existing limitguard database");
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        // Prevent data changes between prerequisite checks and preservation verification.
        // These locks belong only to this one-off seed, never to the business services.
        jdbc.execute("LOCK TABLE " + String.join(", ", TABLES) + " IN SHARE ROW EXCLUSIVE MODE");
        List<String> expectedKeys = DemoCreditRequestSeeder.approvedRequests(LocalDateTime.now()).stream()
                .map(DemoCreditRequestSeeder.RequestSeed::key).toList();
        List<String> existingKeys = jdbc.queryForList(
                "SELECT seed_key FROM credit_requests WHERE seed_key LIKE 'development-citi-%'", String.class);
        if (!existingKeys.isEmpty()) {
            require(existingKeys.size() == 35 && existingKeys.containsAll(expectedKeys),
                    "Partial seed or conflicting seed keys; no changes made");
            require(COMPANIES.stream().allMatch(c -> count("SELECT COUNT(*) FROM counterparties WHERE name = ?", c.name()) == 1),
                    "Previously seeded companies are missing; no automatic repair is allowed");
            log.info("Approved seed already exists; no records, balances, statuses or expiries changed");
            return;
        }
        validatePrerequisites();
        Map<String, Map<Long, String>> before = snapshot();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Map<String, Long> groups = new LinkedHashMap<>();
        for (String name : List.of("Gulf Aviation Holdings", "Pearl Infrastructure Group", "Arabian Energy Group")) {
            Long id = jdbc.queryForObject("INSERT INTO corporate_groups (name, created_at, updated_at) VALUES (?, ?, ?) RETURNING id",
                    Long.class, name, now, now);
            groups.put(name, id);
            jdbc.update("""
                    INSERT INTO group_credit_limits (corporate_group_id, financial_institution_id, amount, created_at, updated_at)
                    VALUES (?, 5, ?, ?, ?)
                    """, id, new BigDecimal(name.equals("Gulf Aviation Holdings") ? "12000000" : "2000000"), now, now);
        }
        Map<String, Long> limits = new LinkedHashMap<>();
        for (Company company : COMPANIES) {
            Long companyId = jdbc.queryForObject("""
                    INSERT INTO counterparties (name, status, created_at, updated_at)
                    VALUES (?, 'ACTIVE', ?, ?) RETURNING id
                    """, Long.class, company.name(), now, now);
            if (company.group() != null) {
                jdbc.update("INSERT INTO corporate_group_memberships (corporate_group_id, counterparty_id) VALUES (?, ?)",
                        groups.get(company.group()), companyId);
            }
            Long limitId = jdbc.queryForObject("""
                    INSERT INTO credit_limits (counterparty_id, financial_institution_id, limit_amount,
                                               used_amount, reserved_amount, created_at, updated_at)
                    VALUES (?, 5, ?, 0, 0, ?, ?) RETURNING id
                    """, Long.class, companyId, new BigDecimal(company.limit()), now, now);
            limits.put(company.name(), limitId);
            String[] ratios = company.ratios();
            jdbc.update("""
                    INSERT INTO counterparty_financial_profiles (counterparty_id, return_on_assets, profit_margin,
                        fixed_asset_turnover, debt_to_total_capital_percent, interest_coverage, total_debt_to_ebitda,
                        financial_year_end, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, companyId, new BigDecimal(ratios[0]), new BigDecimal(ratios[1]), new BigDecimal(ratios[2]),
                    new BigDecimal(ratios[3]), new BigDecimal(ratios[4]), new BigDecimal(ratios[5]),
                    LocalDate.of(now.getYear() - 1, 12, 31), now, now);
        }
        requests.seedCreditRequests(limits, now);
        validateNewData(limits, groups, now);
        Map<String, Map<Long, String>> after = snapshot();
        for (String table : TABLES) {
            Map<Long, String> oldRows = before.get(table);
            Map<Long, String> newRows = after.get(table);
            require(newRows.size() == oldRows.size() + ADDITIONS.getOrDefault(table, 0), "Unexpected row count: " + table);
            oldRows.forEach((id, hash) -> require(hash.equals(newRows.get(id)), "Existing row changed: " + table + "/" + id));
        }
        log.info("Validated approved seed: 3 groups, 9 companies, 35 requests including 16 untouched pending requests; all existing rows preserved");
    }

    private void validatePrerequisites() {
        require(count("SELECT COUNT(*) FROM financial_institutions WHERE id = 5 AND name = 'Citi' AND status = 'ACTIVE'") == 1,
                "Citi institution prerequisite differs");
        Object[][] employees = {{15L, "aliaburashid@hotmail.com", "RELATIONSHIP_MANAGER"},
                {23L, "burashid993@gmail.com", "RISK_OFFICER"}, {58L, "sara.alhaddad@example.com", "RELATIONSHIP_MANAGER"},
                {59L, "fatima.alzayani@example.com", "RISK_OFFICER"}};
        for (Object[] employee : employees) {
            require(count("""
                    SELECT COUNT(*) FROM users WHERE id = ? AND email = ? AND role = ? AND status = 'ACTIVE'
                        AND email_verified = true AND financial_institution_id = 5
                    """, employee) == 1, "Employee prerequisite differs for ID " + employee[0]);
        }
        Map<String, Integer> expected = Map.of("users", 70, "counterparties", 32, "corporate_groups", 3,
                "credit_limits", 78, "credit_requests", 237, "counterparty_financial_profiles", 5,
                "financial_institutions", 9, "corporate_group_memberships", 5, "group_credit_limits", 1);
        expected.forEach((table, number) -> require(count("SELECT COUNT(*) FROM " + table) == number,
                "Audited prerequisite count differs: " + table));
        for (Company company : COMPANIES) {
            require(count("SELECT COUNT(*) FROM counterparties WHERE lower(name) = lower(?)", company.name()) == 0,
                    "Company name collision: " + company.name());
        }
        for (String name : List.of("Gulf Aviation Holdings", "Pearl Infrastructure Group", "Arabian Energy Group")) {
            require(count("SELECT COUNT(*) FROM corporate_groups WHERE lower(name) = lower(?)", name) == 0,
                    "Group name collision: " + name);
        }
        require(count("SELECT COUNT(*) FROM credit_limits WHERE financial_institution_id = 5") == 12, "Citi limits differ");
        Object[][] limits = {{1, "1000000", "0", "680000", "ACTIVE"}, {2, "2000000", "35000", "0", "ACTIVE"},
                {33, "2500000", "0", "15000", "ACTIVE"}, {34, "400000", "25000", "0", "ACTIVE"},
                {35, "2200000", "55000", "0", "ACTIVE"}, {36, "550000", "0", "0", "CLOSED"},
                {37, "750000", "0", "75000", "ACTIVE"}, {38, "800000", "85000", "0", "ACTIVE"},
                {39, "1250000", "0", "0", "FROZEN"}, {40, "3200000", "50000", "0", "ACTIVE"},
                {77, "1600000", "0", "0", "FROZEN"}, {78, "900000", "0", "0", "CLOSED"}};
        for (Object[] limit : limits) {
            require(count("""
                    SELECT COUNT(*) FROM credit_limits cl JOIN counterparties c ON c.id = cl.counterparty_id
                    WHERE cl.id = ? AND cl.financial_institution_id = 5 AND cl.limit_amount = ?
                        AND cl.used_amount = ? AND cl.reserved_amount = ? AND c.status = ?
                    """, limit[0], new BigDecimal((String) limit[1]), new BigDecimal((String) limit[2]),
                    new BigDecimal((String) limit[3]), limit[4]) == 1, "Audited Citi balance/status differs: " + limit[0]);
        }
        Map<String, Integer> statuses = Map.of("CANCELLED", 12, "EXPIRED", 7, "PENDING_APPROVAL", 1,
                "REJECTED", 9, "RESERVED", 5, "USED", 5);
        statuses.forEach((status, number) -> require(count("""
                SELECT COUNT(*) FROM credit_requests r JOIN credit_limits cl ON cl.id = r.credit_limit_id
                WHERE cl.financial_institution_id = 5 AND r.status = ?
                """, status) == number, "Citi request status count differs: " + status));
        require(count("""
                SELECT COUNT(*) FROM credit_requests WHERE id = 183 AND seed_key = 'demo-5-37-1'
                    AND credit_limit_id = 37 AND amount = 80000 AND status = 'PENDING_APPROVAL'
                """) == 1, "Legacy request prerequisite differs");
        require(count("""
                SELECT COUNT(*) FROM corporate_groups WHERE (id = 2 AND name = 'Bahrain Holding Group')
                    OR (id = 3 AND name = 'Gulf Investment Group') OR (id = 4 AND name = 'Al Noor Holdings')
                """) == 3, "Existing group prerequisite differs");
        require(count("SELECT COUNT(*) FROM group_credit_limits WHERE corporate_group_id = 2 AND financial_institution_id = 5 AND amount = 6000000") == 1,
                "Existing shared limit differs");
        require(count("""
                SELECT COUNT(*) FROM corporate_group_memberships m JOIN counterparties c ON c.id = m.counterparty_id
                WHERE (m.corporate_group_id = 2 AND c.name = 'Bahrain Trading Company')
                    OR (m.corporate_group_id = 3 AND c.name IN ('Bahrain Gateway Warehousing', 'Falcon Freight Services',
                        'Gulf Meridian Logistics W.L.L.', 'Pearl Coast Shipping Co.'))
                """) == 5, "Existing group membership differs");
        validateLedger();
    }

    private void validateNewData(Map<String, Long> limits, Map<String, Long> groups, LocalDateTime now) {
        validateLedger();
        for (Company company : COMPANIES) {
            require(count("""
                    SELECT COUNT(*) FROM credit_limits WHERE id = ? AND limit_amount = ? AND used_amount = ? AND reserved_amount = ?
                    """, limits.get(company.name()), new BigDecimal(company.limit()), new BigDecimal(company.used()),
                    new BigDecimal(company.reserved())) == 1, "New company balances do not match plan: " + company.name());
        }
        for (String name : groups.keySet()) {
            BigDecimal expected = new BigDecimal(name.equals("Gulf Aviation Holdings") ? "800000"
                    : name.equals("Pearl Infrastructure Group") ? "1600000" : "1200000");
            BigDecimal actual = jdbc.queryForObject("""
                    SELECT SUM(cl.used_amount + cl.reserved_amount) FROM credit_limits cl
                    JOIN corporate_group_memberships m ON m.counterparty_id = cl.counterparty_id
                    WHERE m.corporate_group_id = ? AND cl.financial_institution_id = 5
                    """, BigDecimal.class, groups.get(name));
            require(actual != null && expected.compareTo(actual) == 0, "New group exposure differs: " + name);
        }
        require(count("""
                SELECT COUNT(*) FROM credit_requests r JOIN credit_limits cl ON cl.id = r.credit_limit_id
                WHERE r.seed_key LIKE 'development-citi-%' AND r.status = 'PENDING_APPROVAL' AND r.amount = 600000
                    AND r.requester_id = 15 AND cl.financial_institution_id = 5 AND r.expires_at IS NULL
                    AND NOT EXISTS (SELECT 1 FROM approval_decisions d WHERE d.credit_request_id = r.id)
                """) == 16, "Pending requests are not presentation-ready");
        require(count("""
                SELECT COUNT(*) FROM credit_requests WHERE seed_key LIKE 'development-citi-%'
                    AND (updated_at < created_at OR updated_at > ?
                        OR (expires_at IS NOT NULL AND expires_at <> created_at + INTERVAL '24 hours'))
                """, now) == 0, "Seeded lifecycle dates are inconsistent");
    }

    private void validateLedger() {
        require(count("""
                SELECT COUNT(*) FROM credit_limits cl WHERE used_amount < 0 OR reserved_amount < 0
                    OR used_amount + reserved_amount > limit_amount
                    OR used_amount <> (SELECT COALESCE(SUM(r.amount), 0) FROM credit_requests r WHERE r.credit_limit_id = cl.id AND r.status = 'USED')
                    OR reserved_amount <> (SELECT COALESCE(SUM(r.amount), 0) FROM credit_requests r WHERE r.credit_limit_id = cl.id AND r.status = 'RESERVED')
                """) == 0, "Individual exposure ledger does not reconcile");
        require(count("SELECT COUNT(*) FROM (SELECT credit_request_id FROM approval_decisions GROUP BY credit_request_id HAVING COUNT(*) > 1) d") == 0,
                "Duplicate approval decisions found");
        require(count("""
                SELECT COUNT(*) FROM group_credit_limits gl WHERE gl.amount < (
                    SELECT COALESCE(SUM(cl.used_amount + cl.reserved_amount), 0)
                    FROM corporate_group_memberships m JOIN credit_limits cl ON cl.counterparty_id = m.counterparty_id
                    WHERE m.corporate_group_id = gl.corporate_group_id AND cl.financial_institution_id = gl.financial_institution_id)
                """) == 0, "Shared corporate group exposure exceeds its limit");
    }

    private Map<String, Map<Long, String>> snapshot() {
        Map<String, Map<Long, String>> result = new LinkedHashMap<>();
        for (String table : TABLES) {
            Map<Long, String> rows = new LinkedHashMap<>();
            jdbc.query("SELECT * FROM " + table + " ORDER BY id", rs -> {
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    ResultSetMetaData metadata = rs.getMetaData();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        String value = rs.getString(i);
                        String encoded = value == null ? "null;" : value.length() + ":" + value + ";";
                        digest.update(encoded.getBytes(StandardCharsets.UTF_8));
                    }
                    rows.put(rs.getLong("id"), HexFormat.of().formatHex(digest.digest()));
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            });
            result.put(table, rows);
        }
        return result;
    }

    private long count(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message + "; seed rolled back");
    }
}
