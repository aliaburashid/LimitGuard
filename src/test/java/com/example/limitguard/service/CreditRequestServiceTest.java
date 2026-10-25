package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.CreditRequestResponse;
import com.example.limitguard.enums.CounterpartyStatus;
import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.exception.InsufficientHeadroomException;
import com.example.limitguard.exception.InvalidCreditRequestStateException;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.CreditRequest;
import com.example.limitguard.model.User;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.enums.UserRole;
import com.example.limitguard.security.InstitutionAccessService;
import org.mockito.Spy;
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
    @Spy
    private InstitutionAccessService institutionAccessService = new InstitutionAccessService();

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
        counterparty.setName("Test Counterparty");
        counterparty.setStatus(CounterpartyStatus.ACTIVE);

        creditLimit = new CreditLimit();
        FinancialInstitution institution = new FinancialInstitution();
        institution.setId(1L);
        institution.setName("Test Bank");
        creditLimit.setFinancialInstitution(institution);
        creditLimit.setId(3L);
        creditLimit.setCounterparty(counterparty);
        creditLimit.setLimitAmount(new BigDecimal("1000000"));
        creditLimit.setUsedAmount(new BigDecimal("200000"));
        creditLimit.setReservedAmount(new BigDecimal("100000"));

        requester = new User();
        requester.setId(10L);
        requester.setFirstName("Test");
        requester.setLastName("Requester");
        requester.setRole(UserRole.RELATIONSHIP_MANAGER);
        requester.setFinancialInstitution(institution);
        loginAs(requester);

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

        MyUserDetails userDetails = new MyUserDetails(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
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
        assertThrows(InvalidCreditRequestStateException.class, () ->
                creditRequestService.cancelCreditRequest(5L)
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
        otherUser.setRole(UserRole.RELATIONSHIP_MANAGER);
        otherUser.setFinancialInstitution(requester.getFinancialInstitution());

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


    // test that the person who created a request cannot approve or reject it
    // expect AccessDeniedException even if the user is a Risk Officer
    @Test
    void shouldPreventMakerFromApprovingOrRejectingOwnRequest() {

        // arrange: give the requester the Risk Officer role
        requester.setRole(UserRole.RISK_OFFICER);

        // simulate the requester being logged in
        loginAs(requester);

        // set the request status to pending approval
        creditRequest.setStatus(CreditRequestStatus.PENDING_APPROVAL);

        // simulate finding the request in the database
        when(creditRequestRepository.findById(5L))
                .thenReturn(Optional.of(creditRequest));

        // act and assert: the requester cannot approve their own request
        assertThrows(
                AccessDeniedException.class,
                () -> creditRequestService.approveCreditRequest(5L)
        );

        // act and assert: the requester cannot reject their own request
        assertThrows(
                AccessDeniedException.class,
                () -> creditRequestService.rejectCreditRequest(5L, "Declined")
        );

        // assert: no request or approval decision should be saved
        verify(creditRequestRepository, never()).save(any());
        verify(approvalDecisionRepository, never()).save(any());

        // assert: the request must remain pending approval
        assertEquals(
                CreditRequestStatus.PENDING_APPROVAL,
                creditRequest.getStatus()
        );
    }



    // test that a different Risk Officer can approve a pending credit request
    // expect the request to become RESERVED and exposure to increase correctly
    @Test
    void shouldAllowCheckerToApproveRequestWithinOwnInstitution() {

        // arrange: create another Risk Officer from the same bank
        User checker = new User();
        checker.setId(20L);
        checker.setRole(UserRole.RISK_OFFICER);
        checker.setFinancialInstitution(requester.getFinancialInstitution());

        // simulate the checker being logged in
        loginAs(checker);

        // set the request status and amount
        creditRequest.setStatus(CreditRequestStatus.PENDING_APPROVAL);
        creditRequest.setAmount(new BigDecimal("600000.25"));

        // simulate finding the pending request
        when(creditRequestRepository.findById(5L))
                .thenReturn(Optional.of(creditRequest));

        // simulate saving the updated request
        when(creditRequestRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act: approve the credit request
        CreditRequestResponse response =
                creditRequestService.approveCreditRequest(5L);

        // assert: the request should now be reserved
        assertEquals(
                CreditRequestStatus.RESERVED,
                response.getStatus()
        );

        // assert: reserved exposure increases from 100000 to 700000.25
        assertEquals(
                new BigDecimal("700000.25"),
                creditLimit.getReservedAmount()
        );

        // assert: used exposure should remain unchanged
        assertEquals(
                new BigDecimal("200000"),
                creditLimit.getUsedAmount()
        );

        // assert: the approved amount is correct
        assertEquals(
                new BigDecimal("600000.25"),
                response.getAmount()
        );

        // assert: the reservation has an expiry date
        assertNotNull(response.getExpiresAt());

        // assert: the response includes the correct business information
        assertEquals("Test Requester", response.getRequesterName());
        assertEquals("Test Counterparty", response.getCounterpartyName());
        assertEquals("Test Bank", response.getFinancialInstitutionName());

        // assert: the approval decision was saved
        verify(approvalDecisionRepository).save(any());
    }



    // test that a scheduled task can expire a credit request automatically
    // expect EXPIRED and reserved exposure to be released without a logged-in user
    @Test
    void shouldExpireReservedRequestWithoutLoggedInUser() {

        // arrange: clear the logged-in user
        // scheduled background tasks do not have an authenticated user
        SecurityContextHolder.clearContext();

        // set the reservation expiry time to one minute in the past
        creditRequest.setExpiresAt(
                java.time.LocalDateTime.now().minusMinutes(1)
        );

        // simulate finding the expired request
        when(creditRequestRepository.findById(5L))
                .thenReturn(Optional.of(creditRequest));

        // simulate saving the updated request
        when(creditRequestRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act: expire the credit request
        CreditRequestResponse response =
                creditRequestService.expireCreditRequest(5L);

        // assert: the request status should become EXPIRED
        assertEquals(
                CreditRequestStatus.EXPIRED,
                response.getStatus()
        );

        // assert: reserved exposure decreases from 100000 to zero
        assertEquals(
                BigDecimal.ZERO,
                creditLimit.getReservedAmount()
        );

        // assert: used exposure remains unchanged
        assertEquals(
                new BigDecimal("200000"),
                creditLimit.getUsedAmount()
        );
    }



    // test that a Relationship Manager cannot use another bank's credit limit
    // expect AccessDeniedException and no credit request to be saved
    @Test
    void shouldRejectCreditRequestForAnotherFinancialInstitution() {

        // arrange: the logged-in user belongs to institution 1
        // this is already configured in @BeforeEach

        // create another financial institution
        FinancialInstitution foreignInstitution = new FinancialInstitution();
        foreignInstitution.setId(2L);
        foreignInstitution.setName("Another Bank");

        // create a credit limit belonging to institution 2
        CreditLimit foreignCreditLimit = new CreditLimit();
        foreignCreditLimit.setId(4L);
        foreignCreditLimit.setFinancialInstitution(foreignInstitution);
        foreignCreditLimit.setCounterparty(counterparty);
        foreignCreditLimit.setLimitAmount(new BigDecimal("1000000"));
        foreignCreditLimit.setUsedAmount(new BigDecimal("200000"));
        foreignCreditLimit.setReservedAmount(new BigDecimal("100000"));

        // prepare a request using the foreign bank's credit limit
        CreditRequestRequest request = new CreditRequestRequest();
        request.setCreditLimitId(4L);
        request.setAmount(new BigDecimal("100000"));

        // simulate finding the foreign credit limit
        when(creditLimitRepository.findById(4L))
                .thenReturn(Optional.of(foreignCreditLimit));

        // act and assert: the request should be rejected
        assertThrows(
                AccessDeniedException.class,
                () -> creditRequestService.createCreditRequest(request)
        );

        // assert: no credit request or credit limit should be saved
        verify(creditRequestRepository, never()).save(any());
        verify(creditLimitRepository, never()).save(any());

        // assert: the foreign bank's reserved exposure stays unchanged
        assertEquals(
                0,
                new BigDecimal("100000")
                        .compareTo(foreignCreditLimit.getReservedAmount())
        );
    }

}
