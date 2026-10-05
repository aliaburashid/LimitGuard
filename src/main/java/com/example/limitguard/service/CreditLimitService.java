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
import com.example.limitguard.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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

    // creates a credit limit for a financial institution and counterparty
    @Transactional
    public CreditLimitResponse createCreditLimit(CreditLimitRequest creditLimitRequest) {

        // checks that the financial institution exists
        FinancialInstitution financialInstitution = financialInstitutionRepository
                        .findById(creditLimitRequest.getFinancialInstitutionId())
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
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("CREDIT_LIMIT_CREATED");
        auditLog.setEntityType("CREDIT_LIMIT");
        auditLog.setEntityId(savedCreditLimit.getId());
        auditLog.setDetails("Created credit limit of " + savedCreditLimit.getLimitAmount() + " for counterparty " + counterparty.getName());
        auditLog.setActor(currentUser);

        auditLogRepository.save(auditLog);

        // converts the saved entity into the response DTO
        return new CreditLimitResponse(
                savedCreditLimit.getId(),
                savedCreditLimit.getLimitAmount(),
                savedCreditLimit.getUsedAmount(),
                savedCreditLimit.getReservedAmount(),
                savedCreditLimit.getFinancialInstitution().getId(),
                savedCreditLimit.getCounterparty().getId(),
                savedCreditLimit.getCreatedAt(),
                savedCreditLimit.getUpdatedAt()
        );
    }
}