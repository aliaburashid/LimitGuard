package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.CreditRequestResponse;
import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.exception.CreditLimitNotFoundException;
import com.example.limitguard.exception.InsufficientHeadroomException;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.CreditRequest;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.CreditRequestRepository;
import com.example.limitguard.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.limitguard.enums.CounterpartyStatus;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

@Service
public class CreditRequestService {

    @Autowired
    private CreditRequestRepository creditRequestRepository;

    @Autowired
    private CreditLimitRepository creditLimitRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    //---------------------------------------------------------------------------

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


    // submits a new credit capacity request
    @Transactional
    public CreditRequestResponse createCreditRequest(CreditRequestRequest creditRequestRequest) {

        // checks that the credit limit exists
        CreditLimit creditLimit = creditLimitRepository
                .findById(creditRequestRequest.getCreditLimitId())
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found"));

        // prevents new credit requests for frozen or closed counterparties
        if (creditLimit.getCounterparty().getStatus() == CounterpartyStatus.FROZEN
                || creditLimit.getCounterparty().getStatus() == CounterpartyStatus.CLOSED) {

            throw new IllegalArgumentException(
                    "Credit requests cannot be created for a frozen or closed counterparty");
        }

        // calculates the credit capacity that is still available
        BigDecimal availableHeadroom = creditLimit.getLimitAmount()
                .subtract(creditLimit.getUsedAmount())
                .subtract(creditLimit.getReservedAmount());

        // prevents a credit request from exceeding the available headroom
        // rejecting 0 and below
        if (creditRequestRequest.getAmount()
                .compareTo(availableHeadroom) > 0) {
            throw new InsufficientHeadroomException("Requested amount exceeds available headroom");
        }

        // gets the requester from the logged-in user
        User requester = getCurrentLoggedInUser();

        // create a credit request object
        CreditRequest creditRequest = new CreditRequest();

        creditRequest.setAmount(creditRequestRequest.getAmount());
        creditRequest.setCreditLimit(creditLimit);
        creditRequest.setRequester(requester);

        // requests above 500,000 require Risk Officer approval
        if (creditRequestRequest.getAmount()
                .compareTo(new BigDecimal("500000")) > 0) {
            creditRequest.setStatus(CreditRequestStatus.PENDING_APPROVAL);
        } else {
            creditRequest.setStatus(CreditRequestStatus.RESERVED);
        }

        // save the credit request
        CreditRequest savedCreditRequest = creditRequestRepository.save(creditRequest);

        // records the credit request creation in the audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("CREDIT_REQUEST_CREATED");
        auditLog.setEntityType("CREDIT_REQUEST");
        auditLog.setEntityId(savedCreditRequest.getId());
        auditLog.setDetails("Created credit request of " + savedCreditRequest.getAmount() + " with status " + savedCreditRequest.getStatus());
        auditLog.setActor(requester);

        auditLogRepository.save(auditLog);

        // return it in a DTO response
        return new CreditRequestResponse(
                savedCreditRequest.getId(),
                savedCreditRequest.getAmount(),
                savedCreditRequest.getStatus(),
                savedCreditRequest.getCreditLimit().getId(),
                savedCreditRequest.getCreditLimit().getCounterparty().getId(),
                savedCreditRequest.getRequester().getId(),
                savedCreditRequest.getExpiresAt(),
                savedCreditRequest.getCreatedAt(),
                savedCreditRequest.getUpdatedAt()
        );
    }

    // gets the credit requests submitted by the logged-in user
    public Page<CreditRequestResponse> getMyCreditRequests(Pageable pageable) {

        // gets the currently logged-in user
        User requester = getCurrentLoggedInUser();

        // finds only the credit requests created by this user
        Page<CreditRequest> creditRequests =
                creditRequestRepository.findByRequesterId(requester.getId(), pageable);

        // converts each CreditRequest into a CreditRequestResponse
        return creditRequests.map(creditRequest ->
                new CreditRequestResponse(
                        creditRequest.getId(),
                        creditRequest.getAmount(),
                        creditRequest.getStatus(),
                        creditRequest.getCreditLimit().getId(),
                        creditRequest.getCreditLimit().getCounterparty().getId(),
                        creditRequest.getRequester().getId(),
                        creditRequest.getExpiresAt(),
                        creditRequest.getCreatedAt(),
                        creditRequest.getUpdatedAt()
                )
        );
    }




}