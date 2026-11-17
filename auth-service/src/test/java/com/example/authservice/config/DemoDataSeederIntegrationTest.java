package com.example.authservice.config;

import com.example.authservice.AuthServiceApplication;
import com.example.authservice.dto.LoginRequest;
import com.example.authservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "GIB_SEED_TEST_JDBC_URL", matches = ".+")
class DemoDataSeederIntegrationTest {
    private static final List<Account> ACCOUNTS = List.of(
            new Account("gib.noura.almarafi@example.com", "RELATIONSHIP_MANAGER", "GIB_DEMO_RM1_PASSWORD"),
            new Account("gib.yousef.alkindi@example.com", "RELATIONSHIP_MANAGER", "GIB_DEMO_RM2_PASSWORD"),
            new Account("gib.layla.almansoori@example.com", "RISK_OFFICER", "GIB_DEMO_RO1_PASSWORD"),
            new Account("gib.hassan.aldarwish@example.com", "RISK_OFFICER", "GIB_DEMO_RO2_PASSWORD"));

    @Test
    void provisionsGibOnceAndPreservesExistingRowsAcrossRestarts() {
        try (var context = start("--limitguard.seed.demo.enabled=false", "create", "demo")) {
            assertThat(context.getBeansOfType(DemoDataSeeder.class)).isEmpty();
        }
        assertThatThrownBy(() -> start("--limitguard.seed.demo.enabled=true", "validate", "demo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Citi institution ID 5 is required");

        List<Map<String, Object>> originalBanks;
        List<Map<String, Object>> originalUsers;
        try (var context = start("--limitguard.seed.demo.enabled=false", "validate", "demo")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(context.getBeansOfType(DemoDataSeeder.class)).isEmpty();
            jdbc.update("""
                insert into financial_institutions (id, name, status, created_at, updated_at)
                values (5,'Citi','ACTIVE', now(), now()), (13,'Citi Bank','ACTIVE', now(), now())
                """);
            String originalHash = context.getBean(PasswordEncoder.class).encode(UUID.randomUUID().toString());
            jdbc.update("""
                insert into users (email, first_name, last_name, role, status, email_verified,
                    password, financial_institution_id, created_at, updated_at)
                values ('aliaburashid@hotmail.com','Existing','Citi','RELATIONSHIP_MANAGER','ACTIVE',true,?,5,now(),now()),
                       ('sara.alhaddad@example.com','Existing','Citi','RELATIONSHIP_MANAGER','ACTIVE',true,?,5,now(),now()),
                       ('burashid993@gmail.com','Existing','Citi','RISK_OFFICER','ACTIVE',true,?,5,now(),now()),
                       ('fatima.alzayani@example.com','Existing','Citi','RISK_OFFICER','ACTIVE',true,?,5,now(),now())
                """, originalHash, originalHash, originalHash, originalHash);
            originalBanks = jdbc.queryForList("select * from financial_institutions order by id");
            originalUsers = jdbc.queryForList("select * from users order by id");
        }
        try (var context = start("--limitguard.seed.demo.enabled=true", "validate", "default")) {
            assertThat(context.getBeansOfType(DemoDataSeeder.class)).isEmpty();
            assertThat(count(context.getBean(JdbcTemplate.class), "select count(*) from users")).isEqualTo(4);
        }

        List<Map<String, Object>> seededUsers;
        try (var context = start("--limitguard.seed.demo.enabled=true", "validate", "demo")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "select count(*) from financial_institutions where name='Gulf International Bank'"))
                    .isEqualTo(1);
            assertThat(count(jdbc, "select count(*) from financial_institutions")).isEqualTo(3);
            assertThat(count(jdbc, "select count(*) from users")).isEqualTo(8);
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            for (Account account : ACCOUNTS) {
                var row = jdbc.queryForMap("""
                    select u.role, u.status, u.email_verified, u.password, f.name, f.status as institution_status
                    from users u join financial_institutions f on f.id = u.financial_institution_id
                    where u.email = ?
                    """, account.email());
                assertThat(row.get("name")).isEqualTo("Gulf International Bank");
                assertThat(row.get("institution_status")).isEqualTo("ACTIVE");
                assertThat(row.get("role")).isEqualTo(account.role());
                assertThat(row.get("status")).isEqualTo("ACTIVE");
                assertThat(row.get("email_verified")).isEqualTo(true);
                String password = System.getenv(account.passwordVariable());
                String hash = (String) row.get("password");
                assertThat(hash.startsWith("$2")).isTrue();
                assertThat(encoder.matches(password, hash)).isTrue();
                LoginRequest login = new LoginRequest();
                login.setEmail(account.email());
                login.setPassword(password);
                try {
                    assertThat(context.getBean(UserService.class).loginUser(login)).isNotNull();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
            seededUsers = jdbc.queryForList("select * from users order by id");
        }

        try (var context = start("--limitguard.seed.gib.enabled=true", "validate", "demo")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "select count(*) from financial_institutions where name='Gulf International Bank'"))
                    .isEqualTo(1);
            assertThat(jdbc.queryForList("select * from financial_institutions where id in (5,13) order by id"))
                    .isEqualTo(originalBanks);
            assertThat(jdbc.queryForList("select * from users where financial_institution_id=5 order by id")
                    .equals(originalUsers)).isTrue();
            assertThat(jdbc.queryForList("select * from users order by id").equals(seededUsers)).isTrue();
        }
    }

    private ConfigurableApplicationContext start(String optIn, String ddlMode, String profile) {
        String url = System.getenv("GIB_SEED_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/limitguard_gib_seed_test")
                || url.contains(":5432/")) {
            throw new IllegalArgumentException("GIB seed tests require dedicated loopback PostgreSQL");
        }
        return new SpringApplicationBuilder(AuthServiceApplication.class).run(
                "--spring.profiles.active=" + profile,
                optIn,
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=gib_seed_test",
                "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.jpa.hibernate.ddl-auto=" + ddlMode,
                "--spring.sql.init.mode=never",
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--spring.mail.host=127.0.0.1",
                "--spring.mail.port=1",
                "--jwt-secret=auth-gib-seed-test-only-signing-key",
                "--internal-service-key=gib-test-internal-key",
                "--activity-service.notification-recipient-key=gib-test-activity-key");
    }

    private long count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private record Account(String email, String role, String passwordVariable) {}
}
