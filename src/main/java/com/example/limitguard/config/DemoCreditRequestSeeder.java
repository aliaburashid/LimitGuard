package com.example.limitguard.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Approved development history; only called inside the data seeder's transaction. */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "limitguard.seed.enabled", havingValue = "true")
public class DemoCreditRequestSeeder {
    static final String PREFIX = "development-citi-";
    static final List<String> AVIATION = List.of("Gulf Aviation Services", "Gulf Cargo Logistics",
            "Gulf Aircraft Maintenance", "Gulf Airport Technology");
    static final List<String> SCENARIOS = List.of("approval", "rejection", "dual-approval", "conflicting-decision");

    private final JdbcTemplate jdbc;

    public DemoCreditRequestSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record RequestSeed(String key, String company, long requester, BigDecimal amount, String status,
                       LocalDateTime created, LocalDateTime updated, LocalDateTime expires) { }

    static List<RequestSeed> approvedRequests(LocalDateTime now) {
        List<RequestSeed> result = new ArrayList<>();
        for (String scenario : SCENARIOS) {
            for (int i = 0; i < AVIATION.size(); i++) {
                result.add(new RequestSeed(PREFIX + scenario + "-" + (i + 1), AVIATION.get(i), 15,
                        new BigDecimal("600000"), "PENDING_APPROVAL", now, now, null));
            }
        }
        for (int i = 0; i < AVIATION.size(); i++) {
            addHistory(result, "aviation-used-" + (i + 1), AVIATION.get(i), 15,
                    "200000", "USED", now.minusDays(20 + i));
        }
        for (String company : List.of("Pearl Construction", "Pearl Engineering",
                "Arabian Renewable Energy", "Arabian Industrial Utilities")) {
            boolean pearl = company.startsWith("Pearl");
            for (long rm : List.of(15L, 58L)) {
                addHistory(result, company.toLowerCase().replace(' ', '-') + "-used-" + rm,
                        company, rm, pearl ? "400000" : "300000", "USED", now.minusDays(10 + rm % 3));
            }
        }
        String lifecycle = "Bahrain Digital Services";
        for (String purpose : List.of("cancel", "use", "expiry")) {
            LocalDateTime created = purpose.equals("expiry") ? now.minusHours(23).minusMinutes(55) : now;
            result.add(new RequestSeed(PREFIX + "lifecycle-" + purpose, lifecycle, 15,
                    new BigDecimal("250000"), "RESERVED", created, created, created.plusHours(24)));
        }
        addHistory(result, "lifecycle-used", lifecycle, 15, "250000", "USED", now.minusDays(7));
        addHistory(result, "lifecycle-cancelled", lifecycle, 15, "200000", "CANCELLED", now.minusDays(6));
        addHistory(result, "lifecycle-expired", lifecycle, 15, "200000", "EXPIRED", now.minusDays(5));
        addHistory(result, "lifecycle-rejected", lifecycle, 15, "600000", "REJECTED", now.minusDays(4));
        return List.copyOf(result);
    }

    private static void addHistory(List<RequestSeed> result, String key, String company, long requester,
                                   String amount, String status, LocalDateTime created) {
        result.add(new RequestSeed(PREFIX + key, company, requester, new BigDecimal(amount), status,
                created, created.plusHours(status.equals("EXPIRED") ? 24 : 2),
                status.equals("REJECTED") ? null : created.plusHours(24)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void seedCreditRequests(Map<String, Long> limits, LocalDateTime now) {
        for (RequestSeed seed : approvedRequests(now)) {
            Long requestId = jdbc.queryForObject("""
                    INSERT INTO credit_requests
                        (seed_key, credit_limit_id, requester_id, amount, status, created_at, updated_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, seed.key(), limits.get(seed.company()), seed.requester(), seed.amount(),
                    seed.status(), seed.created(), seed.updated(), seed.expires());
            if (seed.status().equals("REJECTED")) {
                jdbc.update("""
                        INSERT INTO approval_decisions
                            (credit_request_id, decided_by_id, decision, reason, created_at)
                        VALUES (?, 23, 'REJECTED', ?, ?)
                        """, requestId, "Historical credit review: repayment evidence was insufficient", seed.updated());
            }
        }
        // Only the nine limits just created by the caller are touched. Existing balances are never updated.
        for (Long limitId : limits.values()) {
            int updated = jdbc.update("""
                    UPDATE credit_limits cl SET
                        used_amount = (SELECT COALESCE(SUM(amount), 0) FROM credit_requests
                                       WHERE credit_limit_id = cl.id AND status = 'USED'),
                        reserved_amount = (SELECT COALESCE(SUM(amount), 0) FROM credit_requests
                                           WHERE credit_limit_id = cl.id AND status = 'RESERVED')
                    WHERE cl.id = ? AND cl.used_amount = 0 AND cl.reserved_amount = 0
                    """, limitId);
            if (updated != 1) throw new IllegalStateException("New limit balance prerequisite changed: " + limitId);
        }
    }
}
