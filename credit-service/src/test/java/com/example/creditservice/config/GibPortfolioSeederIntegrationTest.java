package com.example.creditservice.config;

import com.example.creditservice.client.AuthServiceClient;
import com.example.creditservice.dto.AuthContextResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.username=expiry_test",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/exposure-snapshots.sql",
        "limitguard.seed.gib.enabled=true",
        "limitguard.notification-relay.enabled=false",
        "limitguard.credit-request-expiry.enabled=false",
        "limitguard.exposure-snapshots.enabled=false",
        "jwt-secret=" + GibPortfolioSeederIntegrationTest.SIGNING_KEY,
        "internal-service-key=gib-credit-test-key"
})
@ActiveProfiles("demo")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "GIB_CREDIT_TEST_JDBC_URL", matches = ".+")
class GibPortfolioSeederIntegrationTest {
    static final String SIGNING_KEY = "gib-credit-fixtures-test-only-signing-key";

    @Autowired JdbcTemplate jdbc;
    @Autowired DemoDataSeeder seeder;
    @Autowired MockMvc mvc;
    @MockitoBean AuthServiceClient auth;

    @DynamicPropertySource
    static void disposableServices(DynamicPropertyRegistry properties) {
        String database = System.getenv("GIB_CREDIT_TEST_JDBC_URL");
        String ml = System.getenv("CREDIT_ML_TEST_URL");
        if (!database.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/limitguard_gib_credit(_both)?_test")
                || database.contains(":5432/") || ml == null || !ml.matches("http://127[.]0[.]0[.]1:[0-9]+")) {
            throw new IllegalArgumentException("GIB portfolio tests require disposable PostgreSQL and loopback FastAPI");
        }
        properties.add("spring.datasource.url", () -> database);
        properties.add("ml.service.url", () -> ml);
        properties.add("limitguard.seed.citi.enabled", GibPortfolioSeederIntegrationTest::citiEnabled);
    }

    @Test
    void seedsCompletePortfolioAndSupportsLiveWorkflowsWithoutResettingThemOnRerun() throws Exception {
        long bank = id("SELECT id FROM financial_institutions WHERE name='Gulf International Bank'");
        assertThat(jdbc.queryForList("""
            SELECT role FROM users WHERE financial_institution_id=? AND status='ACTIVE' AND email_verified ORDER BY role
            """, String.class, bank)).containsExactly("RELATIONSHIP_MANAGER", "RELATIONSHIP_MANAGER", "RISK_OFFICER", "RISK_OFFICER");
        assertThat(count("SELECT count(*) FROM credit_limits WHERE financial_institution_id=?", bank)).isEqualTo(6);
        assertThat(count("SELECT count(*) FROM credit_limits WHERE financial_institution_id=5")).isEqualTo(citiEnabled() ? 59 : 0);
        assertThat(count("""
            SELECT count(*) FROM credit_limits l JOIN counterparty_financial_profiles p ON p.counterparty_id=l.counterparty_id
            WHERE l.financial_institution_id=? AND num_nonnulls(p.return_on_assets,p.profit_margin,
                p.fixed_asset_turnover,p.debt_to_total_capital_percent,p.interest_coverage,p.total_debt_to_ebitda)=6
            """, bank)).isEqualTo(6);
        assertThat(count("""
            SELECT count(*) FROM corporate_group_memberships m JOIN corporate_groups g ON g.id=m.corporate_group_id
            WHERE g.name='North Reef Holdings'
            """)).isEqualTo(3);
        assertThat(count("""
            SELECT count(*) FROM credit_limits l WHERE l.financial_institution_id=?
                AND NOT EXISTS (SELECT 1 FROM corporate_group_memberships m WHERE m.counterparty_id=l.counterparty_id)
            """, bank)).isEqualTo(3);
        assertThat(jdbc.queryForList("""
            SELECT status || ':' || count(*) FROM credit_requests WHERE seed_key LIKE 'gib-demo-v1-%'
            GROUP BY status ORDER BY status
            """, String.class)).containsExactly("CANCELLED:1", "EXPIRED:1", "PENDING_APPROVAL:2",
                "REJECTED:1", "RESERVED:2", "USED:6");
        assertThat(count("""
            SELECT count(*) FROM approval_decisions d JOIN credit_requests r ON r.id=d.credit_request_id
            JOIN users u ON u.id=d.decided_by_id
            WHERE r.seed_key LIKE 'gib-demo-v1-%' AND u.financial_institution_id=? AND u.role='RISK_OFFICER'
              AND d.reason IS NOT NULL AND d.decided_by_id<>r.requester_id
            """, bank)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM credit_exposure_snapshots WHERE institution_id=?", bank)).isEqualTo(36);
        assertThat(count("""
            SELECT count(*) FROM credit_exposure_snapshots WHERE institution_id=?
              AND (used_amount<0 OR reserved_amount<0 OR used_amount+reserved_amount>limit_amount
                   OR (captured_at AT TIME ZONE 'UTC')::date<>reporting_date)
            """, bank)).isZero();
        assertThat(count("SELECT count(*) FROM credit_exposure_snapshots WHERE institution_id=? AND reserved_amount>0", bank))
                .isPositive();
        assertThat(count("SELECT count(*) FROM notification_outbox")).isZero();
        assertThat(count("SELECT count(*) FROM audit_logs")).isZero();
        assertBalances(bank);
        var seededRows = portfolioRows();
        seeder.run(new DefaultApplicationArguments());
        assertThat(portfolioRows()).isEqualTo(seededRows);

        String gibRm = bearer("gib.noura.almarafi@example.com", bank, "RELATIONSHIP_MANAGER");
        String gibRo = bearer("gib.layla.almansoori@example.com", bank, "RISK_OFFICER");
        long gibCompany = id("SELECT id FROM counterparties WHERE name='Dilmun Medical Supplies W.L.L.'");
        long group = id("SELECT id FROM corporate_groups WHERE name='North Reef Holdings'");
        long riffa = id("""
            SELECT l.id FROM credit_limits l JOIN counterparties c ON c.id=l.counterparty_id
            WHERE c.name='Riffa Industrial Rentals W.L.L.' AND l.financial_institution_id=?
            """, bank);
        long saffron = id("""
            SELECT l.id FROM credit_limits l JOIN counterparties c ON c.id=l.counterparty_id
            WHERE c.name='Saffron Coast Food Distribution W.L.L.' AND l.financial_institution_id=?
            """, bank);
        long pending = id("SELECT id FROM credit_requests WHERE seed_key='gib-demo-v1-dilmun-approval'");
        long review = id("SELECT id FROM credit_requests WHERE seed_key='gib-demo-v1-dilmun-review'");

        mvc.perform(get("/api/counterparties?size=2&sort=name,asc&status=ACTIVE").header("Authorization", gibRo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
        if (citiEnabled()) {
            verifyInstitutionIsolation(gibRo, gibCompany, pending);
        }
        mvc.perform(post("/api/credit-requests").header("Authorization", gibRm)
                        .contentType("application/json").content("{\"creditLimitId\":" + riffa + ",\"amount\":10000}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/credit-requests").header("Authorization", gibRm)
                        .contentType("application/json").content("{\"creditLimitId\":" + saffron + ",\"amount\":160000}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/credit-requests/{id}/approve", pending).header("Authorization", gibRm))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/credit-limits/exposure-history").header("Authorization", gibRm))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].synthetic").value(true));
        mvc.perform(get("/api/group-credit-limits/groups/{id}/exposure-breakdown", group).header("Authorization", gibRo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableHeadroom").value(1510000));
        for (long company : jdbc.queryForList("SELECT counterparty_id FROM credit_limits WHERE financial_institution_id=?",
                Long.class, bank)) {
            mvc.perform(post("/api/counterparties/{id}/risk-prediction", company).header("Authorization", gibRo))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.riskLevel").isString());
        }
        mvc.perform(post("/api/corporate-groups/{id}/risk-assessment", group).header("Authorization", gibRo))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.successful == true)]", hasSize(3)));

        mvc.perform(patch("/api/credit-requests/{id}/approve", pending).header("Authorization", gibRo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESERVED"));
        mvc.perform(patch("/api/credit-requests/{id}/reject", review).header("Authorization", gibRo)
                        .contentType("application/json").content("{\"reason\":\"Additional repayment evidence is required.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertBalances(bank);
        assertThat(count("SELECT count(*) FROM notification_outbox WHERE financial_institution_id=?", bank)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE financial_institution_id=?", bank)).isEqualTo(2);
        var afterLiveActions = portfolioRows();
        seeder.run(new DefaultApplicationArguments());
        assertThat(portfolioRows()).isEqualTo(afterLiveActions);
    }

    private static boolean citiEnabled() {
        return !"false".equalsIgnoreCase(System.getenv("GIB_SEED_TEST_INCLUDE_CITI"));
    }

    private void verifyInstitutionIsolation(String gibRo, long gibCompany, long pending) throws Exception {
        String citiRo = bearer("burashid993@gmail.com", 5, "RISK_OFFICER");
        long citiCompany = id("SELECT id FROM counterparties WHERE name='Apple Inc.'");
        mvc.perform(get("/api/counterparties/{id}", citiCompany).header("Authorization", gibRo))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/counterparties/{id}", gibCompany).header("Authorization", citiRo))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/credit-requests/{id}", pending).header("Authorization", citiRo))
                .andExpect(status().isForbidden());
    }

    private void assertBalances(long bank) {
        assertThat(count("""
            SELECT count(*) FROM credit_limits l WHERE l.financial_institution_id=? AND (
                l.used_amount + l.reserved_amount > l.limit_amount
                OR l.used_amount <> (SELECT coalesce(sum(r.amount),0) FROM credit_requests r
                    WHERE r.credit_limit_id=l.id AND r.status='USED')
                OR l.reserved_amount <> (SELECT coalesce(sum(r.amount),0) FROM credit_requests r
                    WHERE r.credit_limit_id=l.id AND r.status='RESERVED'))
            """, bank)).isZero();
    }

    private Map<String, List<String>> portfolioRows() {
        var rows = new TreeMap<String, List<String>>();
        for (String table : List.of("financial_institutions", "users", "counterparties", "corporate_groups",
                "corporate_group_memberships", "counterparty_financial_profiles", "credit_limits", "group_credit_limits",
                "credit_requests", "approval_decisions", "credit_exposure_snapshots", "audit_logs", "notification_outbox")) {
            rows.put(table, jdbc.queryForList("SELECT md5(row_to_json(t)::text) FROM " + table + " t ORDER BY 1", String.class));
        }
        return rows;
    }

    private String bearer(String email, long bank, String role) {
        long id = id("SELECT id FROM users WHERE email=?", email);
        String token = Jwts.builder().subject(email).expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SIGNING_KEY.getBytes(StandardCharsets.UTF_8))).compact();
        when(auth.getAuthContext(token)).thenReturn(new AuthContextResponse(id, role, "ACTIVE", bank));
        return "Bearer " + token;
    }

    private long id(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Long.class, parameters);
    }

    private long count(String sql, Object... parameters) {
        return id(sql, parameters);
    }
}
