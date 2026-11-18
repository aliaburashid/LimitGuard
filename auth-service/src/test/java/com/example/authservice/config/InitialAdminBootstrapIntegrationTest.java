package com.example.authservice.config;

import com.example.authservice.AuthServiceApplication;
import com.example.authservice.repository.FinancialInstitutionRepository;
import com.example.authservice.repository.UserRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AUTH_BOOTSTRAP_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InitialAdminBootstrapIntegrationTest {
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private MockEnvironment settings;
    private static final String PASSWORD = "Disposable-Test-Only!42";

    @BeforeAll
    void start() {
        String url = System.getenv("AUTH_BOOTSTRAP_TEST_JDBC_URL");
        // Never accept the normal development database, even if supplied accidentally.
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/auth_bootstrap_test")) {
            throw new IllegalArgumentException("An isolated loopback auth_bootstrap_test database is required");
        }
        context = new SpringApplicationBuilder(AuthServiceApplication.class).run(
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=postgres",
                "--spring.datasource.password=" + System.getenv("AUTH_BOOTSTRAP_TEST_DB_PASSWORD"),
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.sql.init.mode=never",
                "--server.address=127.0.0.1", "--server.port=0",
                "--spring.mail.host=127.0.0.1", "--spring.mail.port=1",
                "--jwt-secret=bootstrap-test-only-signing-key-long-enough",
                "--internal-service-key=test-only", "--activity-service.notification-recipient-key=test-only",
                "--limitguard.bootstrap.enabled=false", "--limitguard.seed.demo.enabled=false");
        jdbc = context.getBean(JdbcTemplate.class);
    }

    @BeforeEach
    void emptyDisposableTables() {
        jdbc.update("DELETE FROM public.users");
        jdbc.update("DELETE FROM public.financial_institutions");
        settings = new MockEnvironment()
                .withProperty("AUTH_BOOTSTRAP_INSTITUTION_NAME", "Bootstrap Test Bank")
                .withProperty("AUTH_BOOTSTRAP_ADMIN_FIRST_NAME", "Initial")
                .withProperty("AUTH_BOOTSTRAP_ADMIN_LAST_NAME", "Administrator")
                .withProperty("AUTH_BOOTSTRAP_ADMIN_EMAIL", "admin@example.test")
                .withProperty("AUTH_BOOTSTRAP_ADMIN_PASSWORD", PASSWORD);
    }

    @AfterAll
    void stop() {
        if (context != null) context.close();
    }

    private void bootstrap() {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status ->
                new InitialAdminBootstrap(settings, context.getBean(UserRepository.class),
                        context.getBean(FinancialInstitutionRepository.class),
                        context.getBean(PasswordEncoder.class), jdbc, context.getBean(Validator.class))
                        .run(new DefaultApplicationArguments()));
    }

    private SpringApplicationBuilder enabledApplication() {
        return new SpringApplicationBuilder(AuthServiceApplication.class).properties(java.util.Map.of(
                "AUTH_BOOTSTRAP_INSTITUTION_NAME", "Startup Test Bank",
                "AUTH_BOOTSTRAP_ADMIN_FIRST_NAME", "Initial",
                "AUTH_BOOTSTRAP_ADMIN_LAST_NAME", "Administrator",
                "AUTH_BOOTSTRAP_ADMIN_EMAIL", "startup@example.test",
                "AUTH_BOOTSTRAP_ADMIN_PASSWORD", PASSWORD));
    }

    private String[] enabledArguments() {
        return new String[] {
                "--spring.datasource.url=" + System.getenv("AUTH_BOOTSTRAP_TEST_JDBC_URL"),
                "--spring.datasource.username=postgres",
                "--spring.datasource.password=" + System.getenv("AUTH_BOOTSTRAP_TEST_DB_PASSWORD"),
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.sql.init.mode=never",
                "--server.address=127.0.0.1", "--server.port=0",
                "--spring.mail.host=127.0.0.1", "--spring.mail.port=1",
                "--jwt-secret=bootstrap-test-only-signing-key-long-enough",
                "--internal-service-key=test-only", "--activity-service.notification-recipient-key=test-only",
                "--limitguard.bootstrap.enabled=true", "--limitguard.seed.demo.enabled=false"};
    }

    @Test
    void enabledStartupCompletesBeforeReadyAndRejectsReinitialization() throws Exception {
        AtomicBoolean readyObserved = new AtomicBoolean();
        var app = enabledApplication().listeners(event -> {
            if (event instanceof org.springframework.boot.context.event.ApplicationReadyEvent) {
                readyObserved.set(true);
            }
        });
        try (var bootstrapped = app.run(enabledArguments())) {
            assertThat(readyObserved).isTrue();
            assertThat(bootstrapped.getBeansOfType(InitialAdminBootstrap.class)).hasSize(1);
            int port = Integer.parseInt(bootstrapped.getEnvironment().getProperty("local.server.port"));
            var request = java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://127.0.0.1:" + port + "/api/auth/sso/providers")).GET().build();
            var response = java.net.http.HttpClient.newHttpClient().send(request,
                    java.net.http.HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(200);
        }
        readyObserved.set(false);
        assertThatThrownBy(() -> enabledApplication().listeners(event -> {
            if (event instanceof org.springframework.boot.context.event.ApplicationReadyEvent) {
                readyObserved.set(true);
            }
        }).run(enabledArguments())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires empty");
        assertThat(readyObserved).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(1L);
    }

    @Test
    void disabledByDefault() {
        assertThat(context.getBeansOfType(InitialAdminBootstrap.class)).isEmpty();
        assertThat(context.getBeansOfType(StartupReadinessFilter.class)).isEmpty();
    }

    @Test
    void createsVerifiedActiveAdminAndRejectsRepeatWithoutChangingHash() {
        bootstrap();
        var admin = context.getBean(UserRepository.class).findByEmail("admin@example.test").orElseThrow();
        assertThat(admin.getRole().name()).isEqualTo("ADMIN");
        assertThat(admin.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(admin.isEmailVerified()).isTrue();
        assertThat(context.getBean(PasswordEncoder.class).matches(PASSWORD, admin.getPassword())).isTrue();
        String hash = admin.getPassword();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users u JOIN financial_institutions f ON f.id=u.financial_institution_id WHERE f.status='ACTIVE'", Long.class)).isEqualTo(1L);
        assertThatThrownBy(this::bootstrap).hasMessageContaining("requires empty");
        assertThat(context.getBean(UserRepository.class).findById(admin.getId()).orElseThrow().getPassword()).isEqualTo(hash);
    }

    @Test
    void refusesInstitutionOnlyDatabase() {
        jdbc.update("INSERT INTO financial_institutions(name,status,created_at,updated_at) VALUES ('Existing','ACTIVE',now(),now())");
        assertThatThrownBy(this::bootstrap).hasMessageContaining("requires empty");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "missing-complexity", "", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA!1a"})
    void rejectsInvalidPassword(String password) {
        settings.setProperty("AUTH_BOOTSTRAP_ADMIN_PASSWORD", password);
        assertThatThrownBy(this::bootstrap).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_institutions", Long.class)).isZero();
    }

    @Test
    void rejectsMissingCredentialWithoutEchoingOtherCredentials() {
        settings.getPropertySources().remove("mockProperties");
        assertThatThrownBy(this::bootstrap).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    void rejectsDemoSeeding() {
        settings.setProperty("limitguard.seed.demo.enabled", "true");
        assertThatThrownBy(this::bootstrap).hasMessageContaining("demo seeding");
    }

    @Test
    void rollsBackInstitutionIfUserInsertFails() {
        jdbc.execute("ALTER TABLE users ADD CONSTRAINT bootstrap_test_reject CHECK (email <> 'admin@example.test')");
        try {
            assertThatThrownBy(this::bootstrap).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_institutions", Long.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE users DROP CONSTRAINT bootstrap_test_reject");
        }
    }

    @Test
    void concurrentAttemptsCreateOnlyOneAdministrator() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> task = () -> {
            start.await();
            try { bootstrap(); return true; }
            catch (IllegalStateException expected) { return false; }
        };
        try {
            Future<Boolean> first = pool.submit(task);
            Future<Boolean> second = pool.submit(task);
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS) ^ second.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_institutions", Long.class)).isEqualTo(1L);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void tableLockBlocksOrdinaryConcurrentInsert() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                .executeWithoutResult(status -> {
                    jdbc.execute("LOCK TABLE public.financial_institutions, public.users IN EXCLUSIVE MODE");
                    locked.countDown();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }));
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                    .executeWithoutResult(status -> {
                        jdbc.execute("SET LOCAL lock_timeout='200ms'");
                        jdbc.update("INSERT INTO financial_institutions(name,status,created_at,updated_at) VALUES ('Blocked','ACTIVE',now(),now())");
                    })).isInstanceOf(RuntimeException.class);
        } finally { release.countDown(); holder.get(15, TimeUnit.SECONDS); pool.shutdownNow(); }
    }

    @Test
    void readinessRejectsTrafficUntilStartupCompletes() throws Exception {
        StartupReadinessFilter filter = new StartupReadinessFilter();
        AtomicBoolean reached = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> reached.set(true));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(reached).isFalse();
        filter.onReady();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
    }
}
