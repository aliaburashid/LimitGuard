package com.example.limitguard.integration;

import com.example.limitguard.repository.CreditExposureSnapshotRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(CreditExposureSnapshotIntegrationTest.Config.class)
@EnabledIfEnvironmentVariable(named = "EXPOSURE_TEST_DB_URL", matches = ".+")
class CreditExposureSnapshotIntegrationTest {

    // Each run owns a new schema so test cleanup only drops objects created here.
    private static final String schema = "exposure_test_" + UUID.randomUUID().toString().replace("-", "");

    private final JdbcTemplate jdbc;
    private final CreditExposureSnapshotRepository snapshots;
    private final DataSourceTransactionManager transactions;

    @org.springframework.beans.factory.annotation.Autowired
    CreditExposureSnapshotIntegrationTest(JdbcTemplate jdbc, CreditExposureSnapshotRepository snapshots,
                                          DataSourceTransactionManager transactions) {
        this.jdbc = jdbc;
        this.snapshots = snapshots;
        this.transactions = transactions;
    }

    @BeforeAll
    static void initializeSchema() {
        JdbcTemplate admin = new JdbcTemplate(Config.dataSource(""));
        admin.execute("CREATE SCHEMA " + schema);
        JdbcTemplate fixture = new JdbcTemplate(Config.dataSource(schema));
        fixture.execute("""
                CREATE TABLE credit_limits (
                    id BIGINT PRIMARY KEY,
                    financial_institution_id BIGINT NOT NULL,
                    limit_amount NUMERIC(19, 2) NOT NULL,
                    used_amount NUMERIC(19, 2) NOT NULL,
                    reserved_amount NUMERIC(19, 2) NOT NULL,
                    created_at TIMESTAMP
                )
                """);
        fixture.execute("""
                CREATE TABLE credit_requests (
                    id BIGINT PRIMARY KEY,
                    credit_limit_id BIGINT NOT NULL,
                    requester_id BIGINT NOT NULL,
                    seed_key TEXT
                )
                """);
        new ResourceDatabasePopulator(new ClassPathResource("db/exposure-snapshots.sql"))
                .execute(Config.dataSource(schema));
    }

    @AfterAll
    static void removeSchema() {
        new JdbcTemplate(Config.dataSource("")).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @BeforeEach
    void seedFacilities() {
        jdbc.execute("TRUNCATE credit_exposure_snapshots, credit_limits, credit_requests");
        jdbc.update("""
                INSERT INTO credit_limits (id, financial_institution_id, limit_amount, used_amount, reserved_amount) VALUES
                    (11, 1, 100000.25, 10000.10, 1000.20),
                    (12, 1, 200000.50, 20000.25, 2000.30),
                    (13, 1, 400000, 40000, 4000),
                    (21, 2, 900000, 90000, 9000)
                """);
        jdbc.update("INSERT INTO credit_requests (id, credit_limit_id, requester_id) VALUES (1, 11, 7), (2, 11, 7), (3, 12, 7), (4, 13, 8), (5, 21, 7)");
    }

    @Test
    void capturesCurrentBalancesOnceAndAggregatesOnlyAuthorizedFacilities() {
        Instant beforeCapture = Instant.now().minusSeconds(1);
        assertEquals(4, snapshots.capture(true));
        var history = snapshots.findHistory(1L, 7L);
        assertEquals(1, history.size());
        var first = history.get(0);
        assertFalse(first.capturedAt().isBefore(beforeCapture));
        assertFalse(first.capturedAt().isAfter(Instant.now().plusSeconds(1)));
        assertEquals(new BigDecimal("300000.75"), first.limitAmount());
        assertEquals(new BigDecimal("30000.35"), first.usedAmount());
        assertEquals(new BigDecimal("3000.50"), first.reservedAmount());

        assertEquals(new BigDecimal("400000.00"), snapshots.findHistory(1L, 8L).get(0).limitAmount());
        assertEquals(new BigDecimal("900000.00"), snapshots.findHistory(2L, 7L).get(0).limitAmount());
        assertTrue(snapshots.findHistory(2L, 8L).isEmpty());
        assertEquals(new BigDecimal("300000.75"), snapshots.findHistory(1L, 7L, true).get(1).limitAmount());
        assertEquals(new BigDecimal("400000.00"), snapshots.findHistory(1L, 8L, true).get(1).limitAmount());
        assertEquals(new BigDecimal("900000.00"), snapshots.findHistory(2L, 7L, true).get(1).limitAmount());
        assertTrue(snapshots.findHistory(2L, 8L, true).isEmpty());

        jdbc.update("UPDATE credit_limits SET used_amount = 11111 WHERE id = 11");
        assertEquals(0, snapshots.capture(true));
        assertEquals(0, snapshots.capture(false));
        assertEquals(history, snapshots.findHistory(1L, 7L));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(DISTINCT captured_at) FROM credit_exposure_snapshots", Integer.class));
        assertEquals(4, jdbc.queryForObject("""
                SELECT COUNT(*) FROM credit_exposure_snapshots
                WHERE reporting_date = (captured_at AT TIME ZONE 'UTC')::date
                """, Integer.class));
    }

    @Test
    void capturesNewDaysWithoutFillingHistoricalGaps() {
        // Insert a prior observation as a test fixture, then capture today's real balances.
        jdbc.update("""
                INSERT INTO credit_exposure_snapshots VALUES
                (1, 11, statement_timestamp() - INTERVAL '3 days',
                 (statement_timestamp() AT TIME ZONE 'UTC')::date - 3, 90000, 7500, 0)
                """);
        // The prior observation belongs to institution 1, so institution 2 still gets its baseline.
        assertEquals(1, snapshots.capture(true));
        assertEquals(3, snapshots.capture(false));
        var history = snapshots.findHistory(1L, 7L);
        assertEquals(2, history.size());
        assertEquals(new BigDecimal("90000.00"), history.get(0).limitAmount());
        assertEquals(new BigDecimal("300000.75"), history.get(1).limitAmount());
        assertTrue(Duration.between(history.get(0).capturedAt(), history.get(1).capturedAt()).toDays() >= 3);
    }

    @Test
    void identifiesDemoHistoryBeforeFacilityCreationOnlyForAuthorizedFacilities() {
        jdbc.update("UPDATE credit_limits SET created_at = statement_timestamp() AT TIME ZONE 'Asia/Bahrain' WHERE id = 11");
        jdbc.update("UPDATE credit_requests SET seed_key = 'citi-panel-v1-11-used-1' WHERE id = 1");
        jdbc.update("""
                INSERT INTO credit_exposure_snapshots VALUES
                (1, 11, statement_timestamp() - INTERVAL '2 days',
                 (statement_timestamp() AT TIME ZONE 'UTC')::date - 2, 100000.25, 5000, 2000)
                """);
        assertEquals(4, snapshots.capture(false));
        var history = snapshots.findHistory(1L, 7L);
        assertEquals(2, history.size());
        assertTrue(history.get(0).synthetic());
        assertFalse(history.get(1).synthetic());
        assertFalse(snapshots.findHistory(1L, 8L).get(0).synthetic());
        assertFalse(snapshots.findHistory(2L, 7L).get(0).synthetic());
    }

    @Test
    void parallelCollectorsCommitOneCompleteCapture() throws Exception {
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var baseline = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return snapshots.capture(true);
            });
            var daily = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return snapshots.capture(false);
            });
            start.countDown();
            assertEquals(List.of(0, 4), List.of(
                    baseline.get(10, TimeUnit.SECONDS), daily.get(10, TimeUnit.SECONDS)
            ).stream().sorted().toList());
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM credit_exposure_snapshots", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(DISTINCT captured_at) FROM credit_exposure_snapshots", Integer.class));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void ignoresUncommittedBalancesAndCanRetryAFailedBatch() throws Exception {
        jdbc.execute("ALTER TABLE credit_exposure_snapshots ADD CONSTRAINT test_failed_row CHECK (facility_id <> 12)");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> snapshots.capture(true));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM credit_exposure_snapshots", Integer.class));
        } finally {
            jdbc.execute("ALTER TABLE credit_exposure_snapshots DROP CONSTRAINT test_failed_row");
        }

        var worker = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.update("UPDATE credit_limits SET used_amount = 50000 WHERE id = 11");
                var capture = worker.submit(() -> snapshots.capture(true));
                assertEquals(4, assertDoesNotThrow(() -> capture.get(10, TimeUnit.SECONDS)));
                var trend = worker.submit(() -> snapshots.findHistory(1L, 7L, true));
                var live = assertDoesNotThrow(() -> trend.get(10, TimeUnit.SECONDS)).get(1);
                assertTrue(live.current());
                assertEquals(new BigDecimal("30000.35"), live.usedAmount());
            });
            assertEquals(new BigDecimal("70000.25"), snapshots.findHistory(1L, 7L, true).get(1).usedAmount());
        } finally {
            worker.shutdownNow();
        }
        assertEquals(new BigDecimal("50000.00"), jdbc.queryForObject(
                "SELECT used_amount FROM credit_limits WHERE id = 11", BigDecimal.class));
        assertEquals(new BigDecimal("10000.10"), jdbc.queryForObject(
                "SELECT used_amount FROM credit_exposure_snapshots WHERE facility_id = 11", BigDecimal.class));
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        static DataSource dataSource(String searchSchema) {
            String url = System.getenv("EXPOSURE_TEST_DB_URL");
            if (!searchSchema.isEmpty()) {
                url += (url.contains("?") ? "&" : "?") + "currentSchema=" + searchSchema;
            }
            return new DriverManagerDataSource(url, System.getenv("EXPOSURE_TEST_DB_USER"),
                    System.getenv("EXPOSURE_TEST_DB_PASSWORD"));
        }

        @Bean
        DataSource dataSource() {
            return dataSource(schema);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        CreditExposureSnapshotRepository snapshots(JdbcTemplate jdbc) {
            return new CreditExposureSnapshotRepository(jdbc);
        }
    }
}
