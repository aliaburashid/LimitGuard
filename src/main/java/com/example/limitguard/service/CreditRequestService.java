package com.example.limitguard.service;

import com.example.limitguard.dto.CreditRequestRequest;
import com.example.limitguard.dto.CreditRequestResponse;
import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.dto.CreditRequestReviewResponse;
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
import com.example.limitguard.enums.ApprovalDecisionType;
import com.example.limitguard.model.ApprovalDecision;
import com.example.limitguard.repository.ApprovalDecisionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class CreditRequestService {

    @Autowired
    private CreditRequestRepository creditRequestRepository;

    @Autowired
    private CreditLimitRepository creditLimitRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ApprovalDecisionRepository approvalDecisionRepository;

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

    // enforces the maker-checker rule for approval decisions
    // the user who submitted the request cannot approve or reject their own request
    private void validateMakerChecker(CreditRequest creditRequest) {

        // gets the authenticated user attempting to make the decision
        User currentUser = getCurrentLoggedInUser();

        // compares the decision maker with the user who originally submitted the request
        if (creditRequest.getRequester().getId().equals(currentUser.getId())) {

            // stops the decision before the request status or exposure can be changed
            throw new AccessDeniedException("You cannot approve or reject your own credit request");
        }
    }

    // checks whether a credit request is allowed to move
    // from its current status to the requested new status
    private void validateStatusTransition(CreditRequest creditRequest, CreditRequestStatus newStatus) {
        // gets the status the credit request is currently in
        CreditRequestStatus currentStatus = creditRequest.getStatus();

        // starts as false because a status change should only be allowed
        // when it matches one of the valid workflow transitions below
        boolean validTransition = false;

        // a request waiting for Risk approval can only:
        // 1. be approved and move to RESERVED
        // 2. be rejected and move to REJECTED
        if (currentStatus == CreditRequestStatus.PENDING_APPROVAL) {
            if (newStatus == CreditRequestStatus.RESERVED
                    || newStatus == CreditRequestStatus.REJECTED) {
                validTransition = true;
            }
        }
        // once capacity has been reserved, the request can only:
        // 1. become USED when the reserved capacity is used
        // 2. become CANCELLED and release the reserved capacity
        // 3. become EXPIRED and release the reserved capacity
        else if (currentStatus == CreditRequestStatus.RESERVED) {
            if (newStatus == CreditRequestStatus.USED
                    || newStatus == CreditRequestStatus.CANCELLED
                    || newStatus == CreditRequestStatus.EXPIRED) {

                validTransition = true;
            }
        }

        // USED, CANCELLED, REJECTED and EXPIRED do not have any
        // allowed transitions above because they are terminal statuses.
        // if the requested transition was not one of the allowed paths,
        // stop the operation before any request or exposure data is changed
        if (!validTransition) {
            throw new IllegalArgumentException(
                    "Invalid credit request status transition from "
                            + currentStatus
                            + " to "
                            + newStatus
            );
        }
    }

    // makes sure only requests waiting for approval can enter the approval workflow
    private void validateApprovalAction(CreditRequest creditRequest) {

        if (creditRequest.getStatus() != CreditRequestStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException(
                    "Only pending approval requests can be approved or rejected"
            );
        }
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

        // checks the current available headroom before accepting the request
        BigDecimal availableHeadroom = calculateAvailableHeadroom(creditLimit);

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

    // calculates how much headroom is available right now
    // headroom = total limit - used exposure - reserved exposure
    private BigDecimal calculateAvailableHeadroom(CreditLimit creditLimit) {
        return creditLimit.getLimitAmount()
                .subtract(creditLimit.getUsedAmount())
                .subtract(creditLimit.getReservedAmount());
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

        // every reservation receives an expiry time
        creditRequest.setExpiresAt(LocalDateTime.now().plusHours(24));
    }


    // approves a credit request that is waiting for Risk Officer approval
    @Transactional
    public CreditRequestResponse approveCreditRequest(Long creditRequestId) {

        // finds the credit request
        CreditRequest creditRequest = creditRequestRepository.findById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException(
                                "Credit request with id " + creditRequestId + " was not found"
                        )
                );

        // makes sure only a PENDING_APPROVAL request can be approved
        validateApprovalAction(creditRequest);

        // makes sure the requester and decision maker are different users
        // this must happen before any request status or exposure is changed
        validateMakerChecker(creditRequest);

        // only PENDING_APPROVAL can move to RESERVED
        validateStatusTransition(creditRequest, CreditRequestStatus.RESERVED);

        // gets the credit limit connected to this credit request
        CreditLimit creditLimit = creditRequest.getCreditLimit();

        // approval reserves the requested capacity using the same
        // reservation rules used by other credit requests
        reserveCreditCapacity(creditRequest, creditLimit);

        CreditRequest savedCreditRequest =
                creditRequestRepository.save(creditRequest);

        // records the final approval decision
        ApprovalDecision approvalDecision = new ApprovalDecision();

        approvalDecision.setDecision(ApprovalDecisionType.APPROVED);
        approvalDecision.setCreditRequest(savedCreditRequest);
        approvalDecision.setDecidedBy(getCurrentLoggedInUser());

        approvalDecisionRepository.save(approvalDecision);

        // records the successful approval in the audit trail
        AuditLog auditLog = new AuditLog();

        auditLog.setAction("CREDIT_REQUEST_APPROVED");
        auditLog.setEntityType("CREDIT_REQUEST");
        auditLog.setEntityId(savedCreditRequest.getId());
        auditLog.setDetails("Approved credit request of " + savedCreditRequest.getAmount() + " and reserved the credit capacity");

        auditLog.setActor(getCurrentLoggedInUser());

        auditLogRepository.save(auditLog);

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

    // rejects a credit request that is waiting for Risk Officer approval
    @Transactional
    public CreditRequestResponse rejectCreditRequest(Long creditRequestId, String reason) {

        // find the credit request
        CreditRequest creditRequest = creditRequestRepository.findById(creditRequestId)
                .orElseThrow(() -> new CreditRequestNotFoundException(
                        "Credit request with id " + creditRequestId + " was not found"));

        // makes sure the requester and decision maker are different users
        // this must happen before the request can be rejected
        validateMakerChecker(creditRequest);

        // only PENDING_APPROVAL can move to REJECTED
        validateStatusTransition(creditRequest, CreditRequestStatus.REJECTED);

        // rejection does not reserve or use any credit capacity
        creditRequest.setStatus(CreditRequestStatus.REJECTED);

        CreditRequest savedCreditRequest =
                creditRequestRepository.save(creditRequest);

        // records the final rejection decision and its reason
        ApprovalDecision approvalDecision = new ApprovalDecision();

        approvalDecision.setDecision(ApprovalDecisionType.REJECTED);
        approvalDecision.setReason(reason);
        approvalDecision.setCreditRequest(savedCreditRequest);
        approvalDecision.setDecidedBy(getCurrentLoggedInUser());

        approvalDecisionRepository.save(approvalDecision);

        // record who rejected the request and why
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("CREDIT_REQUEST_REJECTED");
        auditLog.setEntityType("CREDIT_REQUEST");
        auditLog.setEntityId(savedCreditRequest.getId());
        auditLog.setDetails("Rejected credit request of " + savedCreditRequest.getAmount() + ". Reason: " + reason);
        auditLog.setActor(getCurrentLoggedInUser());

        auditLogRepository.save(auditLog);

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

    // marks reserved credit capacity as used
    @Transactional
    public CreditRequestResponse markCreditRequestAsUsed(Long creditRequestId) {

        // checks that the credit request exists
        CreditRequest creditRequest = creditRequestRepository
                .findById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException("Credit request not found"));

        // gets the currently logged-in user
        User currentUser = getCurrentLoggedInUser();

        // prevents a Relationship Manager from using another users request
        if (!creditRequest.getRequester().getId()
                .equals(currentUser.getId())) {

            throw new AccessDeniedException(
                    "You do not have permission to use this credit request");
        }

        // checks that the current request status is allowed to move to USED
        // this prevents invalid transitions such as CANCELLED -> USED
        // or EXPIRED -> USED before any exposure amounts are changed
        validateStatusTransition(creditRequest, CreditRequestStatus.USED);

        CreditLimit creditLimit = creditRequest.getCreditLimit();

        // removes the request amount from reserved exposure
        creditLimit.setReservedAmount(
                creditLimit.getReservedAmount()
                        .subtract(creditRequest.getAmount())
        );

        // adds the same amount to used exposure
        creditLimit.setUsedAmount(
                creditLimit.getUsedAmount()
                        .add(creditRequest.getAmount())
        );

        // changes the request status from RESERVED to USED
        creditRequest.setStatus(CreditRequestStatus.USED);

        // saves the updated request and credit limit
        CreditRequest savedCreditRequest =
                creditRequestRepository.save(creditRequest);

        creditLimitRepository.save(creditLimit);

        // records the operation in the audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("CREDIT_CAPACITY_USED");
        auditLog.setEntityType("CREDIT_REQUEST");
        auditLog.setEntityId(savedCreditRequest.getId());
        auditLog.setDetails("Marked reserved credit capacity of " + savedCreditRequest.getAmount() + " as used");
        auditLog.setActor(currentUser);

        auditLogRepository.save(auditLog);

        // returns the updated credit request
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

    // cancels a reserved credit request
    @Transactional
    public CreditRequestResponse cancelCreditRequest(Long creditRequestId) {

        // checks that the credit request exists
        CreditRequest creditRequest = creditRequestRepository
                .findById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException("Credit request not found"));

        // gets the currently logged-in user
        User currentUser = getCurrentLoggedInUser();

        // prevents a Relationship Manager from cancelling another users request
        if (!creditRequest.getRequester().getId()
                .equals(currentUser.getId())) {

            throw new AccessDeniedException(
                    "You do not have permission to cancel this credit request");
        }

        // checks that the current request status is allowed to move to CANCELLED
        // this prevents invalid transitions such as USED -> CANCELLED
        // or EXPIRED -> CANCELLED before reserved capacity is released
        validateStatusTransition(creditRequest, CreditRequestStatus.CANCELLED);


        CreditLimit creditLimit = creditRequest.getCreditLimit();

        // makes sure reserved capacity cannot become negative
        // Do we have enough reservedAmount to remove this request amount?
        if (creditLimit.getReservedAmount()
                .compareTo(creditRequest.getAmount()) < 0) {

            throw new IllegalArgumentException(
                    "Reserved amount cannot become negative");
        }

        // releases the reserved capacity
        creditLimit.setReservedAmount(
                creditLimit.getReservedAmount()
                        .subtract(creditRequest.getAmount())
        );

        // changes the request status to CANCELLED
        creditRequest.setStatus(CreditRequestStatus.CANCELLED);

        // saves the updated request and credit limit
        CreditRequest savedCreditRequest =
                creditRequestRepository.save(creditRequest);

        creditLimitRepository.save(creditLimit);

        // records the cancellation in the audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("CREDIT_REQUEST_CANCELLED");
        auditLog.setEntityType("CREDIT_REQUEST");
        auditLog.setEntityId(savedCreditRequest.getId());
        auditLog.setDetails("Cancelled reserved credit request of " + savedCreditRequest.getAmount() + " and released the reserved capacity");
        auditLog.setActor(currentUser);

        auditLogRepository.save(auditLog);

        // returns the updated credit request
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

    // expires an unused credit reservation
    @Transactional
    public CreditRequestResponse expireCreditRequest(Long creditRequestId) {

        // checks that the credit request exists
        CreditRequest creditRequest = creditRequestRepository
                .findById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException("Credit request not found"));

        // checks that the current request status is allowed to move to EXPIRED
        // this prevents terminal requests such as USED or CANCELLED
        // from being expired and releasing capacity again
        validateStatusTransition(creditRequest, CreditRequestStatus.EXPIRED);

        // makes sure the reservation has an expiry time
        if (creditRequest.getExpiresAt() == null) {
            throw new IllegalArgumentException("Credit request does not have an expiry time");
        }

        // prevents the reservation from expiring before its expiry time
        if (LocalDateTime.now().isBefore(creditRequest.getExpiresAt())) {
            throw new IllegalArgumentException("Credit request cannot expire before its expiry time");
        }

        // gets the credit limit connected to this reservation
        CreditLimit creditLimit = creditRequest.getCreditLimit();

        // makes sure reserved capacity cannot become negative
        // Do we have enough reservedAmount to remove this request amount?
        // if yes -> subtract it if no -> stop
        // that would make the reserved amount negative
        if (creditLimit.getReservedAmount()
                .compareTo(creditRequest.getAmount()) < 0) {
            throw new IllegalArgumentException("Reserved amount cannot become negative");
        }

        // releases the unused reserved capacity
        creditLimit.setReservedAmount(
                creditLimit.getReservedAmount()
                        .subtract(creditRequest.getAmount())
        );

        // saves the updated credit limit
        creditLimitRepository.save(creditLimit);

        // changes the request status from RESERVED to EXPIRED
        creditRequest.setStatus(CreditRequestStatus.EXPIRED);

        // saves the expired request
        CreditRequest savedCreditRequest = creditRequestRepository.save(creditRequest);

        // records the automatic expiry in the audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("CREDIT_REQUEST_EXPIRED");
        auditLog.setEntityType("CREDIT_REQUEST");
        auditLog.setEntityId(savedCreditRequest.getId());
        auditLog.setDetails("Credit reservation of " + savedCreditRequest.getAmount() + " expired and released the reserved capacity");

        auditLogRepository.save(auditLog);

        // returns the updated credit request
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

    // gets the information a Risk Officer needs to review a pending credit request
    public CreditRequestReviewResponse reviewCreditRequest(Long creditRequestId) {

        // finds the requested credit request
        CreditRequest creditRequest = creditRequestRepository.findById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException(
                                "Credit request with id " + creditRequestId + " was not found"
                        )
                );

        // only requests waiting for approval can enter the approval-review workflow
        if (creditRequest.getStatus() != CreditRequestStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException( "Credit request is not awaiting approval");
        }

        CreditLimit creditLimit = creditRequest.getCreditLimit();

        // shows the Risk Officer the headroom based on the latest exposure values
        // not values from when the credit request was originally submitted
        BigDecimal availableHeadroom = calculateAvailableHeadroom(creditLimit);

        String requesterName =
                creditRequest.getRequester().getFirstName()
                        + " "
                        + creditRequest.getRequester().getLastName();

        return new CreditRequestReviewResponse(
                creditRequest.getId(),
                creditRequest.getAmount(),
                creditRequest.getStatus(),
                creditRequest.getCreatedAt(),

                creditRequest.getRequester().getId(),
                requesterName,

                creditLimit.getCounterparty().getId(),
                creditLimit.getCounterparty().getName(),

                creditLimit.getId(),
                creditLimit.getLimitAmount(),
                creditLimit.getUsedAmount(),
                creditLimit.getReservedAmount(),
                availableHeadroom
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

    // gets all credit requests that are waiting for Risk Officer approval
    public Page<CreditRequestResponse> getPendingCreditRequests(Pageable pageable) {

        // finds only requests that currently have the PENDING_APPROVAL status
        // pagination and sorting are applied through the Pageable object
        Page<CreditRequest> pendingRequests =
                creditRequestRepository.findByStatus(
                        CreditRequestStatus.PENDING_APPROVAL,
                        pageable
                );

        // converts each CreditRequest entity into a CreditRequestResponse DTO
        // so the API does not return the database entity directly
        return pendingRequests.map(creditRequest ->
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