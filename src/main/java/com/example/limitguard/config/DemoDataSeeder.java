package com.example.limitguard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit invocation only: deliberately no startup runner or application event listener. */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "limitguard.seed.enabled", havingValue = "true")
public class DemoDataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final List<String> TABLES = List.of("approval_decisions", "audit_logs",
            "corporate_group_memberships", "corporate_groups", "counterparties", "counterparty_financial_profiles",
            "credit_limits", "credit_requests", "financial_institutions", "group_credit_limits", "user_tokens", "users");
    private final JdbcTemplate jdbc;
    private final DemoCreditRequestSeeder requests;

    public DemoDataSeeder(JdbcTemplate jdbc, DemoCreditRequestSeeder requests) {
        this.jdbc = jdbc;
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public void preflight() {
        require("limitguard".equals(jdbc.queryForObject("SELECT current_catalog", String.class)), "Wrong database");
        if (!alreadySeeded()) validatePrerequisites();
    }

    @Transactional(rollbackFor = Exception.class)
    public void seedApprovedAdjustment() {
        require("limitguard".equals(jdbc.queryForObject("SELECT current_catalog", String.class)), "Wrong database");
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        // Only the explicitly invoked seed takes these locks; business services are unchanged.
        jdbc.execute("LOCK TABLE " + String.join(", ", TABLES) + " IN SHARE ROW EXCLUSIVE MODE");
        if (alreadySeeded()) {
            log.info("Unit 3 seed already exists; skipping without changing balances, statuses or expiry times");
            return;
        }
        validatePrerequisites();
        Map<String, Map<Long, String>> before = snapshot();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Map<Long, Long> limits = new LinkedHashMap<>();
        limits.put(1L, 1L);
        for (long company : List.of(5L, 6L)) {
            // Opening exposure is backed by the historical USED request inserted in this same transaction.
            BigDecimal used = DemoCreditRequestSeeder.approvedRequests(now).stream()
                    .filter(r -> r.companyId() == company && r.status().equals("USED"))
                    .map(DemoCreditRequestSeeder.RequestSeed::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            Long id = jdbc.queryForObject("""
                    INSERT INTO credit_limits (counterparty_id, financial_institution_id, limit_amount,
                                               used_amount, reserved_amount, created_at, updated_at)
                    VALUES (?, 5, 1000000, ?, 0, ?, ?) RETURNING id
                    """, Long.class, company, used, now.minusDays(8), now.minusDays(7).plusHours(2));
            limits.put(company, id);
        }
        requests.seedCreditRequests(limits, now);
        validateLedger();
        require(groupExposure(3).compareTo(new BigDecimal("1200000")) == 0, "Expected group exposure 1200000");
        require(count("""
                SELECT COUNT(*) FROM credit_requests r WHERE r.seed_key IN (?, ?) AND r.status = 'PENDING_APPROVAL'
                    AND r.amount = 600000 AND r.requester_id = 15 AND r.credit_limit_id = 1 AND r.expires_at IS NULL
                    AND NOT EXISTS (SELECT 1 FROM approval_decisions d WHERE d.credit_request_id = r.id)
                """, DemoCreditRequestSeeder.PREFIX + "dual-approval",
                DemoCreditRequestSeeder.PREFIX + "approval-versus-rejection") == 2, "Pending records are inconsistent");
        Map<String, Map<Long, String>> after = snapshot();
        for (String table : TABLES) {
            Map<Long, String> oldRows = before.get(table);
            Map<Long, String> newRows = after.get(table);
            int additions = table.equals("credit_limits") ? 2 : table.equals("credit_requests") ? 4 : 0;
            require(newRows.size() == oldRows.size() + additions, "Unexpected additions: " + table);
            oldRows.forEach((id, hash) -> require(hash.equals(newRows.get(id)), "Existing row changed: " + table + "/" + id));
        }
        log.info("Unit 3 seed validated: exactly two facilities and four requests; every existing row preserved");
    }

    private boolean alreadySeeded() {
        List<String> keys = jdbc.queryForList("SELECT seed_key FROM credit_requests WHERE seed_key LIKE 'unit3-citi-%'", String.class);
        if (keys.isEmpty()) return false;
        List<DemoCreditRequestSeeder.RequestSeed> expected = DemoCreditRequestSeeder.approvedRequests(LocalDateTime.now());
        require(keys.size() == 4 && keys.containsAll(expected.stream().map(DemoCreditRequestSeeder.RequestSeed::key).toList()),
                "Partial or conflicting Unit 3 seed; refusing automatic repair");
        for (var seed : expected) {
            require(count("""
                    SELECT COUNT(*) FROM credit_requests r JOIN credit_limits cl ON cl.id = r.credit_limit_id
                    WHERE r.seed_key = ? AND r.amount = ? AND r.requester_id = ?
                        AND cl.counterparty_id = ? AND cl.financial_institution_id = 5
                    """, seed.key(), seed.amount(), seed.requester(), seed.companyId()) == 1, "Seed identity differs: " + seed.key());
        }
        // Decisions may have consumed pending records. Do not reset or refill anything on rerun.
        return true;
    }

    private void validatePrerequisites() {
        require(count("SELECT COUNT(*) FROM financial_institutions WHERE id = 5 AND name = 'Citi' AND status = 'ACTIVE'") == 1,
                "Citi prerequisite differs");
        Object[][] employees = {{15L, "aliaburashid@hotmail.com", "RELATIONSHIP_MANAGER"},
                {23L, "burashid993@gmail.com", "RISK_OFFICER"}, {58L, "sara.alhaddad@example.com", "RELATIONSHIP_MANAGER"},
                {59L, "fatima.alzayani@example.com", "RISK_OFFICER"}};
        for (Object[] employee : employees) {
            require(count("""
                    SELECT COUNT(*) FROM users WHERE id = ? AND email = ? AND role = ? AND status = 'ACTIVE'
                        AND email_verified = true AND financial_institution_id = 5
                    """, employee) == 1, "Employee prerequisite differs: " + employee[0]);
        }
        Map<String, Integer> expected = Map.of("users", 70, "financial_institutions", 9, "counterparties", 32,
                "corporate_groups", 3, "corporate_group_memberships", 5, "credit_limits", 78,
                "group_credit_limits", 2, "counterparty_financial_profiles", 5, "credit_requests", 238, "approval_decisions", 95);
        expected.forEach((table, size) -> require(count("SELECT COUNT(*) FROM " + table) == size, "Baseline count differs: " + table));
        require(count("SELECT COUNT(*) FROM credit_requests WHERE seed_key LIKE 'development-citi-%'") == 0,
                "Unexpected larger seed exists");
        Object[][] companies = {{1L, "Bahrain Trading Company", "ACTIVE", 2L, 1L},
                {3L, "Gulf Meridian Logistics W.L.L.", "ACTIVE", 3L, 2L},
                {4L, "Pearl Coast Shipping Co.", "FROZEN", 3L, 3L},
                {5L, "Falcon Freight Services", "ACTIVE", 3L, 4L},
                {6L, "Bahrain Gateway Warehousing", "ACTIVE", 3L, 5L}};
        for (Object[] company : companies) {
            require(count("""
                    SELECT COUNT(*) FROM counterparties c JOIN corporate_group_memberships m ON m.counterparty_id = c.id
                    JOIN counterparty_financial_profiles p ON p.counterparty_id = c.id
                    WHERE c.id = ? AND c.name = ? AND c.status = ? AND m.corporate_group_id = ? AND p.id = ?
                        AND p.financial_year_end = DATE '2025-12-31'
                        AND c.created_at <= LOCALTIMESTAMP - INTERVAL '8 days'
                    """, company) == 1, "Company, membership or profile prerequisite differs: " + company[0]);
        }
        require(count("SELECT COUNT(*) FROM credit_limits WHERE financial_institution_id = 5 AND counterparty_id IN (5, 6)") == 0,
                "Target Citi facilities already exist");
        checkLimit(1, "2000000", "0", "680000");
        checkLimit(33, "2500000", "0", "15000");
        checkLimit(37, "750000", "0", "675000");
        checkLimit(39, "1250000", "0", "0");
        require(count("""
                SELECT COUNT(*) FROM group_credit_limits gl JOIN corporate_groups g ON g.id = gl.corporate_group_id
                WHERE (gl.id = 1 AND gl.corporate_group_id = 2 AND g.name = 'Bahrain Holding Group'
                    AND gl.financial_institution_id = 5 AND gl.amount = 6000000)
                   OR (gl.id = 2 AND gl.corporate_group_id = 3 AND g.name = 'Gulf Investment Group'
                    AND gl.financial_institution_id = 5 AND gl.amount = 2000000)
                """) == 2, "Shared limit prerequisite differs");
        require(groupExposure(2).compareTo(new BigDecimal("680000")) == 0, "Group 2 headroom changed");
        require(groupExposure(3).compareTo(new BigDecimal("675000")) == 0, "Group 3 headroom changed");
        require(count("""
                SELECT COUNT(*) FROM credit_requests WHERE id = 402 AND status = 'RESERVED' AND amount = 600000
                    AND requester_id = 15 AND credit_limit_id = 37 AND expires_at > LOCALTIMESTAMP
                    AND expires_at = TIMESTAMP '2026-10-30 09:37:07.643964'
                """) == 1, "Request 402 changed or expired; 800000 scenario would be invalid");
        require(count("SELECT COUNT(*) FROM approval_decisions WHERE credit_request_id = 402 AND decision = 'APPROVED'") == 1,
                "Request 402 decision differs");
        require(count("""
                SELECT COUNT(*) FROM credit_requests WHERE id = 165 AND status = 'RESERVED' AND amount = 15000
                    AND requester_id = 15 AND credit_limit_id = 33 AND expires_at > LOCALTIMESTAMP
                """) == 1, "Cancellation request 165 is no longer eligible");
        validateLedger();
    }

    private void checkLimit(long id, String amount, String used, String reserved) {
        require(count("""
                SELECT COUNT(*) FROM credit_limits WHERE id = ? AND financial_institution_id = 5
                    AND limit_amount = ? AND used_amount = ? AND reserved_amount = ?
                """, id, new BigDecimal(amount), new BigDecimal(used), new BigDecimal(reserved)) == 1,
                "Existing limit balance differs: " + id);
    }

    private BigDecimal groupExposure(long group) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(cl.used_amount + cl.reserved_amount), 0) FROM credit_limits cl
                JOIN corporate_group_memberships m ON m.counterparty_id = cl.counterparty_id
                WHERE m.corporate_group_id = ? AND cl.financial_institution_id = 5
                """, BigDecimal.class, group);
    }

    private void validateLedger() {
        require(count("""
                SELECT COUNT(*) FROM credit_limits cl WHERE used_amount < 0 OR reserved_amount < 0
                    OR used_amount + reserved_amount > limit_amount
                    OR used_amount <> (SELECT COALESCE(SUM(r.amount), 0) FROM credit_requests r WHERE r.credit_limit_id = cl.id AND r.status = 'USED')
                    OR reserved_amount <> (SELECT COALESCE(SUM(r.amount), 0) FROM credit_requests r WHERE r.credit_limit_id = cl.id AND r.status = 'RESERVED')
                """) == 0, "Exposure ledger does not reconcile");
        require(count("SELECT COUNT(*) FROM (SELECT credit_request_id FROM approval_decisions GROUP BY credit_request_id HAVING COUNT(*) > 1) d") == 0,
                "Duplicate approval decisions");
        require(count("""
                SELECT COUNT(*) FROM group_credit_limits gl WHERE gl.amount < (
                    SELECT COALESCE(SUM(cl.used_amount + cl.reserved_amount), 0)
                    FROM corporate_group_memberships m JOIN credit_limits cl ON cl.counterparty_id = m.counterparty_id
                    WHERE m.corporate_group_id = gl.corporate_group_id AND cl.financial_institution_id = gl.financial_institution_id)
                """) == 0, "Shared group exposure exceeds limit");
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
                        digest.update((value == null ? "null;" : value.length() + ":" + value + ";").getBytes(StandardCharsets.UTF_8));
                    }
                    rows.put(rs.getLong("id"), HexFormat.of().formatHex(digest.digest()));
                } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
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
        if (!condition) throw new IllegalStateException(message + "; no changes committed");
    }
}
