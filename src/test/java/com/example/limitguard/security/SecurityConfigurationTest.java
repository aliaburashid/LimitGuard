package com.example.limitguard.security;

import com.example.limitguard.dto.CreditExposureSnapshotResponse;
import com.example.limitguard.enums.UserRole;
import com.example.limitguard.enums.UserStatus;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.CounterpartyRepository;
import com.example.limitguard.repository.CreditExposureSnapshotRepository;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.springframework.security.test.context.support.WithMockUser;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreditExposureSnapshotRepository exposureSnapshots;

    @Autowired
    private FinancialInstitutionRepository institutions;

    @Autowired
    private CounterpartyRepository counterparties;

    @Autowired
    private CreditLimitRepository facilities;

    @Test
    @Transactional
    void facilityDirectoryPaginatesOnlyTheSignedInInstitutionWithoutRequestHistory() throws Exception {
        FinancialInstitution citi = new FinancialInstitution();
        citi.setName("Directory test Citi");
        citi = institutions.save(citi);
        FinancialInstitution other = new FinancialInstitution();
        other.setName("Directory test other institution");
        other = institutions.save(other);

        CreditLimit first = directoryFacility(citi, "First funded counterparty");
        CreditLimit second = directoryFacility(citi, "Second funded counterparty");
        directoryFacility(other, "Other institution counterparty");

        mockMvc.perform(get("/api/credit-limits").param("size", "1")
                        .param("institutionId", other.getId().toString())
                        .with(user(historyUser(UserRole.RELATIONSHIP_MANAGER, citi.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].exposure.creditLimitId").value(first.getId().intValue()))
                .andExpect(jsonPath("$.content[0].exposure.availableHeadroom").value(250.0))
                .andExpect(jsonPath("$.content[0].name").value("First funded counterparty"));

        mockMvc.perform(get("/api/credit-limits").param("size", "1").param("page", "1")
                        .with(user(historyUser(UserRole.RELATIONSHIP_MANAGER, citi.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].exposure.creditLimitId").value(second.getId().intValue()));
    }

    @Test
    void facilityDirectoryRequiresAuthenticationAndAnInstitution() throws Exception {
        mockMvc.perform(get("/api/credit-limits"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/credit-limits")
                        .with(user(historyUser(UserRole.RELATIONSHIP_MANAGER, null))))
                .andExpect(status().isForbidden());
    }

    private CreditLimit directoryFacility(FinancialInstitution institution, String name) {
        Counterparty company = new Counterparty();
        company.setName(name);
        CreditLimit limit = new CreditLimit();
        limit.setCounterparty(counterparties.save(company));
        limit.setFinancialInstitution(institution);
        limit.setLimitAmount(new BigDecimal("500.00"));
        limit.setUsedAmount(new BigDecimal("200.00"));
        limit.setReservedAmount(new BigDecimal("50.00"));
        return facilities.save(limit);
    }

    @Test
    void profilePictureRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/users/profile-picture"))
                .andExpect(status().isUnauthorized());
    }

    // Test 1: A protected endpoint must reject requests without a JWT
    @Test
    void shouldReturn401WhenJwtIsMissing() throws Exception {

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // Test 2: A protected endpoint must reject an invalid JWT
    @Test
    void shouldReturn401WhenJwtIsInvalid() throws Exception {

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }


    // Read the test-only JWT secret from application-test.properties
    @Value("${jwt-secret}")
    private String jwtSecret;

    // Test 3: An expired JWT must return HTTP 401
    @Test
    void shouldReturn401WhenJwtIsExpired() throws Exception {

        // Create the signing key using our test secret
        SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );

        // Set both timestamps in the past
        Date issuedAt = new Date(System.currentTimeMillis() - 120000);
        Date expiredAt = new Date(System.currentTimeMillis() - 60000);

        // Create a correctly signed JWT that has already expired
        String expiredToken = Jwts.builder()
                .subject("test@example.com")
                .issuedAt(issuedAt)
                .expiration(expiredAt)
                .signWith(key)
                .compact();

        // Send the expired JWT to a protected endpoint
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }


    // Test 4: An authenticated user without ADMIN permission gets HTTP 403
    @Test
    @WithMockUser(
            username = "manager@example.com",
            roles = {"RELATIONSHIP_MANAGER"}
    )
    void shouldReturn403WhenUserHasInsufficientPermissions() throws Exception {

        // A RELATIONSHIP_MANAGER is authenticated but is not an ADMIN
        // Only ADMIN users can create financial institutions
        mockMvc.perform(post("/api/financial-institutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "LG84 Authorization Test",
                                    "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden());
    }


    @Test
    void shouldReturnEmptyExposureHistoryForRelationshipManager() throws Exception {
        mockMvc.perform(get("/api/credit-limits/exposure-history")
                        .with(user(historyUser(UserRole.RELATIONSHIP_MANAGER, 3L))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verify(exposureSnapshots).findHistory(3L, 7L);
    }

    @Test
    void shouldReturnDatedExposureHistoryInTheChartFormat() throws Exception {
        when(exposureSnapshots.findHistory(3L, 7L)).thenReturn(List.of(
                new CreditExposureSnapshotResponse(
                        Instant.parse("2026-11-07T00:00:00Z"), new BigDecimal("100000.25"),
                        new BigDecimal("10000.10"), new BigDecimal("1000.20"), true
                )
        ));

        mockMvc.perform(get("/api/credit-limits/exposure-history")
                        .with(user(historyUser(UserRole.RELATIONSHIP_MANAGER, 3L))))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{
                            "capturedAt": "2026-11-07T00:00:00Z",
                            "limitAmount": 100000.25,
                            "usedAmount": 10000.10,
                            "reservedAmount": 1000.20,
                            "synthetic": true
                        }]
                        """));
    }

    @Test
    void shouldReturnCurrentReadingOnlyWhenRequested() throws Exception {
        when(exposureSnapshots.findHistory(3L, 7L, true)).thenReturn(List.of(
                new CreditExposureSnapshotResponse(Instant.parse("2026-11-07T12:00:00Z"),
                        new BigDecimal("100000"), new BigDecimal("10000"), new BigDecimal("5000"), false, true)
        ));

        mockMvc.perform(get("/api/credit-limits/exposure-history")
                        .param("includeCurrent", "true")
                        .with(user(historyUser(UserRole.RELATIONSHIP_MANAGER, 3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].reservedAmount").value(5000));
    }

    @Test
    void shouldRequireAuthenticationForExposureHistory() throws Exception {
        mockMvc.perform(get("/api/credit-limits/exposure-history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectOtherRolesFromRelationshipManagerHistory() throws Exception {
        mockMvc.perform(get("/api/credit-limits/exposure-history")
                        .with(user(historyUser(UserRole.RISK_OFFICER, 3L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRequireAnInstitutionForExposureHistory() throws Exception {
        mockMvc.perform(get("/api/credit-limits/exposure-history")
                        .with(user(historyUser(UserRole.RELATIONSHIP_MANAGER, null))))
                .andExpect(status().isForbidden());
    }

    private MyUserDetails historyUser(UserRole role, Long institutionId) {
        User account = new User();
        account.setId(7L);
        account.setEmail("employee@example.com");
        account.setRole(role);
        account.setStatus(UserStatus.ACTIVE);
        if (institutionId != null) {
            FinancialInstitution institution = new FinancialInstitution();
            institution.setId(institutionId);
            account.setFinancialInstitution(institution);
        }
        return new MyUserDetails(account);
    }
}
