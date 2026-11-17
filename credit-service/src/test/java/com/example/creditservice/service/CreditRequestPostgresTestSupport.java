package com.example.creditservice.service;

import com.example.creditservice.enums.CreditRequestStatus;
import com.example.creditservice.model.Counterparty;
import com.example.creditservice.model.CreditLimit;
import com.example.creditservice.model.CreditRequest;
import com.example.creditservice.model.CorporateGroup;
import com.example.creditservice.model.CorporateGroupMembership;
import com.example.creditservice.repository.AuditLogRepository;
import com.example.creditservice.repository.CounterpartyRepository;
import com.example.creditservice.repository.CreditLimitRepository;
import com.example.creditservice.repository.CorporateGroupRepository;
import com.example.creditservice.repository.CorporateGroupMembershipRepository;
import com.example.creditservice.repository.GroupCreditLimitRepository;
import com.example.creditservice.repository.CreditRequestRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.creditservice.client.AuthServiceClient;
import com.example.creditservice.dto.AuthContextResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.username=expiry_test",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "limitguard.credit-request-expiry.enabled=false",
        "limitguard.notification-relay.enabled=false",
        "limitguard.exposure-snapshots.enabled=false",
        "jwt-secret=credit-expiry-test-only-signing-key",
        "internal-service-key=credit-expiry-test-only-key"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "CREDIT_EXPIRY_TEST_JDBC_URL", matches = ".+")
abstract class CreditRequestPostgresTestSupport {

    static final long INSTITUTION_ID = 5L;
    static final long REQUESTER_ID = 7L;

    @Autowired MockMvc mvc;
    @MockitoBean AuthServiceClient auth;
    @Autowired CreditRequestService service;
    @Autowired CreditRequestRepository requests;
    @Autowired CreditLimitRepository limits;
    @Autowired CounterpartyRepository counterparties;
    @Autowired AuditLogRepository audits;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired CorporateGroupRepository groups;
    @Autowired CorporateGroupMembershipRepository memberships;
    @Autowired GroupCreditLimitRepository groupLimits;
    @Autowired ObjectMapper json;

    @MockitoBean
    CreditRequestKafkaPublisher kafkaPublisher;

    CreditLimit limit;

    @DynamicPropertySource
    static void useDisposablePostgres(DynamicPropertyRegistry properties) {
        String url = System.getenv("CREDIT_EXPIRY_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/limitguard_expiry_test")
                || url.contains(":5432/")) {
            throw new IllegalArgumentException("Expiry tests require dedicated loopback PostgreSQL");
        }
        properties.add("spring.datasource.url", () -> url);
    }

    @BeforeEach
    void createFacility() {
        Counterparty counterparty = new Counterparty();
        counterparty.setName("Credit integration test " + UUID.randomUUID());

        limit = new CreditLimit();
        limit.setCounterparty(counterparties.save(counterparty));
        limit.setFinancialInstitutionId(INSTITUTION_ID);
        limit.setLimitAmount(new BigDecimal("1000.00"));
        limit.setUsedAmount(new BigDecimal("100.00"));
        limit.setReservedAmount(new BigDecimal("400.00"));
        limit = limits.save(limit);
    }

    String login(long userId, String role, long institutionId) {
        return login(userId, role, institutionId, "ACTIVE");
    }

    String login(long userId, String role, long institutionId, String accountStatus) {
        String jwt = Jwts.builder().id(UUID.randomUUID().toString()).subject("banking-test@example.test")
                .expiration(new Date(System.currentTimeMillis() + 120000))
                .signWith(Keys.hmacShaKeyFor("credit-expiry-test-only-signing-key".getBytes(StandardCharsets.UTF_8)))
                .compact();
        when(auth.getAuthContext(jwt)).thenReturn(new AuthContextResponse(userId, role, accountStatus, institutionId));
        return "Bearer " + jwt;
    }

    ResultActions create(String authorization, CreditLimit facility, int amount) throws Exception {
        String body = "{\"creditLimitId\":" + facility.getId() + ",\"amount\":" + amount + "}";
        return mvc.perform(post("/api/credit-requests").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    long submit(String authorization, CreditLimit facility, int amount, String expectedStatus) throws Exception {
        String body = create(authorization, facility, amount)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("id").asLong();
    }

    ResultActions change(String authorization, long requestId, String action) throws Exception {
        return mvc.perform(patch("/api/credit-requests/{id}/{action}", requestId, action)
                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Credit test decision\"}"));
    }

    void setBalances(String approved, String used, String reserved) {
        limit.setLimitAmount(new BigDecimal(approved));
        limit.setUsedAmount(new BigDecimal(used));
        limit.setReservedAmount(new BigDecimal(reserved));
        limit = limits.save(limit);
    }

    void assertBalances(String used, String reserved) {
        CreditLimit updated = limits.findById(limit.getId()).orElseThrow();
        assertThat(updated.getUsedAmount()).isEqualByComparingTo(used);
        assertThat(updated.getReservedAmount()).isEqualByComparingTo(reserved);
    }

    void addMember(CorporateGroup group, Counterparty counterparty) {
        CorporateGroupMembership membership = new CorporateGroupMembership();
        membership.setCorporateGroup(group);
        membership.setCounterparty(counterparty);
        memberships.save(membership);
    }

    ResultActions updateFacilityLimit(String authorization, String amount) throws Exception {
        return mvc.perform(put("/api/credit-limits/{id}", limit.getId())
                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limitAmount\":" + amount + "}"));
    }

    CreditRequest reservation(LocalDateTime expiresAt) {
        CreditRequest request = new CreditRequest();
        request.setCreditLimit(limit);
        request.setRequesterId(REQUESTER_ID);
        request.setAmount(new BigDecimal("125.00"));
        request.setStatus(CreditRequestStatus.RESERVED);
        request.setExpiresAt(expiresAt);
        return requests.save(request);
    }
}
