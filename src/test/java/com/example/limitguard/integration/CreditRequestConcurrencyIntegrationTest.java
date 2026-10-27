package com.example.limitguard.integration;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.UpdateCreditLimitRequest;
import com.example.limitguard.enums.CounterpartyStatus;
import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.enums.UserRole;
import com.example.limitguard.model.*;
import com.example.limitguard.repository.*;
import com.example.limitguard.security.MyUserDetails;
import com.example.limitguard.service.CreditLimitService;
import com.example.limitguard.service.CreditRequestEventService;
import com.example.limitguard.service.CreditRequestService;
import com.example.limitguard.service.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

// Loads the real Spring Boot application.
// The services and repositories use a real PostgreSQL database.
@SpringBootTest
@ActiveProfiles("concurrency-test")
public class CreditRequestConcurrencyIntegrationTest {

    @Autowired
    private CreditRequestService creditRequestService;

    @Autowired
    private CreditLimitService creditLimitService;

    @Autowired
    private CreditRequestRepository creditRequestRepository;

    @Autowired
    private CreditLimitRepository creditLimitRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    // Handles corporate groups in the test database.
    @Autowired
    private CorporateGroupRepository corporateGroupRepository;

    // Handles companies belonging to a corporate group.
    @Autowired
    private CorporateGroupMembershipRepository corporateGroupMembershipRepository;

    // Handles the shared credit limit for a corporate group.
    @Autowired
    private GroupCreditLimitRepository groupCreditLimitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Prevent real emails from being sent during tests.
    @MockitoBean
    private EmailService emailService;

    // Prevent real SSE notifications from being published.
    @MockitoBean
    private CreditRequestEventService creditRequestEventService;

    // IDs of the sample records created before each test.
    private Long creditLimitId;
    private Long requesterId;
    private Long checkerOneId;
    private Long checkerTwoId;

    // A simple result for each thread.
    // If error is null, the operation succeeded.
    private record Result(boolean success, Exception error) {
    }

    // This describes the action a thread should perform.
    @FunctionalInterface
    private interface ConcurrentAction {
        void run() throws Exception;
    }

    // Creates fresh sample data before each test.
    @BeforeEach
    void setUp() {

        // IMPORTANT:
        // This clears test data and must only run against
        // the separate limitguard_concurrency_test database.
        jdbcTemplate.execute(
                "TRUNCATE TABLE financial_institutions, counterparties " +
                        "RESTART IDENTITY CASCADE"
        );

        // Create a bank.
        FinancialInstitution bank = new FinancialInstitution();
        bank.setName("Concurrency Test Bank");
        bank = financialInstitutionRepository.saveAndFlush(bank);

        // Create an active counterparty.
        Counterparty counterparty = new Counterparty();
        counterparty.setName("Concurrency Test Company");
        counterparty.setStatus(CounterpartyStatus.ACTIVE);
        counterparty = counterpartyRepository.saveAndFlush(counterparty);

        // Create a Relationship Manager who makes requests.
        User requester = createUser(
                "requester@test.local",
                UserRole.RELATIONSHIP_MANAGER,
                bank
        );

        // Create two different Risk Officers.
        User checkerOne = createUser(
                "checker1@test.local",
                UserRole.RISK_OFFICER,
                bank
        );

        User checkerTwo = createUser(
                "checker2@test.local",
                UserRole.RISK_OFFICER,
                bank
        );

        requesterId = requester.getId();
        checkerOneId = checkerOne.getId();
        checkerTwoId = checkerTwo.getId();

        // Create a credit limit with BHD 1,000,000 available.
        CreditLimit limit = new CreditLimit();
        limit.setFinancialInstitution(bank);
        limit.setCounterparty(counterparty);
        limit.setLimitAmount(new BigDecimal("1000000"));
        limit.setUsedAmount(BigDecimal.ZERO);
        limit.setReservedAmount(BigDecimal.ZERO);

        limit = creditLimitRepository.saveAndFlush(limit);
        creditLimitId = limit.getId();
    }

    // Creates a sample user and saves them to PostgreSQL.
    private User createUser(
            String email,
            UserRole role,
            FinancialInstitution bank
    ) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("test-password-not-used");
        user.setEmailVerified(true);
        user.setRole(role);
        user.setFinancialInstitution(bank);

        return userRepository.saveAndFlush(user);
    }

    // Logs in as the given user inside the CURRENT thread.
    // Spring Security stores authentication separately per thread.
    private void loginAs(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow();

        MyUserDetails details = new MyUserDetails(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        details,
                        null,
                        details.getAuthorities()
                )
        );
    }

    // Runs an action as a specific user.
    // Each thread gets its own authentication.
    private Result runAs(
            Long userId,
            ConcurrentAction action
    ) {
        try {
            if (userId != null) {
                loginAs(userId);
            } else {
                // Background scheduler operations have no logged-in user.
                SecurityContextHolder.clearContext();
            }

            action.run();

            return new Result(true, null);

        } catch (Exception exception) {

            return new Result(false, exception);

        } finally {

            // Always clear authentication when the thread finishes.
            SecurityContextHolder.clearContext();
        }
    }

    // Runs two actions at approximately the same time.
    private List<Result> runConcurrently(
            Long firstUserId,
            ConcurrentAction firstAction,
            Long secondUserId,
            ConcurrentAction secondAction
    ) throws Exception {

        // Create a thread pool containing two worker threads.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Both threads wait at this latch before starting.
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<Result> firstFuture = executor.submit(() -> {
                startLatch.await();
                return runAs(firstUserId, firstAction);
            });

            Future<Result> secondFuture = executor.submit(() -> {
                startLatch.await();
                return runAs(secondUserId, secondAction);
            });

            // Release both threads at approximately the same time.
            startLatch.countDown();

            // Wait for both operations to finish.
            // A timeout prevents the test from hanging forever.
            Result first = firstFuture.get(20, TimeUnit.SECONDS);
            Result second = secondFuture.get(20, TimeUnit.SECONDS);

            return List.of(first, second);

        } finally {

            // Shut down the thread pool after each test.
            executor.shutdownNow();
        }
    }

    // Counts how many concurrent operations succeeded.
    private long successCount(List<Result> results) {
        return results.stream()
                .filter(Result::success)
                .count();
    }

    // Displays errors to make failed tests easier to debug.
    private String describeResults(List<Result> results) {

        List<String> messages = new ArrayList<>();

        for (Result result : results) {
            if (result.success()) {
                messages.add("SUCCESS");
            } else {
                messages.add(
                        result.error().getClass().getSimpleName()
                                + ": "
                                + result.error().getMessage()
                );
            }
        }

        return messages.toString();
    }

    // Reads the latest credit limit values from PostgreSQL.
    private CreditLimit getLimit() {
        return creditLimitRepository.findById(creditLimitId)
                .orElseThrow();
    }

    // Reads the latest credit request from PostgreSQL.
    private CreditRequest getRequest(Long requestId) {
        return creditRequestRepository.findById(requestId)
                .orElseThrow();
    }

    // Compares money values without being affected by decimal scale.
    private void assertMoneyEquals(
            String expected,
            BigDecimal actual
    ) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }

    // Creates a credit request directly in the database.
    // Useful when testing an existing request's state.
    private Long createStoredRequest(
            String amount,
            CreditRequestStatus status,
            LocalDateTime expiresAt
    ) {
        CreditRequest request = new CreditRequest();

        request.setAmount(new BigDecimal(amount));
        request.setStatus(status);
        request.setExpiresAt(expiresAt);

        request.setRequester(
                userRepository.findById(requesterId).orElseThrow()
        );

        request.setCreditLimit(getLimit());

        return creditRequestRepository.saveAndFlush(request).getId();
    }

    // Updates the reserved exposure before a test begins.
    private void setReservedAmount(String amount) {

        CreditLimit limit = getLimit();
        limit.setReservedAmount(new BigDecimal(amount));

        creditLimitRepository.saveAndFlush(limit);
    }

    // Updates the total credit limit before a test begins.
    private void setLimitAmount(String amount) {

        CreditLimit limit = getLimit();
        limit.setLimitAmount(new BigDecimal(amount));

        creditLimitRepository.saveAndFlush(limit);
    }

    // =========================================================
    // SCENARIO 1: TWO OFFICERS APPROVE THE SAME REQUEST
    // =========================================================

    @Test
    void shouldPreventDoubleApproval() throws Exception {

        // STEP 1: Create a BHD 600,000 request waiting for approval.
        Long requestId = createStoredRequest(
                "600000",
                CreditRequestStatus.PENDING_APPROVAL,
                null
        );

        // STEP 2: Two Risk Officers try to approve the same request.
        List<Result> results = runConcurrently(
                checkerOneId,
                () -> creditRequestService.approveCreditRequest(requestId),
                checkerTwoId,
                () -> creditRequestService.approveCreditRequest(requestId)
        );

        // STEP 3: Only one Risk Officer should succeed.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        // STEP 4: Check that the request is RESERVED.
        assertEquals(
                CreditRequestStatus.RESERVED,
                getRequest(requestId).getStatus()
        );

        // STEP 5: Check that BHD 600,000 was reserved only once.
        assertMoneyEquals("600000", getLimit().getReservedAmount());
    }

    // =========================================================
    // SCENARIO 2: TWO USERS COMPETE FOR THE SAME INDIVIDUAL CREDIT LIMIT
    // =========================================================

    @Test
    void shouldPreventOverReservation() throws Exception {

        // STEP 1: Set the individual credit limit to BHD 500,000.
        setLimitAmount("500000");

        // STEP 2: Prepare two requests for BHD 400,000 each.
        // Both cannot fit inside the individual credit limit.
        CreditRequestRequest firstRequest = new CreditRequestRequest();
        firstRequest.setCreditLimitId(creditLimitId);
        firstRequest.setAmount(new BigDecimal("400000"));

        CreditRequestRequest secondRequest = new CreditRequestRequest();
        secondRequest.setCreditLimitId(creditLimitId);
        secondRequest.setAmount(new BigDecimal("400000"));

        // STEP 3: Submit both requests at approximately the same time.
        List<Result> results = runConcurrently(
                requesterId,
                () -> creditRequestService.createCreditRequest(firstRequest),
                checkerOneId,
                () -> creditRequestService.createCreditRequest(secondRequest)
        );

        // STEP 4: Check that only one request succeeded.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        // STEP 5: Check that only BHD 400,000 was reserved.
        assertMoneyEquals("400000", getLimit().getReservedAmount());

        // STEP 6: Make sure the individual credit limit was not exceeded.
        CreditLimit limit = getLimit();

        BigDecimal exposure = limit.getUsedAmount()
                .add(limit.getReservedAmount());

        assertTrue(exposure.compareTo(limit.getLimitAmount()) <= 0);
    }

    // =========================================================
    // SCENARIO 3: APPROVE AND REJECT THE SAME REQUEST
    // =========================================================

    @Test
    void shouldPreventConflictingApprovalAndRejection() throws Exception {

        // STEP 1: Create a request waiting for a Risk Officer decision.
        Long requestId = createStoredRequest(
                "600000",
                CreditRequestStatus.PENDING_APPROVAL,
                null
        );

        // STEP 2: One officer approves while another rejects.
        List<Result> results = runConcurrently(
                checkerOneId,
                () -> creditRequestService.approveCreditRequest(requestId),
                checkerTwoId,
                () -> creditRequestService.rejectCreditRequest(
                        requestId,
                        "Rejected during concurrency test"
                )
        );

        // STEP 3: Only one decision should succeed.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        CreditRequestStatus finalStatus =
                getRequest(requestId).getStatus();

        // STEP 4: Check that the request has one valid final status.
        assertTrue(
                finalStatus == CreditRequestStatus.RESERVED
                        || finalStatus == CreditRequestStatus.REJECTED
        );

        // STEP 5: Check the reserved amount matches the final decision.
        // Approved means BHD 600,000; rejected means zero.
        if (finalStatus == CreditRequestStatus.RESERVED) {

            assertMoneyEquals(
                    "600000",
                    getLimit().getReservedAmount()
            );

        } else {

            assertMoneyEquals(
                    "0",
                    getLimit().getReservedAmount()
            );
        }
    }

    // =========================================================
    // SCENARIO 4: TWO USERS CANCEL THE SAME REQUEST
    // =========================================================

    @Test
    void shouldPreventDoubleCancellation() throws Exception {

        // STEP 1: Create a request with BHD 100,000 already reserved.
        Long requestId = createStoredRequest(
                "100000",
                CreditRequestStatus.RESERVED,
                LocalDateTime.now().plusHours(1)
        );

        setReservedAmount("100000");

        // STEP 2: Try to cancel the same request from two threads.
        List<Result> results = runConcurrently(
                requesterId,
                () -> creditRequestService.cancelCreditRequest(requestId),
                requesterId,
                () -> creditRequestService.cancelCreditRequest(requestId)
        );

        // STEP 3: Only one cancellation should succeed.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        // STEP 4: Check that the request is CANCELLED.
        assertEquals(
                CreditRequestStatus.CANCELLED,
                getRequest(requestId).getStatus()
        );

        // STEP 5: Check that the reserved amount is zero, not negative.
        assertMoneyEquals("0", getLimit().getReservedAmount());
    }

    // =========================================================
    // SCENARIO 5: TWO USERS MARK THE SAME REQUEST AS USED
    // =========================================================

    @Test
    void shouldPreventDoubleUsage() throws Exception {

        // STEP 1: Create a request with BHD 100,000 already reserved.
        Long requestId = createStoredRequest(
                "100000",
                CreditRequestStatus.RESERVED,
                LocalDateTime.now().plusHours(1)
        );

        setReservedAmount("100000");

        // STEP 2: Two threads try to mark the same request as USED.
        List<Result> results = runConcurrently(
                requesterId,
                () -> creditRequestService.markCreditRequestAsUsed(requestId),
                requesterId,
                () -> creditRequestService.markCreditRequestAsUsed(requestId)
        );

        // STEP 4: Check that only one operation succeeded.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        // STEP 4: Check that the request is USED.
        assertEquals(
                CreditRequestStatus.USED,
                getRequest(requestId).getStatus()
        );

        // STEP 5: Check that reserved credit decreased to zero.
        assertMoneyEquals("0", getLimit().getReservedAmount());

        // STEP 6: Check that used credit increased only once.
        assertMoneyEquals("100000", getLimit().getUsedAmount());
    }

    // =========================================================
    // SCENARIO 6: EXPIRATION COMPETES WITH MARKING AS USED
    // =========================================================

    @Test
    void shouldPreventUsingAnExpiredReservation() throws Exception {

        // STEP 1: Create a reservation that expired one minute ago.
        Long requestId = createStoredRequest(
                "100000",
                CreditRequestStatus.RESERVED,
                LocalDateTime.now().minusMinutes(1)
        );

        setReservedAmount("100000");

        // STEP 2: Expire the request and mark it USED at the same time.
        List<Result> results = runConcurrently(
                null,
                () -> creditRequestService.expireCreditRequest(requestId),
                requesterId,
                () -> creditRequestService.markCreditRequestAsUsed(requestId)
        );

        // STEP 3: Check that the request ends as EXPIRED.
        assertEquals(
                CreditRequestStatus.EXPIRED,
                getRequest(requestId).getStatus(),
                describeResults(results)
        );

        // STEP 4: Check that the reserved credit was released.
        assertMoneyEquals("0", getLimit().getReservedAmount());

        // STEP 5: Check that no credit was marked as used.
        assertMoneyEquals("0", getLimit().getUsedAmount());

        // STEP 6: Only the expiration should succeed.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );
    }

    // =========================================================
    // SCENARIO 7: LIMIT REDUCTION VS CREDIT RESERVATION
    // =========================================================

    @Test
    void shouldPreventLimitReductionBelowConcurrentExposure()
            throws Exception {

        // STEP 1: Start with a credit limit of BHD 1,000,000.
        //
        // STEP 2: Prepare a BHD 400,000 reservation and a limit reduction.
        // The proposed new limit is BHD 300,000.
        //
        // Both actions cannot succeed together.

        CreditRequestRequest request = new CreditRequestRequest();
        request.setCreditLimitId(creditLimitId);
        request.setAmount(new BigDecimal("400000"));

        UpdateCreditLimitRequest updateRequest =
                new UpdateCreditLimitRequest();

        updateRequest.setLimitAmount(new BigDecimal("300000"));

        // STEP 3: Run the reservation and limit reduction together.
        List<Result> results = runConcurrently(
                requesterId,
                () -> creditRequestService.createCreditRequest(request),
                checkerOneId,
                () -> creditLimitService.updateCreditLimit(
                        creditLimitId,
                        updateRequest
                )
        );

        // STEP 4: Check that only one operation succeeded.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        CreditLimit limit = getLimit();

        BigDecimal exposure = limit.getUsedAmount()
                .add(limit.getReservedAmount());

        // STEP 5: Check that exposure does not exceed the final limit.
        assertTrue(
                exposure.compareTo(limit.getLimitAmount()) <= 0,
                "Exposure must not exceed the credit limit. "
                        + describeResults(results)
        );

        // STEP 6: Check that the final balances match a valid outcome.
        boolean reservationWon =
                limit.getReservedAmount().compareTo(
                        new BigDecimal("400000")
                ) == 0
                        && limit.getLimitAmount().compareTo(
                        new BigDecimal("1000000")
                ) == 0;

        boolean reductionWon =
                limit.getReservedAmount().compareTo(
                        BigDecimal.ZERO
                ) == 0
                        && limit.getLimitAmount().compareTo(
                        new BigDecimal("300000")
                ) == 0;

        assertTrue(
                reservationWon || reductionWon,
                "Unexpected final credit limit state"
        );
    }

    // =========================================================
    // SCENARIO 8: TWO COMPANIES SHARE ONE CORPORATE GROUP LIMIT
    // =========================================================

    @Test
    void shouldPreventCorporateGroupOverReservation() throws Exception {

        // STEP 1: Get the test bank and first company.
        FinancialInstitution bank = financialInstitutionRepository.findAll().get(0);
        Counterparty companyA = counterpartyRepository.findAll().get(0);

        // STEP 2: Create a second company.
        Counterparty companyB = new Counterparty();
        companyB.setName("Corporate Group Company B");
        companyB.setStatus(CounterpartyStatus.ACTIVE);
        companyB = counterpartyRepository.saveAndFlush(companyB);

        // STEP 3: Give Company A an individual limit of BHD 200,000.
        CreditLimit limitA = getLimit();
        limitA.setLimitAmount(new BigDecimal("200000"));
        creditLimitRepository.saveAndFlush(limitA);

        // STEP 4: Give Company B an individual limit of BHD 200,000.
        CreditLimit limitB = new CreditLimit();
        limitB.setFinancialInstitution(bank);
        limitB.setCounterparty(companyB);
        limitB.setLimitAmount(new BigDecimal("200000"));
        limitB.setUsedAmount(BigDecimal.ZERO);
        limitB.setReservedAmount(BigDecimal.ZERO);
        limitB = creditLimitRepository.saveAndFlush(limitB);

        // STEP 5: Create one corporate group for both companies.
        CorporateGroup group = new CorporateGroup();
        group.setName("Concurrency Test Corporate Group");
        group = corporateGroupRepository.saveAndFlush(group);

        // STEP 6: Add Company A to the corporate group.
        CorporateGroupMembership membershipA = new CorporateGroupMembership();
        membershipA.setCorporateGroup(group);
        membershipA.setCounterparty(companyA);
        corporateGroupMembershipRepository.saveAndFlush(membershipA);

        // STEP 7: Add Company B to the same corporate group.
        CorporateGroupMembership membershipB = new CorporateGroupMembership();
        membershipB.setCorporateGroup(group);
        membershipB.setCounterparty(companyB);
        corporateGroupMembershipRepository.saveAndFlush(membershipB);

        // STEP 8: Set the shared group limit to BHD 100,000.
        GroupCreditLimit groupLimit = new GroupCreditLimit();
        groupLimit.setCorporateGroup(group);
        groupLimit.setFinancialInstitution(bank);
        groupLimit.setAmount(new BigDecimal("100000"));
        groupLimit = groupCreditLimitRepository.saveAndFlush(groupLimit);

        // STEP 9: Prepare a BHD 80,000 request for Company A.
        CreditRequestRequest firstRequest = new CreditRequestRequest();
        firstRequest.setCreditLimitId(limitA.getId());
        firstRequest.setAmount(new BigDecimal("80000"));

        // STEP 10: Prepare a BHD 80,000 request for Company B.
        CreditRequestRequest secondRequest = new CreditRequestRequest();
        secondRequest.setCreditLimitId(limitB.getId());
        secondRequest.setAmount(new BigDecimal("80000"));

        // STEP 11: Submit both requests at approximately the same time.
        // Each company has enough credit, but the group cannot cover both.
        List<Result> results = runConcurrently(
                requesterId,
                () -> creditRequestService.createCreditRequest(firstRequest),
                requesterId,
                () -> creditRequestService.createCreditRequest(secondRequest)
        );

        // STEP 12: Only one reservation should succeed.
        assertEquals(1, successCount(results), describeResults(results));

        // STEP 13: Read the latest balances from PostgreSQL.
        CreditLimit updatedLimitA = creditLimitRepository.findById(limitA.getId())
                .orElseThrow();
        CreditLimit updatedLimitB = creditLimitRepository.findById(limitB.getId())
                .orElseThrow();

        // STEP 14: Add both companies' reserved amounts.
        BigDecimal totalReserved = updatedLimitA.getReservedAmount()
                .add(updatedLimitB.getReservedAmount());

        // STEP 15: Only BHD 80,000 should be reserved across the group.
        assertMoneyEquals("80000", totalReserved);

        // STEP 16: The shared corporate group limit must not be exceeded.
        assertTrue(
                totalReserved.compareTo(groupLimit.getAmount()) <= 0,
                "Corporate group exposure must not exceed the shared limit. "
                        + describeResults(results)
        );
    }
}
