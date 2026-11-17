package com.example.creditservice.service;

import com.example.creditservice.security.AuthenticatedCreditUser;
import com.example.creditservice.dto.CounterpartyStatusRequest;
import com.example.creditservice.dto.UpdateCreditLimitRequest;
import com.example.creditservice.dto.UpdateGroupCreditLimitRequest;
import com.example.creditservice.enums.CounterpartyStatus;
import com.example.creditservice.enums.CreditRequestStatus;
import com.example.creditservice.model.CreditLimit;
import com.example.creditservice.model.CreditRequest;
import com.example.creditservice.model.CorporateGroup;
import com.example.creditservice.model.GroupCreditLimit;
import com.example.creditservice.repository.CreditExposureSnapshotRepository;
import com.example.creditservice.repository.NotificationOutboxRepository;
import com.example.creditservice.scheduler.CreditRequestScheduler;
import com.example.creditservice.scheduler.NotificationOutboxRelay;
import com.example.creditservice.model.NotificationOutbox;
import org.springframework.kafka.support.SendResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.creditservice.dto.AuthContextResponse;
import com.example.creditservice.dto.InternalUserDisplayResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditRequestExpiryTest extends CreditRequestPostgresTestSupport {

    @Autowired private CounterpartyService counterpartyService;
    @Autowired private CreditLimitService creditLimitService;
    @Autowired private GroupCreditLimitService groupCreditLimitService;
    @Autowired private NotificationOutboxRepository outbox;
    @Autowired private CreditExposureSnapshotRepository snapshots;

    @Test
    void schedulerExpiresOnlyDueReservationsAndCommitsFinancialAndEventChanges() {
        CreditRequest due = reservation(LocalDateTime.now().minusMinutes(1));
        CreditRequest future = reservation(LocalDateTime.now().plusHours(1));
        CreditRequestScheduler scheduler = new CreditRequestScheduler(requests, service);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        scheduler.expireUnusedReservations();
        scheduler.expireUnusedReservations();

        assertThat(requests.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(CreditRequestStatus.EXPIRED);
        assertThat(requests.findById(future.getId()).orElseThrow().getStatus()).isEqualTo(CreditRequestStatus.RESERVED);
        CreditLimit updated = limits.findById(limit.getId()).orElseThrow();
        assertThat(updated.getUsedAmount()).isEqualByComparingTo("100.00");
        assertThat(updated.getReservedAmount()).isEqualByComparingTo("275.00");
        assertThat(updated.getLimitAmount().subtract(updated.getUsedAmount()).subtract(updated.getReservedAmount()))
                .isEqualByComparingTo("625.00");
        assertThat(audits.findAll().stream().filter(row -> "CREDIT_REQUEST".equals(row.getEntityType())
                        && due.getId().equals(row.getEntityId())).toList())
                .singleElement().satisfies(row -> {
                    assertThat(row.getAction()).isEqualTo("CREDIT_REQUEST_EXPIRED");
                    assertThat(row.getActorId()).isNull();
                    assertThat(row.getFinancialInstitutionId()).isEqualTo(INSTITUTION_ID);
                });
        assertThat(outbox.findAll().stream().filter(row -> due.getId().equals(row.getCreditRequestId())).toList())
                .singleElement().satisfies(row -> {
                    assertThat(row.getStatus()).isEqualTo("EXPIRED");
                    assertThat(row.getEventType()).isEqualTo("CREDIT_REQUEST_EXPIRED");
                    assertThat(row.getFinancialInstitutionId()).isEqualTo(INSTITUTION_ID);
                    assertThat(row.getRequesterId()).isEqualTo(REQUESTER_ID);
                });
    }

    @Test
    void outboxInsertFailureRollsBackStatusBalanceAndAudit() {
        CreditRequest due = reservation(LocalDateTime.now().minusMinutes(1));
        jdbc.execute("ALTER TABLE notification_outbox ADD CONSTRAINT test_reject_expiry CHECK (credit_request_id <> "
                + due.getId() + ")");
        try {
            assertThatThrownBy(() -> service.expireCreditRequest(due.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("ALTER TABLE notification_outbox DROP CONSTRAINT test_reject_expiry");
        }

        assertThat(requests.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(CreditRequestStatus.RESERVED);
        assertThat(limits.findById(limit.getId()).orElseThrow().getReservedAmount()).isEqualByComparingTo("400.00");
        assertThat(audits.findAll()).noneMatch(row -> "CREDIT_REQUEST".equals(row.getEntityType())
                        && due.getId().equals(row.getEntityId()));
        assertThat(outbox.findAll()).noneMatch(row -> due.getId().equals(row.getCreditRequestId()));
    }

    @Test
    void commitsBeforeDisplayFailureAndStillEnforcesAuthorization() throws Exception {
        String jwt = Jwts.builder().subject("expiry-test@example.test")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor("credit-expiry-test-only-signing-key".getBytes(StandardCharsets.UTF_8)))
                .compact();
        String authorization = "Bearer " + jwt;
        String body = "{\"creditLimitId\":" + limit.getId() + ",\"amount\":125}";
        when(auth.getAuthContext(jwt)).thenThrow(
                new ResourceAccessException("Connection refused"),
                new ResourceAccessException("Read timeout", new java.net.SocketTimeoutException()),
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        for (int attempt = 0; attempt < 3; attempt++) {
            mvc.perform(post("/api/credit-requests").header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Service Unavailable"))
                    .andExpect(jsonPath("$.message").value("Authentication service is temporarily unavailable"));
        }
        String expiredJwt = Jwts.builder().subject("expiry-test@example.test")
                .expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor("credit-expiry-test-only-signing-key".getBytes(StandardCharsets.UTF_8)))
                .compact();
        for (String authHeader : List.of(authorization, "Bearer invalid", "Bearer " + expiredJwt)) {
            mvc.perform(post("/api/credit-requests").header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/credit-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(requests.findAll()).noneMatch(request -> limit.getId().equals(request.getCreditLimit().getId()));
        assertBalances("100.00", "400.00");
        doReturn(new AuthContextResponse(REQUESTER_ID, "RELATIONSHIP_MANAGER", "ACTIVE", 9L))
                .when(auth).getAuthContext(jwt);
        mvc.perform(post("/api/credit-requests").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(limits.findById(limit.getId()).orElseThrow().getReservedAmount()).isEqualByComparingTo("400.00");
        doReturn(new AuthContextResponse(REQUESTER_ID, "RELATIONSHIP_MANAGER", "ACTIVE", INSTITUTION_ID))
                .when(auth).getAuthContext(jwt);
        when(auth.getUserDisplay(REQUESTER_ID, jwt)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(limits.findById(limit.getId()).orElseThrow().getReservedAmount()).isEqualByComparingTo("525.00");
            throw new ResourceAccessException("Display unavailable");
        });
        mvc.perform(post("/api/credit-requests").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.requesterId").value(REQUESTER_ID))
                .andExpect(jsonPath("$.financialInstitutionId").value(INSTITUTION_ID))
                .andExpect(jsonPath("$.requesterName").value("Unavailable"))
                .andExpect(jsonPath("$.financialInstitutionName").value("Unavailable"));
    }

    @Test
    void deduplicatesPageLookupsAndRejectsMismatchedInstitutionNames() throws Exception {
        String jwt = Jwts.builder().subject("display-test@example.test")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor("credit-expiry-test-only-signing-key".getBytes(StandardCharsets.UTF_8)))
                .compact();
        reservation(LocalDateTime.now().plusHours(1));
        reservation(LocalDateTime.now().plusHours(1));
        when(auth.getAuthContext(jwt)).thenReturn(
                new AuthContextResponse(REQUESTER_ID, "RELATIONSHIP_MANAGER", "ACTIVE", INSTITUTION_ID));
        when(auth.getUserDisplay(REQUESTER_ID, jwt)).thenReturn(
                new InternalUserDisplayResponse(REQUESTER_ID, "Test Requester", 9L, "Other Institution"));
        mvc.perform(get("/api/credit-requests/my").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requesterName").value("Test Requester"))
                .andExpect(jsonPath("$.content[0].financialInstitutionId").value(INSTITUTION_ID))
                .andExpect(jsonPath("$.content[0].financialInstitutionName").value("Unavailable"))
                .andExpect(jsonPath("$.content[1].financialInstitutionName").value("Unavailable"));
        verify(auth, times(1)).getUserDisplay(REQUESTER_ID, jwt);
    }

    @Test
    void lifecyclePreservesExposureAndEnforcesMakerCheckerAndRequestOwnership() throws Exception {
        setBalances("2000000", "100000", "0");
        String requester = login(7L, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String otherManager = login(8L, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String requesterAsChecker = login(7L, "RISK_OFFICER", INSTITUTION_ID);
        String checker = login(9L, "RISK_OFFICER", INSTITUTION_ID);
        String outsideChecker = login(10L, "RISK_OFFICER", 9L);
        String inactive = login(11L, "RELATIONSHIP_MANAGER", INSTITUTION_ID, "DEACTIVATED");

        long approved = submit(requester, limit, 600000, "PENDING_APPROVAL");
        assertBalances("100000", "0");
        mvc.perform(get("/api/credit-requests/{id}", approved).header("Authorization", inactive))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/credit-requests/{id}", approved).header("Authorization", otherManager))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/credit-requests/{id}", approved).header("Authorization", outsideChecker))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/credit-requests/{id}", approved).header("Authorization", checker))
                .andExpect(status().isOk());
        change(requester, approved, "approve").andExpect(status().isForbidden());
        change(requesterAsChecker, approved, "reject").andExpect(status().isForbidden());
        change(requesterAsChecker, approved, "approve").andExpect(status().isForbidden());
        change(outsideChecker, approved, "reject").andExpect(status().isForbidden());
        assertThat(requests.findById(approved).orElseThrow().getStatus())
                .isEqualTo(CreditRequestStatus.PENDING_APPROVAL);
        assertBalances("100000", "0");

        change(checker, approved, "approve").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
        assertBalances("100000", "600000");
        change(otherManager, approved, "use").andExpect(status().isForbidden());
        change(otherManager, approved, "cancel").andExpect(status().isForbidden());
        change(requester, approved, "use").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"));
        assertBalances("700000", "0");
        change(requester, approved, "use").andExpect(status().isBadRequest());
        assertBalances("700000", "0");

        long rejected = submit(requester, limit, 600000, "PENDING_APPROVAL");
        change(checker, rejected, "reject").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertBalances("700000", "0");

        long cancelled = submit(requester, limit, 125000, "RESERVED");
        assertBalances("700000", "125000");
        change(requester, cancelled, "cancel").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertBalances("700000", "0");
    }

    @Test
    void exposureHistoryPreservesSnapshotsAndReturnsOnlyAuthorizedCurrentBalances() throws Exception {
        // Hibernate does not create the snapshot table because it is accessed through JDBC.
        new ResourceDatabasePopulator(new ClassPathResource("db/exposure-snapshots.sql"))
                .execute(jdbc.getDataSource());
        setBalances("1000000", "100000", "0");
        // Separate requester IDs keep this scenario independent of other tests.
        String requester = login(70L, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String otherManager = login(71L, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String outsideManager = login(70L, "RELATIONSHIP_MANAGER", 9L);
        String checker = login(9L, "RISK_OFFICER", INSTITUTION_ID);

        long requestId = submit(requester, limit, 125000, "RESERVED");
        snapshots.capture(false);
        change(requester, requestId, "use").andExpect(status().isOk());
        mvc.perform(get("/api/credit-limits/{id}/exposure", limit.getId()).header("Authorization", requester))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedAmount").value(225000))
                .andExpect(jsonPath("$.reservedAmount").value(0))
                .andExpect(jsonPath("$.availableHeadroom").value(775000));
        mvc.perform(get("/api/credit-limits/{id}/exposure", limit.getId()).header("Authorization", outsideManager))
                .andExpect(status().isForbidden());

        String history = "/api/credit-limits/exposure-history";
        mvc.perform(get(history).header("Authorization", requester))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].usedAmount").value(100000))
                .andExpect(jsonPath("$[0].reservedAmount").value(125000))
                .andExpect(jsonPath("$[0].capturedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].synthetic").value(false))
                .andExpect(jsonPath("$[0].current").doesNotExist());
        mvc.perform(get(history).param("includeCurrent", "true").header("Authorization", requester))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reservedAmount").value(125000))
                .andExpect(jsonPath("$[1].limitAmount").value(1000000))
                .andExpect(jsonPath("$[1].usedAmount").value(225000))
                .andExpect(jsonPath("$[1].reservedAmount").value(0))
                .andExpect(jsonPath("$[1].current").value(true));
        mvc.perform(get(history).param("includeCurrent", "true").header("Authorization", outsideManager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(history).param("includeCurrent", "true").header("Authorization", otherManager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(history).header("Authorization", checker))
                .andExpect(status().isForbidden());
    }

    @Test
    void committedCounterpartyUpdateQueuesEventForTheActingInstitution() throws Exception {
        Long counterpartyId = limit.getCounterparty().getId();
        String checker = login(82L, "RISK_OFFICER", INSTITUTION_ID);

        mvc.perform(patch("/api/counterparties/{id}/status", counterpartyId)
                        .header("Authorization", checker).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN"));

        assertThat(counterparties.findById(counterpartyId).orElseThrow().getStatus().name()).isEqualTo("FROZEN");
        assertThat(audits.findAll()).filteredOn(row -> "COUNTERPARTY_STATUS_UPDATED".equals(row.getAction())
                && counterpartyId.equals(row.getEntityId())).singleElement().satisfies(row ->
                assertThat(row.getFinancialInstitutionId()).isEqualTo(INSTITUTION_ID));
        assertThat(outbox.findAll()).filteredOn(row -> counterpartyId.equals(row.getCounterpartyId()))
                .singleElement().satisfies(row -> {
                    assertThat(row.getFinancialInstitutionId()).isEqualTo(INSTITUTION_ID);
                    assertThat(row.getEventType()).isEqualTo("COUNTERPARTY_STATUS_UPDATED");
                    assertThat(row.getStatus()).isEqualTo("FROZEN");
                    assertThat(row.getCreditRequestId()).isNull();
                    assertThat(row.getRequesterId()).isNull();
                    assertThat(row.getPublishedAt()).isNull();
                });
    }

    @Test
    void outboxInsertFailureRollsBackCounterpartyUpdateAndAudit() {
        Long counterpartyId = limit.getCounterparty().getId();
        var actor = new AuthenticatedCreditUser(
                82L, "counterparty-test@example.test", "RISK_OFFICER", INSTITUTION_ID);
        var request = new CounterpartyStatusRequest();
        request.setStatus(CounterpartyStatus.FROZEN);

        // Force the outbox insert to fail inside the real update transaction.
        jdbc.execute("ALTER TABLE notification_outbox ADD CONSTRAINT test_reject_counterparty "
                + "CHECK (counterparty_id <> " + counterpartyId + ")");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, java.util.List.of()));
        try {
            assertThatThrownBy(() -> counterpartyService.updateCounterpartyStatus(counterpartyId, request))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            SecurityContextHolder.clearContext();
            jdbc.execute("ALTER TABLE notification_outbox DROP CONSTRAINT test_reject_counterparty");
        }

        assertThat(counterparties.findById(counterpartyId).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
        assertThat(audits.findAll()).noneMatch(row -> "COUNTERPARTY_STATUS_UPDATED".equals(row.getAction())
                && counterpartyId.equals(row.getEntityId()));
        assertThat(outbox.findAll()).noneMatch(row -> counterpartyId.equals(row.getCounterpartyId()));
    }

    @Test
    void committedLimitUpdateQueuesExactAmountsForTheFacilityOwner() throws Exception {
        setBalances("50000.01", "100.00", "400.00");
        String checker = login(82L, "RISK_OFFICER", INSTITUTION_ID);
        String otherBank = login(83L, "RISK_OFFICER", 29L);

        updateFacilityLimit(otherBank, "75000.23").andExpect(status().isForbidden());
        updateFacilityLimit(checker, "499.99").andExpect(status().isBadRequest());
        assertFacilityLimitUnchanged("50000.01");

        updateFacilityLimit(checker, "75000.23").andExpect(status().isOk());
        assertThat(limits.findById(limit.getId()).orElseThrow().getLimitAmount())
                .isEqualTo(new BigDecimal("75000.23"));
        assertThat(audits.findAll()).filteredOn(row -> "CREDIT_LIMIT_UPDATED".equals(row.getAction())
                && limit.getId().equals(row.getEntityId())).singleElement();
        assertThat(outbox.findAll()).filteredOn(row -> limit.getId().equals(row.getCreditLimitId()))
                .singleElement().satisfies(row -> {
                    assertThat(row.getFinancialInstitutionId()).isEqualTo(INSTITUTION_ID);
                    assertThat(row.getEventType()).isEqualTo("CREDIT_LIMIT_UPDATED");
                    assertThat(row.getOldLimitAmount()).isEqualTo(new BigDecimal("50000.01"));
                    assertThat(row.getNewLimitAmount()).isEqualTo(new BigDecimal("75000.23"));
                    assertThat(row.getStatus()).isNull();
                    assertThat(row.getCreditRequestId()).isNull();
                    assertThat(row.getPublishedAt()).isNull();
                });
    }

    @Test
    void outboxInsertFailureRollsBackFacilityLimitAndAudit() {
        setBalances("50000.01", "100.00", "400.00");
        var actor = new AuthenticatedCreditUser(82L, "limit-test@example.test", "RISK_OFFICER", INSTITUTION_ID);
        var request = new UpdateCreditLimitRequest();
        request.setLimitAmount(new BigDecimal("75000.23"));

        // Force the outbox insert to fail inside the same transaction.
        jdbc.execute("ALTER TABLE notification_outbox ADD CONSTRAINT test_reject_facility_limit "
                + "CHECK (credit_limit_id <> " + limit.getId() + ")");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));
        try {
            assertThatThrownBy(() -> creditLimitService.updateCreditLimit(limit.getId(), request))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            SecurityContextHolder.clearContext();
            jdbc.execute("ALTER TABLE notification_outbox DROP CONSTRAINT test_reject_facility_limit");
        }

        assertFacilityLimitUnchanged("50000.01");
    }

    private void assertFacilityLimitUnchanged(String amount) {
        assertThat(limits.findById(limit.getId()).orElseThrow().getLimitAmount())
                .isEqualTo(new BigDecimal(amount));
        assertThat(audits.findAll()).noneMatch(row -> "CREDIT_LIMIT_UPDATED".equals(row.getAction())
                && limit.getId().equals(row.getEntityId()));
        assertThat(outbox.findAll()).noneMatch(row -> limit.getId().equals(row.getCreditLimitId()));
    }

    @Test
    void committedGroupLimitUpdateQueuesExactAmountsForTheOwner() throws Exception {
        GroupCreditLimit sharedLimit = createGroupLimit();
        String checker = login(82L, "RISK_OFFICER", INSTITUTION_ID);
        String otherBank = login(83L, "RISK_OFFICER", 29L);

        updateGroupLimit(sharedLimit.getId(), otherBank, "150000.23").andExpect(status().isForbidden());
        updateGroupLimit(sharedLimit.getId(), checker, "499.99").andExpect(status().isBadRequest());
        assertGroupLimitUnchanged(sharedLimit.getId());

        updateGroupLimit(sharedLimit.getId(), checker, "150000.23").andExpect(status().isOk());
        assertThat(groupLimits.findById(sharedLimit.getId()).orElseThrow().getAmount())
                .isEqualTo(new BigDecimal("150000.23"));
        assertThat(audits.findAll()).filteredOn(row -> "GROUP_CREDIT_LIMIT_UPDATED".equals(row.getAction())
                && sharedLimit.getId().equals(row.getEntityId())).singleElement();
        assertThat(outbox.findAll()).filteredOn(row -> sharedLimit.getId().equals(row.getGroupCreditLimitId()))
                .singleElement().satisfies(row -> {
                    assertThat(row.getFinancialInstitutionId()).isEqualTo(INSTITUTION_ID);
                    assertThat(row.getCorporateGroupId()).isEqualTo(sharedLimit.getCorporateGroup().getId());
                    assertThat(row.getEventType()).isEqualTo("GROUP_CREDIT_LIMIT_UPDATED");
                    assertThat(row.getOldLimitAmount()).isEqualTo(new BigDecimal("100000.01"));
                    assertThat(row.getNewLimitAmount()).isEqualTo(new BigDecimal("150000.23"));
                    assertThat(row.getCreditRequestId()).isNull();
                    assertThat(row.getCreditLimitId()).isNull();
                    assertThat(row.getPublishedAt()).isNull();
                });
    }

    @Test
    void outboxInsertFailureRollsBackGroupLimitAndAudit() {
        GroupCreditLimit sharedLimit = createGroupLimit();
        var actor = new AuthenticatedCreditUser(82L, "group-limit-test@example.test", "RISK_OFFICER", INSTITUTION_ID);
        var request = new UpdateGroupCreditLimitRequest();
        request.setLimitAmount(new BigDecimal("150000.23"));

        // Force the outbox insert to fail inside the same transaction.
        jdbc.execute("ALTER TABLE notification_outbox ADD CONSTRAINT test_reject_group_limit "
                + "CHECK (group_credit_limit_id <> " + sharedLimit.getId() + ")");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));
        try {
            assertThatThrownBy(() -> groupCreditLimitService.updateGroupCreditLimit(sharedLimit.getId(), request))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            SecurityContextHolder.clearContext();
            jdbc.execute("ALTER TABLE notification_outbox DROP CONSTRAINT test_reject_group_limit");
        }

        assertGroupLimitUnchanged(sharedLimit.getId());
    }

    private GroupCreditLimit createGroupLimit() {
        CorporateGroup group = new CorporateGroup();
        group.setName("Group limit event test " + UUID.randomUUID());
        group = groups.save(group);
        addMember(group, limit.getCounterparty());

        GroupCreditLimit sharedLimit = new GroupCreditLimit();
        sharedLimit.setCorporateGroup(group);
        sharedLimit.setFinancialInstitutionId(INSTITUTION_ID);
        sharedLimit.setAmount(new BigDecimal("100000.01"));
        return groupLimits.save(sharedLimit);
    }

    private ResultActions updateGroupLimit(Long id, String authorization, String amount) throws Exception {
        return mvc.perform(put("/api/group-credit-limits/{id}", id)
                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limitAmount\":" + amount + "}"));
    }

    private void assertGroupLimitUnchanged(Long id) {
        assertThat(groupLimits.findById(id).orElseThrow().getAmount()).isEqualTo(new BigDecimal("100000.01"));
        assertThat(audits.findAll()).noneMatch(row -> "GROUP_CREDIT_LIMIT_UPDATED".equals(row.getAction())
                && id.equals(row.getEntityId()));
        assertThat(outbox.findAll()).noneMatch(row -> id.equals(row.getGroupCreditLimitId()));
    }

    @Test
    void relayRetriesTheSameEventAfterKafkaFailureAndSkipsPublishedEvents() {
        outbox.deleteAll();
        NotificationOutbox event = saveNotification();
        Long eventId = event.getId();
        assertThat(eventId).isPositive();
        when(kafkaPublisher.publish(any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));
        NotificationOutboxRelay relay = new NotificationOutboxRelay(outbox, kafkaPublisher, jdbc);

        transactions.executeWithoutResult(status -> relay.publishPendingNotifications());
        assertThat(outbox.findById(eventId).orElseThrow().getPublishedAt()).isNull();
        transactions.executeWithoutResult(status -> relay.publishPendingNotifications());
        assertThat(outbox.findById(eventId).orElseThrow().getPublishedAt()).isNotNull();
        transactions.executeWithoutResult(status -> relay.publishPendingNotifications());

        verify(kafkaPublisher, times(2)).publish(argThat(sent -> eventId.equals(sent.getId())));
        verifyNoMoreInteractions(kafkaPublisher);
    }

    @Test
    void relayWaitsForAcknowledgmentAndBlocksAnotherWorker() throws Exception {
        outbox.deleteAll();
        NotificationOutbox event = saveNotification();
        CompletableFuture<SendResult<String, String>> acknowledgment = new CompletableFuture<>();
        CountDownLatch sendStarted = new CountDownLatch(1);
        when(kafkaPublisher.publish(any())).thenAnswer(invocation -> {
            sendStarted.countDown();
            return acknowledgment;
        });
        NotificationOutboxRelay first = new NotificationOutboxRelay(outbox, kafkaPublisher, jdbc);
        NotificationOutboxRelay second = new NotificationOutboxRelay(outbox, kafkaPublisher, jdbc);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var publishing = worker.submit(() ->
                    transactions.executeWithoutResult(status -> first.publishPendingNotifications()));
            assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(outbox.findById(event.getId()).orElseThrow().getPublishedAt()).isNull();
            transactions.executeWithoutResult(status -> second.publishPendingNotifications());
            verify(kafkaPublisher, times(1)).publish(any());

            acknowledgment.complete(null);
            publishing.get(5, TimeUnit.SECONDS);
            assertThat(outbox.findById(event.getId()).orElseThrow().getPublishedAt()).isNotNull();
        } finally {
            acknowledgment.completeExceptionally(new IllegalStateException("Test finished"));
            worker.shutdownNow();
        }
    }

    private NotificationOutbox saveNotification() {
        NotificationOutbox event = new NotificationOutbox();
        event.setFinancialInstitutionId(INSTITUTION_ID);
        event.setCreditRequestId(125L);
        event.setRequesterId(REQUESTER_ID);
        event.setStatus("RESERVED");
        event.setEventType("CREDIT_REQUEST_APPROVED");
        return outbox.save(event);
    }

}
