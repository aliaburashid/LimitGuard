package com.example.limitguard.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Four explicitly approved Unit 3 records, created only inside the parent seed transaction. */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "limitguard.seed.enabled", havingValue = "true")
public class DemoCreditRequestSeeder {
    static final String PREFIX = "unit3-citi-";
    private final JdbcTemplate jdbc;

    public DemoCreditRequestSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record RequestSeed(String key, long companyId, long requester, BigDecimal amount, String status,
                       LocalDateTime created, LocalDateTime updated, LocalDateTime expires) { }

    static List<RequestSeed> approvedRequests(LocalDateTime now) {
        LocalDateTime history = now.minusDays(7);
        return List.of(
                new RequestSeed(PREFIX + "falcon-used", 5, 15, new BigDecimal("300000"), "USED",
                        history, history.plusHours(2), history.plusHours(24)),
                new RequestSeed(PREFIX + "gateway-used", 6, 58, new BigDecimal("225000"), "USED",
                        history, history.plusHours(2), history.plusHours(24)),
                new RequestSeed(PREFIX + "dual-approval", 1, 15, new BigDecimal("600000"), "PENDING_APPROVAL",
                        now, now, null),
                new RequestSeed(PREFIX + "approval-versus-rejection", 1, 15, new BigDecimal("600000"), "PENDING_APPROVAL",
                        now, now, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void seedCreditRequests(Map<Long, Long> limits, LocalDateTime now) {
        for (RequestSeed seed : approvedRequests(now)) {
            jdbc.update("""
                    INSERT INTO credit_requests
                        (seed_key, credit_limit_id, requester_id, amount, status, created_at, updated_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, seed.key(), limits.get(seed.companyId()), seed.requester(), seed.amount(),
                    seed.status(), seed.created(), seed.updated(), seed.expires());
        }
    }
}
