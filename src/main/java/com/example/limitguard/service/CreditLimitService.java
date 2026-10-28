package com.example.limitguard.service;

import com.example.limitguard.dto.CreditLimitRequest;
import com.example.limitguard.dto.CreditLimitResponse;
import com.example.limitguard.exception.CounterpartyNotFoundException;
import com.example.limitguard.exception.CreditLimitAlreadyExistsException;
import com.example.limitguard.exception.FinancialInstitutionNotFoundException;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.repository.CounterpartyRepository;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import com.example.limitguard.security.InstitutionAccessService;
import com.example.limitguard.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.limitguard.dto.CreditExposureResponse;
import com.example.limitguard.exception.CreditLimitNotFoundException;
import com.example.limitguard.dto.UpdateCreditLimitRequest;
import com.example.limitguard.exception.InvalidCreditLimitReductionException;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CreditLimitService {

    @Autowired
    private CreditLimitRepository creditLimitRepository;

    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private InstitutionAccessService institutionAccessService;

    // Sends real-time notifications when credit limits are updated
    @Autowired
    private CreditRequestEventService creditRequestEventService;

    private static final Logger logger =
            LoggerFactory.getLogger(CreditLimitService.class);

    //-------------------------------------------------------------------------------

    // gets the currently logged-in user
    private User getCurrentLoggedInUser() {
        // ( SecurityContextHolder ): spring security keeps info and the currently authenticated/logged-in user here
        // (.getContext() ) : Gets the current security information.
        // (.getAuthentication() ) : info about who logged in
        // ( .getPrincipal() ): Gets the actual logged-in user's details.
        // gets the logged-in users details from Spring Security
        MyUserDetails userDetails = (MyUserDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return userDetails.getUser();
    }

    // records credit limit changes in the audit log without repeating the same code
    private void recordAuditLog(String action, CreditLimit creditLimit, String details, User actor) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType("CREDIT_LIMIT");
        auditLog.setEntityId(creditLimit.getId());
        auditLog.setDetails(details);
        auditLog.setActor(actor);
        auditLog.setFinancialInstitution(creditLimit.getFinancialInstitution());
        auditLogRepository.save(auditLog);
    }

    // converts a credit limit into a response with only the information we want to send
    // we use this method to avoid writing the same response code in different methods
    private CreditLimitResponse toResponse(CreditLimit creditLimit) {
        return new CreditLimitResponse(
                creditLimit.getId(),
                creditLimit.getLimitAmount(),
                creditLimit.getUsedAmount(),
                creditLimit.getReservedAmount(),
                creditLimit.getFinancialInstitution().getId(),
                creditLimit.getCounterparty().getId(),
                creditLimit.getCreatedAt(),
                creditLimit.getUpdatedAt()
        );
    }

    // creates a credit limit for a financial institution and counterparty
    @Transactional
    public CreditLimitResponse createCreditLimit(CreditLimitRequest creditLimitRequest) {

        // get the logged-in user's bank id
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // checks that the financial institution exists
        FinancialInstitution financialInstitution = financialInstitutionRepository
                .findById(institutionId)
                .orElseThrow(() ->
                        new FinancialInstitutionNotFoundException("Financial institution not found"));

        // checks that the counterparty exists
        Counterparty counterparty = counterpartyRepository
                .findById(creditLimitRequest.getCounterpartyId())
                .orElseThrow(() ->
                        new CounterpartyNotFoundException("Counterparty not found"));

        // only one credit limit can exist for the same
        // financial institution and counterparty combination
        if (creditLimitRepository
                .existsByFinancialInstitutionIdAndCounterpartyId(financialInstitution.getId(), counterparty.getId())) {
            throw new CreditLimitAlreadyExistsException(
                    "Credit limit already exists for this financial institution and counterparty");
        }

        // creates the new credit limit
        CreditLimit creditLimit = new CreditLimit();

        creditLimit.setLimitAmount(creditLimitRequest.getLimitAmount());
        creditLimit.setFinancialInstitution(financialInstitution);
        creditLimit.setCounterparty(counterparty);

        // every new credit limit starts with no used or reserved exposure
        creditLimit.setUsedAmount(BigDecimal.ZERO);
        creditLimit.setReservedAmount(BigDecimal.ZERO);

        // saves the credit limit to PostgreSQL
        CreditLimit savedCreditLimit = creditLimitRepository.save(creditLimit);

        // gets the Risk Officer who created the limit
        User currentUser = getCurrentLoggedInUser();

        // records the creation in the audit log
        // save which bank created this credit limit
        recordAuditLog(
                "CREDIT_LIMIT_CREATED", savedCreditLimit,
                "Created credit limit of " + savedCreditLimit.getLimitAmount() + " for counterparty " + counterparty.getName(), currentUser
        );

        // converts the saved entity into the response DTO
        return toResponse(savedCreditLimit);
    }

    // gets the credit exposure and available headroom
    public CreditExposureResponse getCreditExposure(Long creditLimitId) {

        // checks that the credit limit exists
        CreditLimit creditLimit = creditLimitRepository
                .findById(creditLimitId)
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found"));

        // deny access if the credit limit belongs to another bank
        // if it belongs to another bank, we throw AccessDeniedException
        institutionAccessService.requireInstitution(creditLimit.getFinancialInstitution().getId());

        // calculates the remaining available credit
        BigDecimal availableHeadroom = creditLimit.getLimitAmount()
                .subtract(creditLimit.getUsedAmount())
                .subtract(creditLimit.getReservedAmount());

        // returns the exposure information
        return new CreditExposureResponse(
                creditLimit.getId(),
                creditLimit.getCounterparty().getId(),
                creditLimit.getLimitAmount(),
                creditLimit.getUsedAmount(),
                creditLimit.getReservedAmount(),
                availableHeadroom
        );
    }


    // updates an existing credit limit
    @Transactional
    public CreditLimitResponse updateCreditLimit(
            Long creditLimitId,
            UpdateCreditLimitRequest updateCreditLimitRequest) {

        logger.info("[{}] UPDATE_LIMIT | START | Limit ID={}",
                Thread.currentThread().getName(),
                creditLimitId);

        logger.info("[{}] UPDATE_LIMIT | WAITING FOR LOCK | Limit ID={}",
                Thread.currentThread().getName(),
                creditLimitId);

        // locks the credit limit so it cannot be changed during another transaction
        CreditLimit creditLimit = creditLimitRepository
                .findWithLockById(creditLimitId)
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found"));

        logger.info("[{}] UPDATE_LIMIT | LOCK ACQUIRED | Limit ID={}",
                Thread.currentThread().getName(),
                creditLimitId);

        // deny access if the credit limit belongs to another bank
        institutionAccessService.requireInstitution(
                creditLimit.getFinancialInstitution().getId()
        );

        // keeps the old limit amount for the audit log
        BigDecimal oldLimitAmount = creditLimit.getLimitAmount();

        // calculates the counterparty current exposure
        BigDecimal currentExposure = creditLimit.getUsedAmount()
                .add(creditLimit.getReservedAmount());

        // prevents the new limit from being lower than the current exposure
        // compareTo() because its BigDecimal
        if (updateCreditLimitRequest.getLimitAmount()
                .compareTo(currentExposure) < 0) {

            // logs the failed credit limit reduction
            logger.warn("[{}] UPDATE_LIMIT | FAILED | Limit ID={} | New limit below current exposure",
                    Thread.currentThread().getName(),
                    creditLimitId);

            throw new InvalidCreditLimitReductionException(
                    "Credit limit cannot be lower than current exposure");
        }

        // updates only the approved limit amount
        creditLimit.setLimitAmount(updateCreditLimitRequest.getLimitAmount());

        // usedAmount and reservedAmount are NOT changed
        CreditLimit updatedCreditLimit = creditLimitRepository.save(creditLimit);

        // gets the Risk Officer who made the change
        User currentUser = getCurrentLoggedInUser();

        // records the change in the audit log
        // save which bank owns this credit limit
        recordAuditLog(
                "CREDIT_LIMIT_UPDATED",
                updatedCreditLimit,
                "Credit limit changed from " + oldLimitAmount
                        + " to " + updatedCreditLimit.getLimitAmount(),
                currentUser
        );

        // save the information needed for the SSE notification
        // these values are collected while the transaction is still active
        Long institutionId = updatedCreditLimit.getFinancialInstitution().getId();
        Long updatedCreditLimitId = updatedCreditLimit.getId();
        BigDecimal newLimitAmount = updatedCreditLimit.getLimitAmount();

        // register the SSE notification to run after a successful commit
        // if the database transaction rolls back, no notification is sent
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        // notify connected users about the credit limit update
                        // only users belonging to the same bank receive this event
                        try {
                            creditRequestEventService.publishCreditLimitUpdate(
                                    institutionId,
                                    updatedCreditLimitId,
                                    oldLimitAmount,
                                    newLimitAmount
                            );

                        } catch (Exception exception) {

                            // logs an SSE failure without undoing the saved credit limit
                            logger.error(
                                    "Failed to publish credit limit SSE for limit ID {}",
                                    updatedCreditLimitId,
                                    exception
                            );
                        }
                    }
                }
        );

        logger.info("[{}] UPDATE_LIMIT | SUCCESS | Limit ID={} | New Amount={}",
                Thread.currentThread().getName(),
                updatedCreditLimit.getId(),
                updatedCreditLimit.getLimitAmount());

        return toResponse(updatedCreditLimit);
    }

}