package com.example.creditservice.service;

import com.example.creditservice.dto.CreditFacilityResponse;
import com.example.creditservice.dto.CreditLimitRequest;
import com.example.creditservice.dto.CreditLimitResponse;
import com.example.creditservice.exception.CreditLimitAlreadyExistsException;
import com.example.creditservice.model.AuditLog;
import com.example.creditservice.model.Counterparty;
import com.example.creditservice.model.CreditLimit;
import com.example.creditservice.repository.AuditLogRepository;
import com.example.creditservice.repository.CreditLimitRepository;
import com.example.creditservice.repository.CreditExposureSnapshotRepository;
import com.example.creditservice.security.InstitutionAccessService;
import com.example.creditservice.security.AuthenticatedCreditUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.example.creditservice.security.CatalogAccessService;
import org.springframework.transaction.annotation.Transactional;
import com.example.creditservice.dto.CreditExposureResponse;
import com.example.creditservice.dto.CreditExposureSnapshotResponse;
import com.example.creditservice.exception.CreditLimitNotFoundException;
import com.example.creditservice.dto.UpdateCreditLimitRequest;
import com.example.creditservice.exception.InvalidCreditLimitReductionException;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CreditLimitService {

    @Autowired
    private CatalogAccessService catalogAccess;

    @Autowired
    private CreditLimitRepository creditLimitRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationOutboxService notificationOutboxService;

    @Autowired
    private InstitutionAccessService institutionAccessService;

    @Autowired
    private CreditExposureSnapshotRepository exposureSnapshots;


    private static final Logger logger =
            LoggerFactory.getLogger(CreditLimitService.class);

    //-------------------------------------------------------------------------------

    // gets the currently logged-in user
    private AuthenticatedCreditUser getCurrentLoggedInUser() {
        return (AuthenticatedCreditUser) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }


    // records credit limit changes in the audit log without repeating the same code
    private void recordAuditLog(String action, CreditLimit creditLimit, String details, AuthenticatedCreditUser actor) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType("CREDIT_LIMIT");
        auditLog.setEntityId(creditLimit.getId());
        auditLog.setDetails(details);
        auditLog.setActorId(actor.getUserId());
        auditLog.setFinancialInstitutionId(creditLimit.getFinancialInstitutionId());
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
                creditLimit.getFinancialInstitutionId(),
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

        // institution ID comes from the authenticated user, validated by Auth Service
        // Credit Service does not access the FinancialInstitution entity directly

        // checks that the counterparty exists
        Counterparty counterparty = catalogAccess.requireCounterparty(creditLimitRequest.getCounterpartyId());

        // only one credit limit can exist for the same
        // financial institution and counterparty combination
        if (creditLimitRepository
                .existsByFinancialInstitutionIdAndCounterpartyId(institutionId, counterparty.getId())) {
            throw new CreditLimitAlreadyExistsException(
                    "Credit limit already exists for this financial institution and counterparty");
        }

        // creates the new credit limit
        CreditLimit creditLimit = new CreditLimit();

        creditLimit.setLimitAmount(creditLimitRequest.getLimitAmount());
        creditLimit.setFinancialInstitutionId(institutionId);
        creditLimit.setCounterparty(counterparty);

        // every new credit limit starts with no used or reserved exposure
        creditLimit.setUsedAmount(BigDecimal.ZERO);
        creditLimit.setReservedAmount(BigDecimal.ZERO);

        // saves the credit limit to PostgreSQL
        CreditLimit savedCreditLimit = creditLimitRepository.save(creditLimit);

        // gets the Risk Officer who created the limit
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // records the creation in the audit log
        // save which bank created this credit limit
        recordAuditLog(
                "CREDIT_LIMIT_CREATED", savedCreditLimit,
                "Created credit limit of " + savedCreditLimit.getLimitAmount() + " for counterparty " + counterparty.getName(), currentUser
        );

        // converts the saved entity into the response DTO
        return toResponse(savedCreditLimit);
    }

    // reads current exposure for every facility at the logged-in institution
    @Transactional(readOnly = true)
    public Page<CreditFacilityResponse> getFacilityDirectory(Pageable pageable) {
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        return creditLimitRepository.findByFinancialInstitutionIdOrderByIdAsc(institutionId, pageable)
                .map(facility -> new CreditFacilityResponse(
                        toExposureResponse(facility),
                        facility.getCounterparty().getName(),
                        facility.getCounterparty().getStatus()
                ));
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
        institutionAccessService.requireInstitution(creditLimit.getFinancialInstitutionId());

        return toExposureResponse(creditLimit);
    }

    // converts an already authorized facility into its current exposure response
    static CreditExposureResponse toExposureResponse(CreditLimit creditLimit) {
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


    // returns only recorded individual facility exposure for the logged-in institution
    public List<CreditExposureSnapshotResponse> getExposureHistory(boolean includeCurrent) {
        Long institutionId = institutionAccessService.getCurrentInstitutionId();
        Long requesterId = getCurrentLoggedInUser().getUserId();
        return includeCurrent ? exposureSnapshots.findHistory(institutionId, requesterId, true)
                : exposureSnapshots.findHistory(institutionId, requesterId);
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
                creditLimit.getFinancialInstitutionId()
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
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // records the change in the audit log
        // save which bank owns this credit limit
        recordAuditLog(
                "CREDIT_LIMIT_UPDATED",
                updatedCreditLimit,
                "Credit limit changed from " + oldLimitAmount
                        + " to " + updatedCreditLimit.getLimitAmount(),
                currentUser
        );

        // Queue the event with the facility update and audit in the same transaction.
        notificationOutboxService.recordCreditLimitUpdatedEvent(
                updatedCreditLimit.getFinancialInstitutionId(),
                updatedCreditLimit.getId(),
                oldLimitAmount,
                updatedCreditLimit.getLimitAmount());

        logger.info("[{}] UPDATE_LIMIT | SUCCESS | Limit ID={} | New Amount={}",
                Thread.currentThread().getName(),
                updatedCreditLimit.getId(),
                updatedCreditLimit.getLimitAmount());

        return toResponse(updatedCreditLimit);
    }

}