package com.example.creditservice.service;

import com.example.creditservice.enums.CreditRequestStatus;
import com.example.creditservice.model.Counterparty;
import com.example.creditservice.model.CreditLimit;
import com.example.creditservice.model.CreditRequest;
import com.example.creditservice.model.CorporateGroup;
import com.example.creditservice.model.GroupCreditLimit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class CreditRequestConcurrencyIntegrationTest extends CreditRequestPostgresTestSupport {

    @Test
    void twoManagersCannotOverReserveTheSameFacility() throws Exception {
        setBalances("500000", "100000", "0");
        String firstManager = login(7L, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String secondManager = login(8L, "RELATIONSHIP_MANAGER", INSTITUTION_ID);

        assertThat(race(() -> create(firstManager, limit, 300000).andReturn().getResponse().getStatus(),
                () -> create(secondManager, limit, 300000).andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 400);
        assertBalances("100000", "300000");
        assertThat(facilityRequests(limit)).singleElement().satisfies(request ->
                assertThat(request.getStatus()).isEqualTo(CreditRequestStatus.RESERVED));
    }

    @Test
    void twoManagersCannotOverReserveSharedCapacityAcrossSeparateFacilities() throws Exception {
        setBalances("900000", "0", "0");
        Counterparty secondCompany = new Counterparty();
        secondCompany.setName("Shared facility test " + UUID.randomUUID());
        CreditLimit secondLimit = new CreditLimit();
        secondLimit.setCounterparty(counterparties.save(secondCompany));
        secondLimit.setFinancialInstitutionId(INSTITUTION_ID);
        secondLimit.setLimitAmount(new BigDecimal("900000"));
        secondLimit.setUsedAmount(BigDecimal.ZERO);
        secondLimit.setReservedAmount(BigDecimal.ZERO);
        secondLimit = limits.save(secondLimit);

        CorporateGroup group = new CorporateGroup();
        group.setName("Shared limit test " + UUID.randomUUID());
        group = groups.save(group);
        addMember(group, limit.getCounterparty());
        addMember(group, secondLimit.getCounterparty());

        GroupCreditLimit sharedLimit = new GroupCreditLimit();
        sharedLimit.setCorporateGroup(group);
        sharedLimit.setFinancialInstitutionId(INSTITUTION_ID);
        sharedLimit.setAmount(new BigDecimal("800000"));
        groupLimits.save(sharedLimit);

        String firstManager = login(7L, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String secondManager = login(8L, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        CreditLimit otherFacility = secondLimit;
        assertThat(race(() -> create(firstManager, limit, 450000).andReturn().getResponse().getStatus(),
                () -> create(secondManager, otherFacility, 450000).andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 400);

        BigDecimal reserved = limits.findById(limit.getId()).orElseThrow().getReservedAmount()
                .add(limits.findById(secondLimit.getId()).orElseThrow().getReservedAmount());
        assertThat(reserved).isEqualByComparingTo("450000");
        assertThat(sharedLimit.getAmount().subtract(reserved)).isEqualByComparingTo("350000");
    }

    @Test
    void concurrentApprovalsReserveOnceAndRecheckHeadroomForDifferentRequests() throws Exception {
        setBalances("1000000", "100000", "0");
        String requester = login(7L, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String firstChecker = login(8L, "RISK_OFFICER", INSTITUTION_ID);
        String secondChecker = login(9L, "RISK_OFFICER", INSTITUTION_ID);
        long first = submit(requester, limit, 600000, "PENDING_APPROVAL");
        long second = submit(requester, limit, 600000, "PENDING_APPROVAL");

        assertThat(race(() -> change(firstChecker, first, "approve").andReturn().getResponse().getStatus(),
                () -> change(secondChecker, second, "approve").andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(200, 400);
        assertBalances("100000", "600000");
        assertThat(facilityRequests(limit)).extracting(CreditRequest::getStatus)
                .containsExactlyInAnyOrder(CreditRequestStatus.RESERVED, CreditRequestStatus.PENDING_APPROVAL);

        change(requester, firstReservedId(), "cancel").andExpect(status().isOk());
        long pending = facilityRequests(limit).stream()
                .filter(request -> request.getStatus() == CreditRequestStatus.PENDING_APPROVAL)
                .findFirst().orElseThrow().getId();
        assertThat(race(() -> change(firstChecker, pending, "approve").andReturn().getResponse().getStatus(),
                () -> change(secondChecker, pending, "approve").andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(200, 400);
        assertBalances("100000", "600000");
        assertThat(audits.findAll().stream().filter(row -> pending == row.getEntityId()
                && "CREDIT_REQUEST_APPROVED".equals(row.getAction())).count()).isEqualTo(1);
    }

    @Test
    void concurrentApprovalAndRejectionCommitOneDecision() throws Exception {
        setBalances("1000000", "100000", "0");
        String requester = login(REQUESTER_ID, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String approver = login(8L, "RISK_OFFICER", INSTITUTION_ID);
        String rejecter = login(9L, "RISK_OFFICER", INSTITUTION_ID);
        long requestId = submit(requester, limit, 600000, "PENDING_APPROVAL");

        List<Integer> outcomes = raceOnRequest(requestId,
                () -> change(approver, requestId, "approve").andReturn().getResponse().getStatus(),
                () -> change(rejecter, requestId, "reject").andReturn().getResponse().getStatus());

        assertThat(outcomes).containsExactlyInAnyOrder(200, 400);
        boolean approved = outcomes.get(0) == 200;
        assertRequestStatus(requestId, approved ? CreditRequestStatus.RESERVED : CreditRequestStatus.REJECTED);
        assertBalances("100000", approved ? "600000" : "0");
        assertThat(jdbc.queryForList("SELECT decision FROM approval_decisions WHERE credit_request_id = ?",
                String.class, requestId)).containsExactly(approved ? "APPROVED" : "REJECTED");
    }

    @Test
    void concurrentCancellationReleasesReservationOnce() throws Exception {
        setBalances("1000", "100", "125");
        String requester = login(REQUESTER_ID, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        long requestId = reservation(LocalDateTime.now().plusHours(1)).getId();

        assertThat(raceOnRequest(requestId,
                () -> change(requester, requestId, "cancel").andReturn().getResponse().getStatus(),
                () -> change(requester, requestId, "cancel").andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(200, 400);

        assertRequestStatus(requestId, CreditRequestStatus.CANCELLED);
        assertBalances("100", "0");
    }

    @Test
    void concurrentUseTransfersReservationToUsedBalanceOnce() throws Exception {
        setBalances("1000", "100", "125");
        String requester = login(REQUESTER_ID, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        long requestId = reservation(LocalDateTime.now().plusHours(1)).getId();

        assertThat(raceOnRequest(requestId,
                () -> change(requester, requestId, "use").andReturn().getResponse().getStatus(),
                () -> change(requester, requestId, "use").andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(200, 400);

        assertRequestStatus(requestId, CreditRequestStatus.USED);
        assertBalances("225", "0");
    }

    @Test
    void concurrentUseCannotConsumeADueReservation() throws Exception {
        setBalances("1000", "100", "125");
        String requester = login(REQUESTER_ID, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        long requestId = reservation(LocalDateTime.now().minusMinutes(1)).getId();

        assertThat(raceOnRequest(requestId,
                () -> change(requester, requestId, "use").andReturn().getResponse().getStatus(),
                () -> {
                    service.expireCreditRequest(requestId);
                    return 200;
                })).containsExactly(400, 200);

        assertRequestStatus(requestId, CreditRequestStatus.EXPIRED);
        assertBalances("100", "0");
    }

    @Test
    void concurrentLimitReductionAndReservationCannotLeaveExposureAboveLimit() throws Exception {
        setBalances("1000000", "0", "0");
        String requester = login(REQUESTER_ID, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String checker = login(8L, "RISK_OFFICER", INSTITUTION_ID);

        List<Integer> outcomes = raceOnRow("SELECT id FROM credit_limits WHERE id = ? FOR UPDATE", limit.getId(),
                () -> create(requester, limit, 400000).andReturn().getResponse().getStatus(),
                () -> updateFacilityLimit(checker, "300000").andReturn().getResponse().getStatus());

        boolean reserved = outcomes.get(0) == 201;
        assertThat(outcomes).containsExactlyElementsOf(reserved ? List.of(201, 400) : List.of(400, 200));
        CreditLimit updated = limits.findById(limit.getId()).orElseThrow();
        assertThat(updated.getLimitAmount()).isEqualByComparingTo(reserved ? "1000000" : "300000");
        assertBalances("0", reserved ? "400000" : "0");
        assertThat(updated.getUsedAmount().add(updated.getReservedAmount()))
                .isLessThanOrEqualTo(updated.getLimitAmount());
        if (reserved) {
            assertThat(facilityRequests(limit)).singleElement().satisfies(request -> {
                assertThat(request.getStatus()).isEqualTo(CreditRequestStatus.RESERVED);
                assertThat(request.getAmount()).isEqualByComparingTo("400000");
            });
        } else {
            assertThat(facilityRequests(limit)).isEmpty();
        }
    }

    @Test
    void concurrentApprovalAndCancellationPreserveReservedExposure() throws Exception {
        setBalances("1000000", "100000", "0");
        String requester = login(REQUESTER_ID, "RELATIONSHIP_MANAGER", INSTITUTION_ID);
        String checker = login(8L, "RISK_OFFICER", INSTITUTION_ID);
        long requestId = submit(requester, limit, 600000, "PENDING_APPROVAL");

        List<Integer> outcomes = raceOnRequest(requestId,
                () -> change(checker, requestId, "approve").andReturn().getResponse().getStatus(),
                () -> change(requester, requestId, "cancel").andReturn().getResponse().getStatus());

        assertThat(outcomes.get(0)).isEqualTo(200);
        assertThat(outcomes.get(1)).isIn(200, 400);
        boolean cancelled = outcomes.get(1) == 200;
        assertRequestStatus(requestId, cancelled ? CreditRequestStatus.CANCELLED : CreditRequestStatus.RESERVED);
        assertBalances("100000", cancelled ? "0" : "600000");
        assertThat(jdbc.queryForList("SELECT decision FROM approval_decisions WHERE credit_request_id = ?",
                String.class, requestId)).containsExactly("APPROVED");
    }

    private List<CreditRequest> facilityRequests(CreditLimit facility) {
        return requests.findAll().stream()
                .filter(request -> facility.getId().equals(request.getCreditLimit().getId())).toList();
    }

    private long firstReservedId() {
        return facilityRequests(limit).stream().filter(request -> request.getStatus() == CreditRequestStatus.RESERVED)
                .findFirst().orElseThrow().getId();
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        try {
            var firstResult = workers.submit(() -> startTogether(ready, first));
            var secondResult = workers.submit(() -> startTogether(ready, second));
            return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
        }
    }

    private void assertRequestStatus(long requestId, CreditRequestStatus expected) {
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(expected);
    }

    private List<Integer> raceOnRequest(long requestId, Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        return raceOnRow("SELECT id FROM credit_requests WHERE id = ? FOR UPDATE", requestId, first, second);
    }

    private List<Integer> raceOnRow(String lockQuery, long rowId, Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        try {
            var results = transactions.execute(status -> {
                assertThat(jdbc.queryForObject(lockQuery, Long.class, rowId)).isEqualTo(rowId);
                var firstResult = workers.submit(first);
                var secondResult = workers.submit(second);
                awaitBothBlockedByTestTransaction();
                return List.of(firstResult, secondResult);
            });
            return List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
        }
    }

    private void awaitBothBlockedByTestTransaction() {
        // A worker may queue behind the other worker, so follow the whole PostgreSQL lock queue.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            Integer blocked = jdbc.queryForObject("""
                    WITH RECURSIVE lock_queue(pid) AS (
                        SELECT pg_backend_pid()
                        UNION
                        SELECT activity.pid FROM pg_stat_activity activity
                        JOIN lock_queue blocker ON blocker.pid = ANY (pg_blocking_pids(activity.pid))
                        WHERE activity.datname = current_database()
                    )
                    SELECT count(*) FROM lock_queue WHERE pid <> pg_backend_pid()
                    """, Integer.class);
            if (Integer.valueOf(2).equals(blocked)) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for concurrent database transactions", ex);
            }
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Both competing transactions must wait before the test releases its row lock");
    }

    private int startTogether(CountDownLatch ready, Callable<Integer> action) throws Exception {
        ready.countDown();
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        return action.call();
    }

}
