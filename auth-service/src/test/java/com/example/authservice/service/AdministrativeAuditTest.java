package com.example.authservice.service;

import com.example.authservice.enums.FinancialInstitutionStatus;
import com.example.authservice.enums.UserRole;
import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.AuthAuditLog;
import com.example.authservice.model.FinancialInstitution;
import com.example.authservice.model.User;
import com.example.authservice.repository.AuthAuditLogRepository;
import com.example.authservice.repository.FinancialInstitutionRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.security.JWTUtils;
import com.example.authservice.security.MyUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_administrative_audit;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "jwt-secret=auth-audit-tests-only-signing-key",
        "jwt-expiration-ms=60000"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AdministrativeAuditTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTUtils jwt;

    @Autowired
    private AuthAuditLogRepository auditLogs;

    @Autowired
    private FinancialInstitutionRepository institutions;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailService emailService;

    private static final String DEACTIVATION_REASON = "Administrative review. " + "Test note. ".repeat(30);

    private User admin;
    private User target;
    private FinancialInstitution affectedInstitution;
    private String newInstitutionName;

    @DynamicPropertySource
    static void useRestoredDatabaseWhenRequested(DynamicPropertyRegistry properties) {
        String url = System.getenv("AUTH_AUDIT_TEST_JDBC_URL");
        if (url == null) {
            return;
        }
        if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/limitguard_audit_test")
                || url.contains(":5432/")) {
            throw new IllegalArgumentException("Audit tests require a dedicated loopback PostgreSQL restore");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.username", () -> "auth_audit_test");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void createFixtures() {
        String suffix = UUID.randomUUID().toString();
        FinancialInstitution adminInstitution = new FinancialInstitution();
        adminInstitution.setName("Administrator institution " + suffix);
        adminInstitution = institutions.save(adminInstitution);

        affectedInstitution = new FinancialInstitution();
        affectedInstitution.setName("Affected institution " + suffix);
        affectedInstitution = institutions.save(affectedInstitution);

        admin = createUser("admin-" + suffix, UserRole.ADMIN, adminInstitution);
        target = createUser("target-" + suffix, UserRole.RELATIONSHIP_MANAGER, affectedInstitution);
        newInstitutionName = "New institution " + suffix;
    }

    @ParameterizedTest
    @EnumSource(AdministrativeAction.class)
    void recordsSuccessfulAdministrativeAction(AdministrativeAction action) throws Exception {
        LocalDateTime before = LocalDateTime.now();
        mvc.perform(requestFor(action).header("Authorization", bearer(admin)))
                .andExpect(status().is(action == AdministrativeAction.FINANCIAL_INSTITUTION_CREATED ? 201 : 200));

        assertThat(auditRecords()).singleElement().satisfies(audit -> {
            assertThat(audit.getActorId()).isEqualTo(admin.getId());
            assertThat(audit.getAction()).isEqualTo(action.name());
            assertThat(audit.getCreatedAt()).isBetween(before.minusNanos(1000), LocalDateTime.now().plusNanos(1000));
            assertSuccessfulChange(action, audit);
        });
    }

    @ParameterizedTest
    @EnumSource(AdministrativeAction.class)
    void rejectsUnauthorizedOperationsWithoutAuditing(AdministrativeAction action) throws Exception {
        mvc.perform(requestFor(action)).andExpect(status().isUnauthorized());
        mvc.perform(requestFor(action).header("Authorization", bearer(target))).andExpect(status().isForbidden());

        assertNoChange();
    }

    @ParameterizedTest
    @EnumSource(AdministrativeAction.class)
    void rollsBackAdministrativeChangeWhenAuditInsertFails(AdministrativeAction action) {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_reject_audit CHECK (actor_id IS DISTINCT FROM "
                + admin.getId() + ")");
        try {
            assertThatThrownBy(() -> mvc.perform(requestFor(action).header("Authorization", bearer(admin))))
                    .hasRootCauseInstanceOf(java.sql.SQLException.class)
                    .hasCauseInstanceOf(DataIntegrityViolationException.class);
            assertNoChange();
        } finally {
            jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT test_reject_audit");
        }
    }

    private void assertSuccessfulChange(AdministrativeAction action, AuthAuditLog audit) {
        switch (action) {
            case USER_DEACTIVATED -> {
                assertTarget(audit, "USER", target.getId(), affectedInstitution.getId());
                assertThat(audit.getDetails()).contains(DEACTIVATION_REASON, "ACTIVE -> DEACTIVATED");
                assertThat(users.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.DEACTIVATED);
            }
            case USER_ROLE_UPDATED -> {
                assertTarget(audit, "USER", target.getId(), affectedInstitution.getId());
                assertThat(audit.getDetails()).contains("RISK_OFFICER", "RELATIONSHIP_MANAGER");
                assertThat(users.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.RISK_OFFICER);
            }
            case FINANCIAL_INSTITUTION_CREATED -> {
                FinancialInstitution created = institutions.findById(audit.getEntityId()).orElseThrow();
                assertTarget(audit, "FINANCIAL_INSTITUTION", created.getId(), created.getId());
                assertThat(audit.getDetails()).contains(newInstitutionName, "ACTIVE");
                assertThat(created.getName()).isEqualTo(newInstitutionName);
            }
            case FINANCIAL_INSTITUTION_UPDATED -> {
                Long institutionId = affectedInstitution.getId();
                assertTarget(audit, "FINANCIAL_INSTITUTION", institutionId, institutionId);
                assertThat(audit.getDetails()).contains(affectedInstitution.getName(), newInstitutionName,
                        "ACTIVE -> INACTIVE");
                FinancialInstitution updated = institutions.findById(institutionId).orElseThrow();
                assertThat(updated.getName()).isEqualTo(newInstitutionName);
                assertThat(updated.getStatus()).isEqualTo(FinancialInstitutionStatus.INACTIVE);
            }
        }
    }

    private void assertTarget(AuthAuditLog audit, String type, Long id, Long institutionId) {
        assertThat(audit.getEntityType()).isEqualTo(type);
        assertThat(audit.getEntityId()).isEqualTo(id);
        assertThat(audit.getFinancialInstitutionId()).isEqualTo(institutionId);
        assertThat(jdbc.queryForList("""
                SELECT id FROM audit_logs WHERE actor_id = ? AND financial_institution_id = ?
                """, Long.class, admin.getId(), institutionId)).containsExactly(audit.getId());
        assertThat(jdbc.queryForList("""
                SELECT id FROM audit_logs WHERE actor_id = ? AND financial_institution_id = ?
                """, Long.class, admin.getId(), admin.getFinancialInstitution().getId())).isEmpty();
    }

    private List<AuthAuditLog> auditRecords() {
        return auditLogs.findAll().stream()
                .filter(audit -> admin.getId().equals(audit.getActorId()) || target.getId().equals(audit.getActorId()))
                .toList();
    }

    private void assertNoChange() {
        assertThat(auditRecords()).isEmpty();
        User unchanged = users.findById(target.getId()).orElseThrow();
        assertThat(unchanged.getRole()).isEqualTo(UserRole.RELATIONSHIP_MANAGER);
        assertThat(unchanged.getStatus()).isEqualTo(UserStatus.ACTIVE);
        FinancialInstitution unchangedInstitution = institutions.findById(affectedInstitution.getId()).orElseThrow();
        assertThat(unchangedInstitution.getName()).isEqualTo(affectedInstitution.getName());
        assertThat(unchangedInstitution.getStatus()).isEqualTo(FinancialInstitutionStatus.ACTIVE);
        assertThat(institutions.existsByName(newInstitutionName)).isFalse();
    }

    private MockHttpServletRequestBuilder requestFor(AdministrativeAction action) {
        String userPath = "/api/auth/users/" + target.getId();
        String institutionPath = "/api/financial-institutions";
        return switch (action) {
            case USER_DEACTIVATED -> request(HttpMethod.PATCH, userPath + "/deactivate")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + DEACTIVATION_REASON + "\"}");
            case USER_ROLE_UPDATED -> request(HttpMethod.PATCH, userPath + "/role")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"RISK_OFFICER\"}");
            case FINANCIAL_INSTITUTION_CREATED -> request(HttpMethod.POST, institutionPath)
                    .contentType(MediaType.APPLICATION_JSON).content(institutionBody("ACTIVE"));
            case FINANCIAL_INSTITUTION_UPDATED -> request(HttpMethod.PUT, institutionPath + "/" + affectedInstitution.getId())
                    .contentType(MediaType.APPLICATION_JSON).content(institutionBody("INACTIVE"));
        };
    }

    private String institutionBody(String status) {
        return """
                {"name":"%s","status":"%s"}
                """.formatted(newInstitutionName, status);
    }

    private User createUser(String name, UserRole role, FinancialInstitution institution) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(name + "@example.test");
        user.setPassword("unused-test-value");
        user.setEmailVerified(true);
        user.setRole(role);
        user.setFinancialInstitution(institution);
        return users.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwt.generateJwtToken(new MyUserDetails(user));
    }

    private enum AdministrativeAction {
        USER_DEACTIVATED,
        USER_ROLE_UPDATED,
        FINANCIAL_INSTITUTION_CREATED,
        FINANCIAL_INSTITUTION_UPDATED
    }
}
