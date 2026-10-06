package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.CreditRequestResponse;
import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.enums.UserRole;
import com.example.limitguard.exception.CreditLimitNotFoundException;
import com.example.limitguard.exception.CreditRequestNotFoundException;
import com.example.limitguard.exception.InsufficientHeadroomException;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.CreditRequest;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.CreditRequestRepository;
import com.example.limitguard.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
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
            // requests of 500,000 or less can reserve capacity directly
            reserveCreditCapacity(creditRequest, creditLimit);
        }

        // save the credit request
        CreditRequest savedCreditRequest = creditRequestRepository.save(creditRequest);

        // saves the updated reserved amount
        creditLimitRepository.save(creditLimit);

        // records the credit request creation in the audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("CREDIT_REQUEST_CREATED");
        auditLog.setEntityType("CREDIT_REQUEST");
        auditLog.setEntityId(savedCreditRequest.getId());
        auditLog.setDetails("Created credit request of " + savedCreditRequest.getAmount() + " with status " + savedCreditRequest.getStatus());
        auditLog.setActor(requester);

        auditLogRepository.save(auditLog);

        // records when credit capacity is reserved
        if (savedCreditRequest.getStatus() == CreditRequestStatus.RESERVED) {

            AuditLog reservationAuditLog = new AuditLog();
            reservationAuditLog.setAction("CREDIT_CAPACITY_RESERVED");
            reservationAuditLog.setEntityType("CREDIT_REQUEST");
            reservationAuditLog.setEntityId(savedCreditRequest.getId());
            reservationAuditLog.setDetails("Reserved credit capacity of " + savedCreditRequest.getAmount());
            reservationAuditLog.setActor(requester);

            auditLogRepository.save(reservationAuditLog);
        }

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

    // reserves available credit capacity for a credit request
    // Take this credit request and actually reserve its money from the credit limit.
    private void reserveCreditCapacity(CreditRequest creditRequest, CreditLimit creditLimit) {

        // prevents the same request from reserving capacity more than once
        // If we've already reserved this request, STOP. Don't reserve the same money twice
        if (creditRequest.getStatus() == CreditRequestStatus.RESERVED) {
            throw new IllegalArgumentException("Credit request is already reserved");
        }

        // calculates the latest available headroom before reservation
        BigDecimal availableHeadroom = creditLimit.getLimitAmount()
                .subtract(creditLimit.getUsedAmount())
                .subtract(creditLimit.getReservedAmount());

        // checks headroom again immediately before reservation
        // Does the requested money fit inside the available headroom?
        if (creditRequest.getAmount().compareTo(availableHeadroom) > 0) {
            throw new InsufficientHeadroomException(
                    "Insufficient headroom to reserve credit capacity");
        }

        // increases the reserved exposure by the request amount
        // If it does fit, we add the money to the reserved amount
        creditLimit.setReservedAmount(
                creditLimit.getReservedAmount()
                        .add(creditRequest.getAmount())
        );

        // changes the request status to RESERVED
        // The money is now successfully held for this request
        creditRequest.setStatus(CreditRequestStatus.RESERVED);
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

    // gets a credit request by ID for an authorized user
    public CreditRequestResponse getCreditRequestById(Long creditRequestId) {

        // checks that the credit request exists
        CreditRequest creditRequest = creditRequestRepository
                .findById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException("Credit request not found"));

        // gets the currently logged-in user
        User currentUser = getCurrentLoggedInUser();

        // relationship managers can only view their own requests
        // If the logged-in user is a Relationship Manager AND this request does not belong to them, block them.
        if (currentUser.getRole() == UserRole.RELATIONSHIP_MANAGER
                && !creditRequest.getRequester().getId()
                .equals(currentUser.getId())) {

            throw new AccessDeniedException("You do not have permission to view this credit request");
        }

        // returns the credit request details
        return new CreditRequestResponse(
                creditRequest.getId(),
                creditRequest.getAmount(),
                creditRequest.getStatus(),
                creditRequest.getCreditLimit().getId(),
                creditRequest.getCreditLimit().getCounterparty().getId(),
                creditRequest.getRequester().getId(),
                creditRequest.getExpiresAt(),
                creditRequest.getCreatedAt(),
                creditRequest.getUpdatedAt()
        );
    }




}