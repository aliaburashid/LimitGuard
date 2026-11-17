package com.example.creditservice.config;

import com.example.creditservice.CreditServiceApplication;
import com.example.creditservice.exception.InvalidCreditRequestOperationException;
import com.example.creditservice.exception.InvalidCreditRequestStateException;
import com.example.creditservice.service.CreditRequestService;
import org.springframework.boot.DefaultApplicationArguments;
import com.example.creditservice.security.AuthenticatedCreditUser;
import com.example.creditservice.service.MlRiskPredictionService;
import com.example.creditservice.service.ParallelRiskAssessmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CITI_SEED_TEST_PORT", matches = "[0-9]{1,5}")
class CitiPortfolioSeederIntegrationTest {
    private static final List<String> CATALOG = List.of("financial_institutions", "counterparties",
            "corporate_groups", "corporate_group_memberships", "credit_limits", "group_credit_limits",
            "counterparty_financial_profiles");
    private static final List<String> HISTORY = List.of("credit_requests", "approval_decisions",
            "credit_exposure_snapshots", "notification_outbox", "audit_logs");

    @Test
    void seedsFreshSchemaAndReadsStoredRatiosThroughIndividualAndGroupMl() {
        String database = "limitguard_citi_seed_fresh";
        try (var context = start(database, "update", false)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(context.getBeansOfType(CitiPortfolioSeeder.class)).isEmpty();
            jdbc.execute("""
                CREATE TABLE financial_institutions (
                    id bigint PRIMARY KEY, name text NOT NULL, status text NOT NULL)
                """);
            jdbc.update("INSERT INTO financial_institutions VALUES (5,'Citi','ACTIVE')");
            jdbc.execute("""
                CREATE TABLE users (
                    id bigint PRIMARY KEY, email text, financial_institution_id bigint,
                    role text, status text, email_verified boolean)
                """);
            jdbc.update("""
                INSERT INTO users VALUES
                    (15,'aliaburashid@hotmail.com',5,'RELATIONSHIP_MANAGER','ACTIVE',true),
                    (58,'sara.alhaddad@example.com',5,'RELATIONSHIP_MANAGER','ACTIVE',true),
                    (23,'burashid993@gmail.com',5,'RISK_OFFICER','ACTIVE',true),
                    (59,'fatima.alzayani@example.com',5,'RISK_OFFICER','ACTIVE',true)
                """);
        }
        Map<String, List<String>> seeded;
        try (var context = start(database, "validate", true)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertPortfolio(jdbc);
            assertThat(count(jdbc, "SELECT count(*) FROM counterparties")).isEqualTo(59);
            assertThat(count(jdbc, "SELECT count(*) FROM corporate_groups")).isEqualTo(10);
            assertThat(count(jdbc, "SELECT count(*) FROM corporate_group_memberships")).isEqualTo(30);
            assertThat(count(jdbc, "SELECT count(*) FROM credit_limits WHERE used_amount <> 0 OR reserved_amount <> 0")).isZero();
            assertThat(count(jdbc, "SELECT count(*) FROM credit_requests")).isZero();
            assertThat(count(jdbc, "SELECT count(*) FROM notification_outbox")).isZero();
            assertThat(count(jdbc, "SELECT count(*) FROM audit_logs")).isZero();
            verifyMl(context, jdbc);
            seeded = rows(jdbc, CATALOG);
        }
        try (var context = start(database, "validate", true)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(rows(jdbc, CATALOG)).isEqualTo(seeded);
            assertThat(count(jdbc, "SELECT count(*) FROM credit_requests WHERE seed_key LIKE 'unit3-citi-%'")).isZero();
        }
        try (var context = start(database, "validate", true, true)) {
            verifyUnit3Scenarios(context);
        }
    }

    private void verifyUnit3Scenarios(ConfigurableApplicationContext context) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForList("""
            SELECT status || ':' || count(*) FROM credit_requests WHERE seed_key LIKE 'unit3-citi-%'
            GROUP BY status ORDER BY status
            """, String.class)).containsExactly("PENDING_APPROVAL:2", "USED:2");
        assertThat(count(jdbc, "SELECT count(*) FROM notification_outbox")).isZero();
        assertThat(count(jdbc, """
            SELECT count(*) FROM credit_limits f
            WHERE f.used_amount <> (SELECT coalesce(sum(r.amount),0) FROM credit_requests r
                WHERE r.credit_limit_id=f.id AND r.status='USED')
            """)).isZero();

        long dual = count(jdbc, "SELECT id FROM credit_requests WHERE seed_key='unit3-citi-dual-approval'");
        long mixed = count(jdbc, "SELECT id FROM credit_requests WHERE seed_key='unit3-citi-approval-versus-rejection'");
        CreditRequestService requests = context.getBean(CreditRequestService.class);
        assertThat(race(
                () -> asOfficer(23, () -> requests.approveCreditRequest(dual)),
                () -> asOfficer(59, () -> requests.approveCreditRequest(dual)))).isEqualTo(1);
        assertThat(race(
                () -> asOfficer(23, () -> requests.approveCreditRequest(mixed)),
                () -> asOfficer(59, () -> requests.rejectCreditRequest(mixed, "Further review is required."))))
                .isEqualTo(1);
        assertThat(count(jdbc, "SELECT count(*) FROM approval_decisions")).isEqualTo(2);
        assertThat(count(jdbc, """
            SELECT count(*) FROM credit_limits f
            WHERE f.reserved_amount <> (SELECT coalesce(sum(r.amount),0) FROM credit_requests r
                WHERE r.credit_limit_id=f.id AND r.status='RESERVED')
            """)).isZero();

        var catalog = rows(jdbc, CATALOG);
        var history = rows(jdbc, List.of("credit_requests", "approval_decisions", "notification_outbox", "audit_logs"));
        context.getBean(DemoDataSeeder.class).run(new DefaultApplicationArguments());
        assertThat(rows(jdbc, CATALOG)).isEqualTo(catalog);
        assertThat(rows(jdbc, List.of("credit_requests", "approval_decisions", "notification_outbox", "audit_logs")))
                .isEqualTo(history);
    }

    private int race(Runnable first, Runnable second) {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var firstResult = pool.submit(atStart(start, first));
            var secondResult = pool.submit(atStart(start, second));
            start.countDown();
            return firstResult.get(25, TimeUnit.SECONDS) + secondResult.get(25, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("Unit 3 race did not complete", failure);
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> atStart(CountDownLatch start, Runnable action) {
        return () -> {
            start.await();
            try {
                action.run();
                return 1;
            } catch (InvalidCreditRequestStateException | InvalidCreditRequestOperationException expected) {
                return 0;
            }
        };
    }

    private void asOfficer(long officer, Runnable action) {
        var user = new AuthenticatedCreditUser(officer, "officer@example.com", "RISK_OFFICER", 5L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CITI_SEED_TEST_COPY", matches = "true")
    void preservesRestoredPortfolioAndHistoryAcrossBothRuns() {
        String database = "limitguard_citi_seed_copy";
        Map<String, List<String>> originalCatalog;
        Map<String, List<String>> originalHistory;
        try (var context = start(database, "validate", false)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "SELECT count(*) FROM credit_limits WHERE financial_institution_id=5"))
                    .isEqualTo(46);
            originalCatalog = rows(jdbc, CATALOG);
            originalHistory = rows(jdbc, HISTORY);
        }
        Map<String, List<String>> firstRun;
        try (var context = start(database, "validate", true)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertPortfolio(jdbc);
            firstRun = rows(jdbc, CATALOG);
            originalCatalog.forEach((table, original) -> assertThat(firstRun.get(table))
                    .as("existing %s rows", table).containsAll(original));
            assertThat(firstRun.get("financial_institutions")).isEqualTo(originalCatalog.get("financial_institutions"));
            assertThat(firstRun.get("credit_limits")).hasSize(originalCatalog.get("credit_limits").size() + 13);
            assertThat(firstRun.get("group_credit_limits")).hasSize(originalCatalog.get("group_credit_limits").size() + 2);
            assertThat(firstRun.get("counterparty_financial_profiles"))
                    .hasSize(originalCatalog.get("counterparty_financial_profiles").size() + 30);
            assertThat(rows(jdbc, HISTORY)).isEqualTo(originalHistory);
            assertThat(count(jdbc, """
                SELECT count(*) FROM credit_limits l JOIN counterparties c ON c.id=l.counterparty_id
                WHERE l.financial_institution_id=5 AND c.id BETWEEN 3 AND 32
                """)).isEqualTo(12);
        }
        try (var context = start(database, "validate", true)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(rows(jdbc, CATALOG)).isEqualTo(firstRun);
            assertThat(rows(jdbc, HISTORY)).isEqualTo(originalHistory);
        }
    }

    private void assertPortfolio(JdbcTemplate jdbc) {
        assertThat(count(jdbc, """
            SELECT count(*) FROM credit_limits f
            JOIN counterparty_financial_profiles p ON p.counterparty_id=f.counterparty_id
            WHERE f.financial_institution_id=5 AND num_nonnulls(p.return_on_assets,p.profit_margin,
                p.fixed_asset_turnover,p.debt_to_total_capital_percent,p.interest_coverage,p.total_debt_to_ebitda)=6
            """)).isEqualTo(59);
        assertThat(count(jdbc, "SELECT count(*) FROM group_credit_limits WHERE financial_institution_id=5")).isEqualTo(10);
        assertThat(count(jdbc, """
            SELECT count(*) FROM credit_limits l JOIN corporate_group_memberships m ON m.counterparty_id=l.counterparty_id
            WHERE l.financial_institution_id=5
            """)).isEqualTo(30);
        assertThat(count(jdbc, """
            SELECT count(*) FROM counterparties WHERE
                (name IN ('Pearl Coast Shipping Co.','Harbor Quay Equipment W.L.L. (fictional)') AND status='FROZEN')
                OR (name='North Quay Services W.L.L. (fictional)' AND status='CLOSED')
            """)).isEqualTo(3);
    }

    private void verifyMl(ConfigurableApplicationContext context, JdbcTemplate jdbc) {
        var user = new AuthenticatedCreditUser(1L, "officer@example.com", "RISK_OFFICER", 5L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        try {
            var predictions = context.getBean(MlRiskPredictionService.class);
            for (Long company : jdbc.queryForList("SELECT counterparty_id FROM credit_limits WHERE financial_institution_id=5", Long.class)) {
                assertThat(predictions.predictRisk(company).getRiskLevel()).isNotBlank();
            }
            var assessments = context.getBean(ParallelRiskAssessmentService.class);
            for (Long group : jdbc.queryForList("SELECT corporate_group_id FROM group_credit_limits WHERE financial_institution_id=5", Long.class)) {
                long members = jdbc.queryForObject("SELECT count(*) FROM corporate_group_memberships WHERE corporate_group_id=?", Long.class, group);
                assertThat(assessments.assessGroup(group)).hasSize((int) members)
                        .allSatisfy(result -> assertThat(result.isSuccessful()).isTrue());
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Map<String, List<String>> rows(JdbcTemplate jdbc, List<String> tables) {
        Map<String, List<String>> result = new TreeMap<>();
        for (String table : tables) {
            result.put(table, jdbc.queryForList("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY 1", String.class));
        }
        return result;
    }

    private long count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private ConfigurableApplicationContext start(String database, String ddl, boolean enabled) {
        return start(database, ddl, enabled, false);
    }

    private ConfigurableApplicationContext start(String database, String ddl, boolean enabled, boolean unit3) {
        int port = Integer.parseInt(System.getenv("CITI_SEED_TEST_PORT"));
        String ml = System.getenv("CREDIT_ML_TEST_URL");
        if (port == 5432 || port < 1 || port > 65535 || ml == null || !ml.matches("http://127[.]0[.]0[.]1:[0-9]+")) {
            throw new IllegalArgumentException("Citi seed test requires disposable loopback PostgreSQL and FastAPI");
        }
        return new SpringApplicationBuilder(CreditServiceApplication.class).run(
                "--spring.profiles.active=demo",
                "--limitguard.seed.citi.enabled=" + enabled,
                "--limitguard.seed.unit3.enabled=" + unit3,
                "--limitguard.notification-relay.enabled=false",
                "--limitguard.credit-request-expiry.enabled=false",
                "--limitguard.exposure-snapshots.enabled=false",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + port + "/" + database,
                "--spring.datasource.username=expiry_test", "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=" + ddl, "--spring.sql.init.mode=never",
                "--server.address=127.0.0.1", "--server.port=0", "--ml.service.url=" + ml,
                "--jwt-secret=credit-local-citi-test-signing-key", "--internal-service-key=citi-local-test-key");
    }
}
