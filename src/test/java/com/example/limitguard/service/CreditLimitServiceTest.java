package com.example.limitguard.service;

import com.example.limitguard.dto.CreditExposureResponse;
import com.example.limitguard.dto.CreditLimitRequest;
import com.example.limitguard.dto.CreditLimitResponse;
import com.example.limitguard.dto.UpdateCreditLimitRequest;
import com.example.limitguard.exception.CreditLimitAlreadyExistsException;
import com.example.limitguard.exception.CreditLimitNotFoundException;
import com.example.limitguard.exception.InvalidCreditLimitReductionException;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.repository.CounterpartyRepository;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import com.example.limitguard.security.MyUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
public class CreditLimitServiceTest {

    // @Mock creates a fake credit limit repository
    // allows us to control what the repository returns during tests
    @Mock
    private CreditLimitRepository creditLimitRepository;

    // @Mock creates a fake financial institution repository
    @Mock
    private FinancialInstitutionRepository financialInstitutionRepository;

    // @Mock creates a fake counterparty repository
    @Mock
    private CounterpartyRepository counterpartyRepository;

    // @Mock creates a fake audit log repository
    @Mock
    private AuditLogRepository auditLogRepository;

    // @InjectMocks creates the real credit limit service
    // and injects the fake repositories above into it
    @InjectMocks
    private CreditLimitService creditLimitService;

    private CreditLimit creditLimit;
    private Counterparty counterparty;
    private FinancialInstitution financialInstitution;

    @BeforeEach
    void setUp() {

        // creates a sample financial institution for testing
        financialInstitution = new FinancialInstitution();
        financialInstitution.setId(1L);

        // creates a sample counterparty for testing
        counterparty = new Counterparty();
        counterparty.setId(2L);
        counterparty.setName("Bahrain Trading Company");

        // creates a credit limit with used and reserved exposure
        creditLimit = new CreditLimit();
        creditLimit.setId(3L);
        creditLimit.setLimitAmount(new BigDecimal("1000000"));
        creditLimit.setUsedAmount(new BigDecimal("300000"));
        creditLimit.setReservedAmount(new BigDecimal("200000"));
        creditLimit.setFinancialInstitution(financialInstitution);
        creditLimit.setCounterparty(counterparty);
    }

    // clears the logged-in user after each test
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // It divides each test into three simple steps:
    //Arrange — Prepare everything needed for the test.
    //Act — Call the method you want to test.
    //Assert — Check whether the result is what you expected.

    // test that available headroom is calculated correctly
    // expect 1000000 - 300000 - 200000 = 500000
    @Test
    void shouldCalculateAvailableHeadroomCorrectly() {

        // arrange: return the sample credit limit
        when(creditLimitRepository.findById(3L))
                .thenReturn(Optional.of(creditLimit));

        // act: get the credit exposure
        CreditExposureResponse response =
                creditLimitService.getCreditExposure(3L);

        // assert: check the remaining available headroom
        assertEquals(
                0,
                new BigDecimal("500000")
                        .compareTo(response.getAvailableHeadroom())
        );
    }

    // test that used and reserved exposure are included in the response
    // expect the correct exposure amounts to be returned
    @Test
    void shouldReturnCorrectExposureAmounts() {

        // arrange: return the sample credit limit
        when(creditLimitRepository.findById(3L))
                .thenReturn(Optional.of(creditLimit));

        // act: get the credit exposure
        CreditExposureResponse response =
                creditLimitService.getCreditExposure(3L);

        // assert: check the credit limit and exposure amounts
        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(response.getLimitAmount())
        );

        assertEquals(
                0,
                new BigDecimal("300000")
                        .compareTo(response.getUsedAmount())
        );

        assertEquals(
                0,
                new BigDecimal("200000")
                        .compareTo(response.getReservedAmount())
        );
    }

    // test that requesting a missing credit limit throws an exception
    // expect CreditLimitNotFoundException
    @Test
    void shouldRejectMissingCreditLimit() {

        // arrange: the credit limit does not exist
        when(creditLimitRepository.findById(99L))
                .thenReturn(Optional.empty());

        // act and assert: check that the exception is thrown
        assertThrows(
                CreditLimitNotFoundException.class,
                () -> creditLimitService.getCreditExposure(99L)
        );
    }

    // test that a credit limit cannot be reduced below current exposure
    // expect the update to be rejected because exposure is 500000
    @Test
    void shouldRejectLimitReductionBelowExposure() {

        // arrange: set the new limit below current exposure
        UpdateCreditLimitRequest request =
                new UpdateCreditLimitRequest();

        request.setLimitAmount(new BigDecimal("400000"));

        when(creditLimitRepository.findById(3L))
                .thenReturn(Optional.of(creditLimit));

        // act and assert: check that the reduction is rejected
        assertThrows(
                InvalidCreditLimitReductionException.class,
                () -> creditLimitService.updateCreditLimit(3L, request)
        );

        // assert: check that the original limit was not changed
        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(creditLimit.getLimitAmount())
        );

        verify(creditLimitRepository, never()).save(any());
    }

    // test that a credit limit cannot be created twice for the same pair
    // expect CreditLimitAlreadyExistsException
    @Test
    void shouldRejectDuplicateCreditLimit() {

        // arrange: prepare a request for an existing credit limit
        CreditLimitRequest request = new CreditLimitRequest();
        request.setFinancialInstitutionId(1L);
        request.setCounterpartyId(2L);
        request.setLimitAmount(new BigDecimal("1000000"));

        when(financialInstitutionRepository.findById(1L))
                .thenReturn(Optional.of(financialInstitution));

        when(counterpartyRepository.findById(2L))
                .thenReturn(Optional.of(counterparty));

        when(creditLimitRepository
                .existsByFinancialInstitutionIdAndCounterpartyId(1L, 2L))
                .thenReturn(true);

        // act and assert: check that duplicate creation is rejected
        assertThrows(
                CreditLimitAlreadyExistsException.class,
                () -> creditLimitService.createCreditLimit(request)
        );

        verify(creditLimitRepository, never()).save(any());
    }

    // test that a new credit limit starts with no exposure
    // expect used and reserved amounts to both be zero
    @Test
    void shouldCreateCreditLimitWithZeroExposure() {

        // arrange: prepare a new credit limit request
        CreditLimitRequest request = new CreditLimitRequest();
        request.setFinancialInstitutionId(1L);
        request.setCounterpartyId(2L);
        request.setLimitAmount(new BigDecimal("1000000"));

        when(financialInstitutionRepository.findById(1L))
                .thenReturn(Optional.of(financialInstitution));

        when(counterpartyRepository.findById(2L))
                .thenReturn(Optional.of(counterparty));

        when(creditLimitRepository
                .existsByFinancialInstitutionIdAndCounterpartyId(1L, 2L))
                .thenReturn(false);

        // returns the credit limit that was passed to save()
        when(creditLimitRepository.save(any(CreditLimit.class)))
                .thenAnswer(invocation -> {
                    CreditLimit saved = invocation.getArgument(0);
                    saved.setId(3L);
                    return saved;
                });

        // creates a mock logged-in user for the audit log
        User user = new User();
        MyUserDetails userDetails = mock(MyUserDetails.class);

        when(userDetails.getUser()).thenReturn(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null
                )
        );

        // act: create the credit limit
        CreditLimitResponse response =
                creditLimitService.createCreditLimit(request);

        // assert: check that both exposure amounts start at zero
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(response.getUsedAmount())
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(response.getReservedAmount())
        );

        // assert: check that the credit limit and audit log were saved
        verify(creditLimitRepository).save(any(CreditLimit.class));
        verify(auditLogRepository).save(any());
    }
}
