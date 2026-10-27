
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

        // Arrange:
        // A request for BHD 600,000 is waiting for approval.
        Long requestId = createStoredRequest(
                "600000",
                CreditRequestStatus.PENDING_APPROVAL,
                null
        );

        // Act:
        // Two different Risk Officers approve at the same time.
        List<Result> results = runConcurrently(
                checkerOneId,
                () -> creditRequestService.approveCreditRequest(requestId),
                checkerTwoId,
                () -> creditRequestService.approveCreditRequest(requestId)
        );

        // Assert:
        // Only one officer should successfully approve.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        // The request must be RESERVED.
        assertEquals(
                CreditRequestStatus.RESERVED,
                getRequest(requestId).getStatus()
        );

        // BHD 600,000 must be reserved only once.
        assertMoneyEquals("600000", getLimit().getReservedAmount());
    }

    // =========================================================
    // SCENARIO 2: TWO USERS COMPETE FOR AVAILABLE CREDIT
    // =========================================================

    @Test
    void shouldPreventOverReservation() throws Exception {

        // Arrange:
        // Only BHD 500,000 of credit is available.
        setLimitAmount("500000");

        // Each thread requests BHD 400,000.
        // Both requests cannot fit inside the credit limit.
        CreditRequestRequest firstRequest = new CreditRequestRequest();
        firstRequest.setCreditLimitId(creditLimitId);
        firstRequest.setAmount(new BigDecimal("400000"));

        CreditRequestRequest secondRequest = new CreditRequestRequest();
        secondRequest.setCreditLimitId(creditLimitId);
        secondRequest.setAmount(new BigDecimal("400000"));

        // Act:
        // Two users submit their requests at the same time.
        List<Result> results = runConcurrently(
                requesterId,
                () -> creditRequestService.createCreditRequest(firstRequest),
                checkerOneId,
                () -> creditRequestService.createCreditRequest(secondRequest)
        );

        // Assert:
        // One request succeeds, and the other must fail.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        // The bank must reserve only BHD 400,000.
        assertMoneyEquals("400000", getLimit().getReservedAmount());

        // Exposure must never exceed the credit limit.
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

        // Arrange:
        // The request is waiting for a Risk Officer's decision.
        Long requestId = createStoredRequest(
                "600000",
                CreditRequestStatus.PENDING_APPROVAL,
                null
        );

        // Act:
        // One officer approves while another rejects.
        List<Result> results = runConcurrently(
                checkerOneId,
                () -> creditRequestService.approveCreditRequest(requestId),
                checkerTwoId,
                () -> creditRequestService.rejectCreditRequest(
                        requestId,
                        "Rejected during concurrency test"
                )
        );

        // Assert:
        // Only one final decision should succeed.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        CreditRequestStatus finalStatus =
                getRequest(requestId).getStatus();

        // The request must have one valid final state.
        assertTrue(
                finalStatus == CreditRequestStatus.RESERVED
                        || finalStatus == CreditRequestStatus.REJECTED
        );

        // If approved, BHD 600,000 must be reserved.
        // If rejected, nothing must be reserved.
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

        // Arrange:
        // A request already reserves BHD 100,000.
        Long requestId = createStoredRequest(
                "100000",
                CreditRequestStatus.RESERVED,
                LocalDateTime.now().plusHours(1)
        );

        setReservedAmount("100000");

        // Act:
        // Two cancellation attempts happen at the same time.
        List<Result> results = runConcurrently(
                requesterId,
                () -> creditRequestService.cancelCreditRequest(requestId),
                requesterId,
                () -> creditRequestService.cancelCreditRequest(requestId)
        );

        // Assert:
        // Only one cancellation should succeed.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        // The request must be CANCELLED.
        assertEquals(
                CreditRequestStatus.CANCELLED,
                getRequest(requestId).getStatus()
        );

        // The reserved amount must decrease to zero.
        // It must not be released twice or become negative.
        assertMoneyEquals("0", getLimit().getReservedAmount());
    }

    // =========================================================
    // SCENARIO 5: TWO USERS MARK THE SAME REQUEST AS USED
    // =========================================================

    @Test
    void shouldPreventDoubleUsage() throws Exception {

        // Arrange:
        // BHD 100,000 is already reserved.
        Long requestId = createStoredRequest(
                "100000",
                CreditRequestStatus.RESERVED,
                LocalDateTime.now().plusHours(1)
        );

        setReservedAmount("100000");

        // Act:
        // Two threads try to mark the same request as USED.
        List<Result> results = runConcurrently(
                requesterId,
                () -> creditRequestService.markCreditRequestAsUsed(requestId),
                requesterId,
                () -> creditRequestService.markCreditRequestAsUsed(requestId)
        );

        // Assert:
        // Only one operation should succeed.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        // The request must become USED.
        assertEquals(
                CreditRequestStatus.USED,
                getRequest(requestId).getStatus()
        );

        // Reserved exposure decreases by BHD 100,000.
        assertMoneyEquals("0", getLimit().getReservedAmount());

        // Used exposure increases by BHD 100,000 only once.
        assertMoneyEquals("100000", getLimit().getUsedAmount());
    }

    // =========================================================
    // SCENARIO 6: EXPIRATION COMPETES WITH MARKING AS USED
    // =========================================================

    @Test
    void shouldPreventUsingAnExpiredReservation() throws Exception {

        // Arrange:
        // The reservation expired one minute ago.
        Long requestId = createStoredRequest(
                "100000",
                CreditRequestStatus.RESERVED,
                LocalDateTime.now().minusMinutes(1)
        );

        setReservedAmount("100000");

        // Act:
        // The scheduler tries to expire the request.
        // At the same time, a user tries to mark it as USED.
        List<Result> results = runConcurrently(
                null,
                () -> creditRequestService.expireCreditRequest(requestId),
                requesterId,
                () -> creditRequestService.markCreditRequestAsUsed(requestId)
        );

        // Assert:
        // The expired reservation must not become USED.
        assertEquals(
                CreditRequestStatus.EXPIRED,
                getRequest(requestId).getStatus(),
                describeResults(results)
        );

        // Reserved exposure must be released.
        assertMoneyEquals("0", getLimit().getReservedAmount());

        // No money should be moved to used exposure.
        assertMoneyEquals("0", getLimit().getUsedAmount());

        // Exactly one operation should succeed: expiration.
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

        // Arrange:
        // The original credit limit is BHD 1,000,000.
        //
        // Thread 1 tries to reserve BHD 400,000.
        // Thread 2 tries to reduce the limit to BHD 300,000.
        //
        // Both actions cannot succeed together.

        CreditRequestRequest request = new CreditRequestRequest();
        request.setCreditLimitId(creditLimitId);
        request.setAmount(new BigDecimal("400000"));

        UpdateCreditLimitRequest updateRequest =
                new UpdateCreditLimitRequest();

        updateRequest.setLimitAmount(new BigDecimal("300000"));

        // Act:
        // Reservation and limit reduction happen concurrently.
        List<Result> results = runConcurrently(
                requesterId,
                () -> creditRequestService.createCreditRequest(request),
                checkerOneId,
                () -> creditLimitService.updateCreditLimit(
                        creditLimitId,
                        updateRequest
                )
        );

        // Assert:
        // Only one operation should succeed.
        assertEquals(
                1,
                successCount(results),
                describeResults(results)
        );

        CreditLimit limit = getLimit();

        BigDecimal exposure = limit.getUsedAmount()
                .add(limit.getReservedAmount());

        // The final exposure must never exceed the final limit.
        assertTrue(
                exposure.compareTo(limit.getLimitAmount()) <= 0,
                "Exposure must not exceed the credit limit. "
                        + describeResults(results)
        );

        // The final state must match one of the two valid outcomes.
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
}
