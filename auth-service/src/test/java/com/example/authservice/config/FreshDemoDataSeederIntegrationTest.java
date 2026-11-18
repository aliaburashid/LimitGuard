package com.example.authservice.config;

import com.example.authservice.AuthServiceApplication;
import com.example.authservice.dto.LoginRequest;
import com.example.authservice.service.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AUTH_DEMO_TEST_JDBC_URL", matches = ".+")
class FreshDemoDataSeederIntegrationTest {
    private static final String PASSWORD = "Disposable-Demo-Only!42";
    private static final List<String> PASSWORD_VARIABLES = List.of(
            "CITI_DEMO_RM1_PASSWORD", "CITI_DEMO_RM2_PASSWORD",
            "CITI_DEMO_RO1_PASSWORD", "CITI_DEMO_RO2_PASSWORD",
            "GIB_DEMO_RM1_PASSWORD", "GIB_DEMO_RM2_PASSWORD",
            "GIB_DEMO_RO1_PASSWORD", "GIB_DEMO_RO2_PASSWORD");

    private ConfigurableApplicationContext start(boolean seed, boolean fresh, String profiles,
            Map<String, Object> overrides) {
        String url = System.getenv("AUTH_DEMO_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/limitguard_docker_demo_auth_test")) {
            throw new IllegalArgumentException("Use an exclusive disposable Docker demo test database");
        }
        Map<String, Object> settings = new HashMap<>();
        PASSWORD_VARIABLES.forEach(key -> settings.put(key, PASSWORD));
        settings.putAll(overrides);
        return new SpringApplicationBuilder(AuthServiceApplication.class).properties(settings).run(
                "--spring.profiles.active=" + profiles,
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=postgres",
                "--spring.datasource.password=" + System.getenv("AUTH_DEMO_TEST_DB_PASSWORD"),
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.sql.init.mode=never",
                "--server.address=127.0.0.1", "--server.port=0",
                "--spring.mail.host=127.0.0.1", "--spring.mail.port=1",
                "--jwt-secret=demo-test-only-signing-key-long-enough",
                "--internal-service-key=test-only", "--activity-service.notification-recipient-key=test-only",
                "--limitguard.bootstrap.enabled=false",
                "--limitguard.seed.demo.enabled=" + seed,
                "--limitguard.seed.fresh.enabled=" + fresh);
    }

    @BeforeEach
    void cleanOnlyDisposableDatabase() {
        try (var context = start(false, false, "default", Map.of())) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("DELETE FROM users");
            jdbc.update("DELETE FROM financial_institutions");
        }
    }

    @Test
    void freshSeedCreatesEightVerifiedAccountsAndRestartPreservesEveryRow() {
        List<Map<String, Object>> users;
        List<Map<String, Object>> institutions;
        try (var context = start(true, true, "demo,docker-demo", Map.of())) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(8L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_institutions", Long.class)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("SELECT name FROM financial_institutions WHERE id=5", String.class)).isEqualTo("Citi");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE status='ACTIVE' AND email_verified", Long.class)).isEqualTo(8L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role='RELATIONSHIP_MANAGER'", Long.class)).isEqualTo(4L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role='RISK_OFFICER'", Long.class)).isEqualTo(4L);
            for (var row : jdbc.queryForList("SELECT email,password FROM users")) {
                assertThat(context.getBean(PasswordEncoder.class).matches(PASSWORD, (String) row.get("password"))).isTrue();
                LoginRequest login = new LoginRequest();
                login.setEmail((String) row.get("email"));
                login.setPassword(PASSWORD);
                try { assertThat(context.getBean(UserService.class).loginUser(login)).isNotNull(); }
                finally { SecurityContextHolder.clearContext(); }
            }
            for (String table : List.of("email_verification_codes", "notification_outbox", "credit_request_email_deliveries", "audit_logs")) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).isZero();
            }
            users = jdbc.queryForList("SELECT * FROM users ORDER BY id");
            institutions = jdbc.queryForList("SELECT * FROM financial_institutions ORDER BY id");
        }
        // Passwords are needed only for missing accounts, never for restarting seeded data.
        Map<String, Object> blankPasswords = new HashMap<>();
        PASSWORD_VARIABLES.forEach(key -> blankPasswords.put(key, ""));
        try (var context = start(true, true, "demo,docker-demo", blankPasswords)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(users);
            assertThat(jdbc.queryForList("SELECT * FROM financial_institutions ORDER BY id")).isEqualTo(institutions);
            Long id = jdbc.queryForObject("INSERT INTO financial_institutions(name,status,created_at,updated_at) VALUES ('Sequence check','ACTIVE',now(),now()) RETURNING id", Long.class);
            assertThat(id).isGreaterThan(5L);
            assertThat(institutions.stream().noneMatch(row -> row.get("id").equals(id))).isTrue();
        }
    }

    @Test
    void normalDemoModeStillRequiresExistingCiti() {
        assertThatThrownBy(() -> start(true, false, "demo", Map.of()))
                .hasMessageContaining("Citi institution ID 5 is required");
    }

    @Test
    void freshFlagRequiresDockerDemoProfile() {
        assertThatThrownBy(() -> start(true, true, "demo", Map.of()))
                .hasMessageContaining("docker-demo");
    }

    @Test
    void missingPasswordRollsBackBothInstitutionsAndUsers() {
        assertThatThrownBy(() -> start(true, true, "demo,docker-demo", Map.of("GIB_DEMO_RO2_PASSWORD", "")))
                .isInstanceOf(IllegalStateException.class);
        try (var context = start(false, false, "default", Map.of())) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_institutions", Long.class)).isZero();
        }
    }

    @Test
    void unrelatedExistingInstitutionIsNeverReplaced() {
        try (var context = start(false, false, "default", Map.of())) {
            context.getBean(JdbcTemplate.class).update("INSERT INTO financial_institutions(id,name,status,created_at,updated_at) VALUES (5,'Unrelated','ACTIVE',now(),now())");
        }
        assertThatThrownBy(() -> start(true, true, "demo,docker-demo", Map.of()))
                .hasMessageContaining("Institution ID 5 must be active Citi");
        try (var context = start(false, false, "default", Map.of())) {
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT name FROM financial_institutions WHERE id=5", String.class)).isEqualTo("Unrelated");
        }
    }

    @Test
    void disabledFlagAndProductionProfileDoNotSeed() {
        try (var context = start(false, true, "demo,docker-demo", Map.of())) {
            assertThat(context.getBeansOfType(DemoDataSeeder.class)).isEmpty();
        }
        try (var context = start(true, true, "demo,docker-demo,production", Map.of())) {
            assertThat(context.getBeansOfType(DemoDataSeeder.class)).isEmpty();
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
        }
    }
}
