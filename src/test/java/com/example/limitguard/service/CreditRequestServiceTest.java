package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.CreditRequestResponse;
import com.example.limitguard.enums.CounterpartyStatus;
import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.exception.InsufficientHeadroomException;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.CreditRequest;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.ApprovalDecisionRepository;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.CreditRequestRepository;
import com.example.limitguard.security.MyUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// tells JUnit to use Mockito when running this test class
// Mockito helps us create fake dependencies without using a real database
@ExtendWith(MockitoExtension.class)
public class CreditRequestServiceTest {

    // creates a fake credit request repository
    @Mock
    private CreditRequestRepository creditRequestRepository;

    // creates a fake credit limit repository
    @Mock
    private CreditLimitRepository creditLimitRepository;

    // creates a fake audit log repository
    @Mock
    private AuditLogRepository auditLogRepository;

    // creates a fake approval decision repository
    @Mock
    private ApprovalDecisionRepository approvalDecisionRepository;

    // prevents real emails from being sent during tests
    @Mock
    private EmailService emailService;

    // prevents real-time events from being published during tests
    @Mock
    private CreditRequestEventService creditRequestEventService;

    // creates the real service and injects the fake dependencies
    @InjectMocks
    private CreditRequestService creditRequestService;

    // sample objects used by the tests
    private CreditLimit creditLimit;
    private Counterparty counterparty;
    private User requester;
    private CreditRequest creditRequest;

    // prepares sample data before each test
    @BeforeEach
    void setUp() {

        counterparty = new Counterparty();
        counterparty.setId(2L);
        counterparty.setStatus(CounterpartyStatus.ACTIVE);

        creditLimit = new CreditLimit();
        creditLimit.setId(3L);
        creditLimit.setCounterparty(counterparty);
        creditLimit.setLimitAmount(new BigDecimal("1000000"));
        creditLimit.setUsedAmount(new BigDecimal("200000"));
        creditLimit.setReservedAmount(new BigDecimal("100000"));

        requester = new User();
        requester.setId(10L);

        creditRequest = new CreditRequest();
        creditRequest.setId(5L);
        creditRequest.setAmount(new BigDecimal("100000"));
        creditRequest.setCreditLimit(creditLimit);
        creditRequest.setRequester(requester);
        creditRequest.setStatus(CreditRequestStatus.RESERVED);
    }

    // clears the logged-in user after each test
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // sets up a fake logged-in user for methods that check authentication
    private void loginAs(User user) {

        MyUserDetails userDetails = mock(MyUserDetails.class);
        when(userDetails.getUser()).thenReturn(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null
                )
        );
    }


    // It divides each test into three simple steps:
    //Arrange — Prepare everything needed for the test.
    //Act — Call the method you want to test.
    //Assert — Check whether the result is what you expected.


    // test that a request within available headroom reserves credit capacity
    // expect the request to become RESERVED and reserved exposure to increase
    @Test
    void shouldReserveCreditWhenHeadroomIsAvailable() {

        // arrange: create a request for 100000
        CreditRequestRequest request = new CreditRequestRequest();
        request.setCreditLimitId(3L);
        request.setAmount(new BigDecimal("100000"));

        when(creditLimitRepository.findById(3L))
                .thenReturn(Optional.of(creditLimit));

        when(creditRequestRepository.save(any(CreditRequest.class)))
                .thenAnswer(invocation -> {
                    CreditRequest saved = invocation.getArgument(0);
                    saved.setId(5L);
                    return saved;
                });

        loginAs(requester);

        // act: create the credit request
        CreditRequestResponse response =
                creditRequestService.createCreditRequest(request);

        // assert: check that the request is reserved
        assertEquals(CreditRequestStatus.RESERVED, response.getStatus());

        // assert: reserved exposure increases from 100000 to 200000
        assertEquals(
                0,
                new BigDecimal("200000")
                        .compareTo(creditLimit.getReservedAmount())
        );

        verify(creditRequestRepository).save(any(CreditRequest.class));
        verify(creditLimitRepository).save(creditLimit);
    }

    // test that a request exceeding available headroom is rejected
    // expect InsufficientHeadroomException and no credit reservation
    @Test
    void shouldRejectRequestWhenHeadroomIsInsufficient() {

        // arrange: available headroom is 700000
        CreditRequestRequest request = new CreditRequestRequest();
        request.setCreditLimitId(3L);
        request.setAmount(new BigDecimal("800000"));

        when(creditLimitRepository.findById(3L))
                .thenReturn(Optional.of(creditLimit));

        // act and assert: check that the request is rejected
        assertThrows(
                InsufficientHeadroomException.class,
                () -> creditRequestService.createCreditRequest(request)
        );

        // assert: no exposure is changed or saved
        assertEquals(
                0,
                new BigDecimal("100000")
                        .compareTo(creditLimit.getReservedAmount())
        );

        verify(creditRequestRepository, never()).save(any());
        verify(creditLimitRepository, never()).save(any());
    }

    // test that requests above 500000 require Risk Officer approval
    // expect PENDING_APPROVAL without reserving credit capacity
    @Test
    void shouldRequireApprovalForLargeCreditRequest() {

        // arrange: request 600000 while headroom is 700000
        CreditRequestRequest request = new CreditRequestRequest();
        request.setCreditLimitId(3L);
        request.setAmount(new BigDecimal("600000"));

        when(creditLimitRepository.findById(3L))
                .thenReturn(Optional.of(creditLimit));

        when(creditRequestRepository.save(any(CreditRequest.class)))
                .thenAnswer(invocation -> {
                    CreditRequest saved = invocation.getArgument(0);
                    saved.setId(5L);
                    return saved;
                });

        loginAs(requester);

        // act: create the credit request
        CreditRequestResponse response =
                creditRequestService.createCreditRequest(request);

        // assert: the request must wait for approval
        assertEquals(
                CreditRequestStatus.PENDING_APPROVAL,
                response.getStatus()
        );

        // assert: reserved exposure remains unchanged
        assertEquals(
                0,
                new BigDecimal("100000")
                        .compareTo(creditLimit.getReservedAmount())
        );

        verify(creditRequestRepository).save(any(CreditRequest.class));
    }

    // test that cancelling a reserved request releases its credit capacity
    // expect CANCELLED and reserved exposure to decrease by 100000
    @Test
    void shouldReleaseReservedExposureWhenCancelled() {

        // arrange: the existing request belongs to the logged-in user
        loginAs(requester);

        when(creditRequestRepository.findById(5L))
                .thenReturn(Optional.of(creditRequest));

        when(creditRequestRepository.save(any(CreditRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act: cancel the reserved request
        CreditRequestResponse response =
                creditRequestService.cancelCreditRequest(5L);

        // assert: check the new request status
        assertEquals(CreditRequestStatus.CANCELLED, response.getStatus());

        // assert: reserved exposure decreases from 100000 to zero
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(creditLimit.getReservedAmount())
        );

        verify(creditLimitRepository).save(creditLimit);
    }

    // test that a used credit request cannot be cancelled
    // expect IllegalArgumentException and no exposure changes
    @Test
    void shouldRejectCancellationOfUsedRequest() {

        // arrange: the request has already been used
        creditRequest.setStatus(CreditRequestStatus.USED);

        loginAs(requester);

        when(creditRequestRepository.findById(5L))
                .thenReturn(Optional.of(creditRequest));

        // act and assert: check that cancellation is rejected
        assertThrows(
                IllegalArgumentException.class,
                () -> creditRequestService.cancelCreditRequest(5L)
        );

        // assert: the request and exposure remain unchanged
        assertEquals(CreditRequestStatus.USED, creditRequest.getStatus());

        assertEquals(
                0,
                new BigDecimal("100000")
                        .compareTo(creditLimit.getReservedAmount())
        );

        verify(creditRequestRepository, never()).save(any());
        verify(creditLimitRepository, never()).save(any());
    }

    // test that a user cannot cancel another user's credit request
    // expect AccessDeniedException without changing the request
    @Test
    void shouldPreventCancellingAnotherUsersRequest() {

        // arrange: log in as a different user
        User otherUser = new User();
        otherUser.setId(20L);

        loginAs(otherUser);

        when(creditRequestRepository.findById(5L))
                .thenReturn(Optional.of(creditRequest));

        // act and assert: check that access is denied
        assertThrows(
                AccessDeniedException.class,
                () -> creditRequestService.cancelCreditRequest(5L)
        );

        // assert: the original request remains reserved
        assertEquals(CreditRequestStatus.RESERVED, creditRequest.getStatus());

        verify(creditRequestRepository, never()).save(any());
        verify(creditLimitRepository, never()).save(any());
    }
}
