package com.example.creditservice.service;

import com.example.creditservice.dto.CreditRequestRequest;
import com.example.creditservice.dto.CreditRequestResponse;
import com.example.creditservice.enums.CreditRequestStatus;
import com.example.creditservice.dto.CreditRequestReviewResponse;
import com.example.creditservice.exception.CreditLimitNotFoundException;
import com.example.creditservice.exception.CreditRequestNotFoundException;
import com.example.creditservice.exception.InsufficientHeadroomException;
import com.example.creditservice.exception.InvalidCreditRequestStateException;
import com.example.creditservice.exception.InvalidCreditRequestOperationException;
import com.example.creditservice.model.*;
import com.example.creditservice.repository.CreditLimitRepository;
import com.example.creditservice.repository.CreditRequestRepository;
import com.example.creditservice.repository.GroupCreditLimitRepository;
import com.example.creditservice.exception.GroupCreditLimitNotFoundException;
import com.example.creditservice.security.InstitutionAccessService;
import com.example.creditservice.security.AuthenticatedCreditUser;
import com.example.creditservice.security.CurrentUserSecurity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.creditservice.enums.CounterpartyStatus;
import com.example.creditservice.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.creditservice.enums.ApprovalDecisionType;
import com.example.creditservice.repository.ApprovalDecisionRepository;
import com.example.creditservice.repository.CorporateGroupMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class CreditRequestService {

    // Logs important credit request workflow events
    private static final Logger logger = LoggerFactory.getLogger(CreditRequestService.class);

    @Autowired
    private CreditRequestRepository creditRequestRepository;

    @Autowired
    private CreditLimitRepository creditLimitRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ApprovalDecisionRepository approvalDecisionRepository;

    // Records notification events in the same transaction as credit changes.
    // Events will later be delivered to Notification Service through Kafka.
    @Autowired
    private NotificationOutboxService notificationOutboxService;

    @Autowired
    private InstitutionAccessService institutionAccessService;

    // finds which corporate group a counterparty belongs to
    @Autowired
    private CorporateGroupMembershipRepository corporateGroupMembershipRepository;

    // reuses the existing group headroom calculation
    @Autowired
    private GroupCreditLimitService groupCreditLimitService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GroupCreditLimitRepository groupCreditLimitRepository;

    //---------------------------------------------------------------------------

    // gets the currently logged-in user
    private AuthenticatedCreditUser getCurrentLoggedInUser() {
        return CurrentUserSecurity.getCurrentUser();
    }

    /**
     * Records credit request activity for traceability
     * The actor can be null for automatic operations such as reservation expiry
     */
    private void recordAuditLog(String action, CreditRequest creditRequest, String details, AuthenticatedCreditUser actor) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType("CREDIT_REQUEST");
        auditLog.setEntityId(creditRequest.getId());
        auditLog.setDetails(details);
        auditLog.setActorId(actor == null ? null : actor.getUserId());
        auditLog.setFinancialInstitutionId(creditRequest.getCreditLimit().getFinancialInstitutionId());
        auditLogRepository.save(auditLog);
    }

    // enforces the maker-checker rule for approval decisions
    // the user who submitted the request cannot approve or reject their own request
    private void validateMakerChecker(CreditRequest creditRequest) {

        // gets the authenticated user attempting to make the decision
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // compares the decision maker with the user who originally submitted the request
        if (creditRequest.getRequesterId().equals(currentUser.getUserId())) {

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

            // Log the rejected workflow transition
            logger.warn(
                    "Invalid credit request status transition from {} to {} for request ID {}",
                    currentStatus,
                    newStatus,
                    creditRequest.getId()
            );

            throw new InvalidCreditRequestStateException(
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
            // logs why a competing approval or rejection cannot proceed
            logger.warn("[{}] APPROVAL VALIDATION | FAILED | Request ID={} | Current Status={}",
                    Thread.currentThread().getName(),
                    creditRequest.getId(),
                    creditRequest.getStatus());

            throw new InvalidCreditRequestStateException(
                    "Only pending approval requests can be approved or rejected"
            );
        }
    }


    // Creates a new credit request for a counterparty
    @Transactional
    public CreditRequestResponse createCreditRequest(CreditRequestRequest creditRequestRequest) {

        // Log when a Relationship Manager starts creating a credit request
        logger.info("[{}] CREATE | START | Limit ID={} | Amount={}",
                Thread.currentThread().getName(),
                creditRequestRequest.getCreditLimitId(),
                creditRequestRequest.getAmount());

        // STEP 1: Find the credit limit without locking it yet
        // We need to know which company this credit limit belongs to
        CreditLimit creditLimitLookup = creditLimitRepository
                .findById(creditRequestRequest.getCreditLimitId())
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found"));

        // Make sure the credit limit belongs to the logged-in user's bank
        institutionAccessService.requireInstitution(
                creditLimitLookup.getFinancialInstitutionId()
        );

        // STEP 2: Check whether this company belongs to a corporate group
        Optional<CorporateGroupMembership> groupMembership =
                corporateGroupMembershipRepository.findByCounterpartyId(
                        creditLimitLookup.getCounterparty().getId()
                );

        if (groupMembership.isPresent()) {

            // Get the corporate group ID
            Long corporateGroupId =
                    groupMembership.get().getCorporateGroup().getId();

            // Get the bank ID
            Long institutionId =
                    creditLimitLookup.getFinancialInstitutionId();

            // Find the shared credit limit for this corporate group
            GroupCreditLimit groupLimit = groupCreditLimitRepository
                    .findByFinancialInstitutionIdAndCorporateGroupId(
                            institutionId,
                            corporateGroupId
                    )
                    .orElseThrow(() -> new GroupCreditLimitNotFoundException(
                            "Group credit limit not found for corporate group ID: "
                                    + corporateGroupId
                    ));

            // Log that we are about to lock the corporate group
            logger.info("[{}] GROUP ALLOCATION | WAITING FOR LOCK | Group Limit ID={}",
                    Thread.currentThread().getName(),
                    groupLimit.getId());

            // STEP 3: Lock the shared corporate group credit limit FIRST
            // If another Relationship Manager is using this group, we wait
            groupCreditLimitRepository.findWithLockById(groupLimit.getId())
                    .orElseThrow(() -> new GroupCreditLimitNotFoundException(
                            "Group credit limit not found"
                    ));

            // Log that we successfully acquired the group lock
            logger.info("[{}] GROUP ALLOCATION | LOCK ACQUIRED | Group Limit ID={}",
                    Thread.currentThread().getName(),
                    groupLimit.getId());
        }

        logger.info("[{}] CREATE | WAITING FOR LOCK | Limit ID={}",
                Thread.currentThread().getName(),
                creditRequestRequest.getCreditLimitId());

        // STEP 4: Lock the individual company's credit limit
        CreditLimit creditLimit = creditLimitRepository
                .findWithLockById(creditRequestRequest.getCreditLimitId())
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found"));

        logger.info("[{}] CREATE | LOCK ACQUIRED | Limit ID={}",
                Thread.currentThread().getName(),
                creditLimit.getId());

        // Refresh the credit limit to get its latest balance from PostgreSQL
        entityManager.refresh(creditLimit, LockModeType.PESSIMISTIC_WRITE);

        // Check bank ownership again using the locked credit limit
        institutionAccessService.requireInstitution(
                creditLimit.getFinancialInstitutionId()
        );

        // STEP 5: Prevent requests for frozen or closed companies
        if (creditLimit.getCounterparty().getStatus() == CounterpartyStatus.FROZEN
                || creditLimit.getCounterparty().getStatus() == CounterpartyStatus.CLOSED) {

            logger.warn("[{}] CREATE | FAILED | Limit ID={} | Counterparty status={}",
                    Thread.currentThread().getName(),
                    creditLimit.getId(),
                    creditLimit.getCounterparty().getStatus());

            throw new InvalidCreditRequestOperationException(
                    "Credit requests cannot be created for a frozen or closed counterparty");
        }

        // STEP 6: Calculate how much credit the company still has available
        BigDecimal availableHeadroom = calculateAvailableHeadroom(creditLimit);

        // Reject the request if the company does not have enough credit
        if (creditRequestRequest.getAmount().compareTo(availableHeadroom) > 0) {

            logger.warn("[{}] CREATE | FAILED | Limit ID={} | Insufficient headroom | Requested={} | Available={}",
                    Thread.currentThread().getName(),
                    creditLimit.getId(),
                    creditRequestRequest.getAmount(),
                    availableHeadroom);

            throw new InsufficientHeadroomException(
                    "Requested amount exceeds available headroom");
        }

        // Get the Relationship Manager who submitted the request
        AuthenticatedCreditUser requester = getCurrentLoggedInUser();

        // STEP 7: Create the new credit request
        CreditRequest creditRequest = new CreditRequest();

        creditRequest.setAmount(creditRequestRequest.getAmount());
        creditRequest.setCreditLimit(creditLimit);
        creditRequest.setRequesterId(requester.getUserId());

        // Requests above 500,000 must be approved by a Risk Officer
        if (creditRequestRequest.getAmount()
                .compareTo(new BigDecimal("500000")) > 0) {

            creditRequest.setStatus(CreditRequestStatus.PENDING_APPROVAL);

        } else {

            // Requests of 500,000 or less reserve credit immediately
            // This also checks the corporate group's available headroom
            reserveCreditCapacity(creditRequest, creditLimit);
        }

        // STEP 8: Save the new credit request
        CreditRequest savedCreditRequest =
                creditRequestRepository.save(creditRequest);

        // Save the updated company's reserved amount
        creditLimitRepository.save(creditLimit);

        // Record the creation in the audit log
        recordAuditLog(
                "CREDIT_REQUEST_CREATED",
                savedCreditRequest,
                "Created credit request of " + savedCreditRequest.getAmount()
                        + " with status " + savedCreditRequest.getStatus(),
                requester
        );

        logger.info("[{}] CREATE | SUCCESS | Request ID={} | Status={}",
                Thread.currentThread().getName(),
                savedCreditRequest.getId(),
                savedCreditRequest.getStatus());

        // STEP 9: If credit was reserved immediately, record the reservation
        if (savedCreditRequest.getStatus() == CreditRequestStatus.RESERVED) {

            // Record the reservation in the audit log
            recordAuditLog(
                    "CREDIT_CAPACITY_RESERVED",
                    savedCreditRequest,
                    "Reserved credit capacity of " + savedCreditRequest.getAmount(),
                    requester
            );
        }

        // STEP 10: Save the information needed for the SSE notification
        // This applies to both RESERVED and PENDING_APPROVAL requests
        Long institutionId = creditLimit.getFinancialInstitutionId();
        Long requestId = savedCreditRequest.getId();
        CreditRequestStatus status = savedCreditRequest.getStatus();

        // STEP 11: Register the SSE notification to run after a successful commit
        // This prevents notifying users about requests that were rolled back
        // Record the event in PostgreSQL within the current transaction.
        // The Kafka publisher will deliver it after the transaction commits.
        notificationOutboxService.recordCreditRequestEvent(
                institutionId,
                requestId,
                savedCreditRequest.getRequesterId(),
                status,
                "CREDIT_REQUEST_CREATED",
                null
        );

        // Return the created request to the frontend
        return toResponse(savedCreditRequest);
    }


    // calculates how much headroom is available right now
    // headroom = total limit - used exposure - reserved exposure
    private BigDecimal calculateAvailableHeadroom(CreditLimit creditLimit) {
        return creditLimit.getLimitAmount()
                .subtract(creditLimit.getUsedAmount())
                .subtract(creditLimit.getReservedAmount());
    }

    // Reserves credit capacity for a request
    // This method is called when creating or approving a credit request
    private void reserveCreditCapacity(CreditRequest creditRequest, CreditLimit creditLimit) {

        // STEP 1: Make sure this request has not already reserved credit
        // We don't want the same request to reserve money twice
        if (creditRequest.getStatus() == CreditRequestStatus.RESERVED) {
            throw new InvalidCreditRequestStateException(
                    "Credit request is already reserved"
            );
        }

        // STEP 2: Calculate the company's available credit
        // Available headroom = total limit - used amount - reserved amount
        BigDecimal availableHeadroom = calculateAvailableHeadroom(creditLimit);

        // Check if the requested amount is greater than the company's available credit
        if (creditRequest.getAmount().compareTo(availableHeadroom) > 0) {

            logger.warn("[{}] RESERVE | FAILED | Limit ID={} | Requested={} | Available={}",
                    Thread.currentThread().getName(),
                    creditLimit.getId(),
                    creditRequest.getAmount(),
                    availableHeadroom);

            throw new InsufficientHeadroomException(
                    "Insufficient headroom to reserve credit capacity"
            );
        }

        // STEP 3: Check whether the company belongs to a corporate group
        Optional<CorporateGroupMembership> membership =
                corporateGroupMembershipRepository.findByCounterpartyId(
                        creditLimit.getCounterparty().getId()
                );

        // If the company belongs to a group, we must also check the group's credit
        if (membership.isPresent()) {

            // Get the corporate group ID
            Long corporateGroupId =
                    membership.get().getCorporateGroup().getId();

            // STEP 4: Calculate the group's available credit
            // Group headroom = group limit - total used and reserved amounts
            // of all companies belonging to this corporate group
            //
            // IMPORTANT:
            // The method calling reserveCreditCapacity() must lock the corporate group first.
            // createCreditRequest() locks the group before creating a reservation.
            // PostgreSQL holds the lock until the transaction finishes.
            BigDecimal groupHeadroom = groupCreditLimitService
                    .calculateGroupHeadroom(corporateGroupId);

            // Log the requested amount and remaining group credit
            logger.info("[{}] GROUP ALLOCATION | HEADROOM CHECK | Group ID={} | Requested={} | Available={}",
                    Thread.currentThread().getName(),
                    corporateGroupId,
                    creditRequest.getAmount(),
                    groupHeadroom);

            // STEP 5: Check whether the group has enough credit
            // Even if this company has enough credit individually,
            // we cannot exceed the shared corporate group limit
            if (creditRequest.getAmount().compareTo(groupHeadroom) > 0) {

                logger.warn("[{}] GROUP ALLOCATION | FAILED | Group ID={} | Requested={} | Available={}",
                        Thread.currentThread().getName(),
                        corporateGroupId,
                        creditRequest.getAmount(),
                        groupHeadroom);

                throw new InsufficientHeadroomException(
                        "Requested amount exceeds corporate group available headroom"
                );
            }
        }

        // STEP 6: Reserve the requested amount
        // Add the requested money to the company's reserved balance
        creditLimit.setReservedAmount(
                creditLimit.getReservedAmount().add(creditRequest.getAmount())
        );

        // STEP 7: Change the request status to RESERVED
        creditRequest.setStatus(CreditRequestStatus.RESERVED);

        // STEP 8: Set the reservation to expire after 24 hours
        creditRequest.setExpiresAt(LocalDateTime.now().plusHours(24));

        // Log that the credit capacity has been reserved
        logger.info("[{}] RESERVE | SUCCESS | Limit ID={} | Amount={}",
                Thread.currentThread().getName(),
                creditLimit.getId(),
                creditRequest.getAmount());
    }

    // Approves a credit request that is waiting for Risk Officer approval
    // Transactional: all database changes succeed together, or are rolled back
    @Transactional
    public CreditRequestResponse approveCreditRequest(Long creditRequestId) {

        logger.info("[{}] APPROVE | START | Request ID={}",
                Thread.currentThread().getName(),
                creditRequestId);

        // STEP 1: Lock the credit request
        // Prevents two Risk Officers from approving the same request at the same time
        logger.info("[{}] APPROVE | WAITING FOR LOCK | Request ID={}",
                Thread.currentThread().getName(),
                creditRequestId);

        CreditRequest creditRequest = creditRequestRepository
                .findWithLockById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException(
                                "Credit request with id " + creditRequestId + " was not found"
                        )
                );

        logger.info("[{}] APPROVE | LOCK ACQUIRED | Request ID={}",
                Thread.currentThread().getName(),
                creditRequestId);

        // DEMO ONLY: Keep the request lock for 5 seconds
        // This lets us see another Risk Officer waiting for the same request
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Approval interrupted", e);
        }

        // STEP 2: Make sure the request belongs to the logged-in user's bank
        institutionAccessService.requireInstitution(
                creditRequest.getCreditLimit().getFinancialInstitutionId()
        );

        // Make sure the request is waiting for approval
        validateApprovalAction(creditRequest);

        // The person approving must be different from the person who created it
        validateMakerChecker(creditRequest);

        // Only PENDING_APPROVAL requests can become RESERVED
        validateStatusTransition(creditRequest, CreditRequestStatus.RESERVED);

        // STEP 3: Check whether this counterparty belongs to a corporate group
        // We use the counterparty ID from the credit request's credit limit
        Long counterpartyId = creditRequest.getCreditLimit()
                .getCounterparty().getId();

        Optional<CorporateGroupMembership> membership =
                corporateGroupMembershipRepository.findByCounterpartyId(counterpartyId);

        // STEP 4: If the counterparty belongs to a group, lock the shared group limit FIRST
        if (membership.isPresent()) {

            // Get the corporate group ID
            Long corporateGroupId =
                    membership.get().getCorporateGroup().getId();

            // Get the bank that owns this credit request
            Long institutionId = creditRequest.getCreditLimit()
                    .getFinancialInstitutionId();

            // Find the corporate group's credit limit for this bank
            GroupCreditLimit groupLimit = groupCreditLimitRepository
                    .findByFinancialInstitutionIdAndCorporateGroupId(
                            institutionId,
                            corporateGroupId
                    )
                    .orElseThrow(() ->
                            new GroupCreditLimitNotFoundException(
                                    "Corporate group credit limit not found"
                            )
                    );

            logger.info("[{}] APPROVE | WAITING FOR GROUP LOCK | Group ID={}",
                    Thread.currentThread().getName(),
                    corporateGroupId);

            // Lock the corporate group's credit limit in PostgreSQL
            // Another transaction using the same group lock must wait
            groupCreditLimitRepository.findWithLockById(groupLimit.getId())
                    .orElseThrow(() ->
                            new GroupCreditLimitNotFoundException(
                                    "Corporate group credit limit not found"
                            )
                    );

            logger.info("[{}] APPROVE | GROUP LOCK ACQUIRED | Group ID={}",
                    Thread.currentThread().getName(),
                    corporateGroupId);

            // PostgreSQL keeps this lock until the transaction finishes
            // We now lock the individual credit limit while holding the group lock
        }

        // STEP 5: Lock the individual company's credit limit
        // Prevents different requests from reserving the same individual credit
        logger.info("[{}] APPROVE | WAITING FOR LOCK | Limit ID={}",
                Thread.currentThread().getName(),
                creditRequest.getCreditLimit().getId());

        CreditLimit creditLimit = creditLimitRepository
                .findWithLockById(creditRequest.getCreditLimit().getId())
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found")
                );

        logger.info("[{}] APPROVE | LOCK ACQUIRED | Limit ID={}",
                Thread.currentThread().getName(),
                creditLimit.getId());

        // STEP 6: Refresh the individual credit limit
        // Read the latest balance from PostgreSQL after acquiring the lock
        // This prevents using an outdated individual reserved amount
        entityManager.refresh(creditLimit, LockModeType.PESSIMISTIC_WRITE);

        // STEP 7: Reserve the requested credit
        // reserveCreditCapacity() checks:
        // 1. Individual available headroom
        // 2. Corporate group available headroom (if the company belongs to a group)
        //
        // Both required locks are already held before these checks
        reserveCreditCapacity(creditRequest, creditLimit);

        // Save the approved request with its new RESERVED status
        CreditRequest savedCreditRequest =
                creditRequestRepository.save(creditRequest);

        // Save the updated reserved balance for the individual credit limit
        creditLimitRepository.save(creditLimit);

        // STEP 8: Record the Risk Officer's approval decision
        ApprovalDecision approvalDecision = new ApprovalDecision();

        approvalDecision.setDecision(ApprovalDecisionType.APPROVED);
        approvalDecision.setCreditRequest(savedCreditRequest);
        approvalDecision.setDecidedById(getCurrentLoggedInUser().getUserId());

        approvalDecisionRepository.save(approvalDecision);

        // STEP 9: Record the successful approval in the audit trail
        recordAuditLog(
                "CREDIT_REQUEST_APPROVED",
                savedCreditRequest,
                "Approved credit request of " + savedCreditRequest.getAmount()
                        + " and reserved the credit capacity",
                getCurrentLoggedInUser()
        );

        // STEP 10: Get the information needed for notifications.
        
        Long requestId = savedCreditRequest.getId();
        Long institutionId = creditLimit.getFinancialInstitutionId();
        CreditRequestStatus status = savedCreditRequest.getStatus();

        // STEP 11: Wait until the database transaction successfully commits.
        // Record the event in PostgreSQL within the current transaction.
        // The Kafka publisher will deliver it after the transaction commits.
        notificationOutboxService.recordCreditRequestEvent(
                institutionId,
                requestId,
                savedCreditRequest.getRequesterId(),
                status,
                "CREDIT_REQUEST_APPROVED",
                null
        );

        logger.info("[{}] APPROVE | SUCCESS | Request ID={} | Status={}",
                Thread.currentThread().getName(),
                savedCreditRequest.getId(),
                savedCreditRequest.getStatus());

        return toResponse(savedCreditRequest);
    }


    // rejects a credit request that is waiting for Risk Officer approval
    @Transactional
    public CreditRequestResponse rejectCreditRequest(Long creditRequestId, String reason) {

        logger.info("[{}] REJECT | START | Request ID={}",
                Thread.currentThread().getName(),
                creditRequestId);

        logger.info("[{}] REJECT | WAITING FOR LOCK | Request ID={}",
                Thread.currentThread().getName(),
                creditRequestId);

        // locks the credit request so it cannot be approved and rejected at the same time
        CreditRequest creditRequest = creditRequestRepository
                .findWithLockById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException(
                                "Credit request with id " + creditRequestId + " was not found"));

        logger.info("[{}] REJECT | LOCK ACQUIRED | Request ID={}",
                Thread.currentThread().getName(),
                creditRequestId);

        institutionAccessService.requireInstitution(creditRequest.getCreditLimit().getFinancialInstitutionId());

        // makes sure only a PENDING_APPROVAL request can be rejected
        validateApprovalAction(creditRequest);

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
        approvalDecision.setDecidedById(getCurrentLoggedInUser().getUserId());

        approvalDecisionRepository.save(approvalDecision);

        // record who rejected the request and why
        // save which bank rejected the credit request
        recordAuditLog(
                "CREDIT_REQUEST_REJECTED",
                savedCreditRequest,
                "Rejected credit request of " + savedCreditRequest.getAmount() + ". Reason: " + reason,
                getCurrentLoggedInUser()
        );

        // save the information needed for the notifications
        
        Long requestId = savedCreditRequest.getId();
        Long institutionId = creditRequest.getCreditLimit().getFinancialInstitutionId();
        CreditRequestStatus status = savedCreditRequest.getStatus();
        String rejectionReason = reason;

        // register a task that runs after the database transaction commits
        // Record the event in PostgreSQL within the current transaction.
        // The Kafka publisher will deliver it after the transaction commits.
        notificationOutboxService.recordCreditRequestEvent(
                institutionId,
                requestId,
                savedCreditRequest.getRequesterId(),
                status,
                "CREDIT_REQUEST_REJECTED",
                rejectionReason
        );

        // Log the successful rejection
        logger.info("[{}] REJECT | SUCCESS | Request ID={} | Status={}",
                Thread.currentThread().getName(),
                savedCreditRequest.getId(),
                savedCreditRequest.getStatus());

        return toResponse(savedCreditRequest);
    }

    // marks reserved credit capacity as used
    @Transactional
    public CreditRequestResponse markCreditRequestAsUsed(Long creditRequestId) {

        logger.info("[{}] MARK_USED | START | Request ID={}",
                Thread.currentThread().getName(),
                creditRequestId);

        logger.info("[{}] MARK_USED | WAITING FOR LOCK | Request ID={}",
                Thread.currentThread().getName(),
                creditRequestId);

        // locks the credit request so it cannot be used twice at the same time
        CreditRequest creditRequest = creditRequestRepository
                .findWithLockById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException("Credit request not found"));

        logger.info("[{}] MARK_USED | LOCK ACQUIRED | Request ID={}",
                Thread.currentThread().getName(),
                creditRequestId);

        institutionAccessService.requireInstitution(creditRequest.getCreditLimit().getFinancialInstitutionId());

        // gets the currently logged-in user
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // prevents a Relationship Manager from using another users request
        if (!creditRequest.getRequesterId()
                .equals(currentUser.getUserId())) {

            throw new AccessDeniedException(
                    "You do not have permission to use this credit request");
        }

        // checks that the current request status is allowed to move to USED
        // this prevents invalid transitions such as CANCELLED -> USED
        // or EXPIRED -> USED before any exposure amounts are changed
        validateStatusTransition(creditRequest, CreditRequestStatus.USED);

        // checks that the reservation has not passed its expiry time
        // even if the scheduler has not yet changed the status to EXPIRED
        if (creditRequest.getExpiresAt() == null
                || !LocalDateTime.now().isBefore(creditRequest.getExpiresAt())) {

            // logs why the request cannot be marked as USED
            logger.warn("[{}] MARK_USED | FAILED | Request ID={} | Reservation expired or missing expiry time",
                    Thread.currentThread().getName(),
                    creditRequestId);

            throw new InvalidCreditRequestStateException(
                    "Credit reservation has expired or has no expiry time");
        }

        logger.info("[{}] MARK_USED | WAITING FOR LOCK | Limit ID={}",
                Thread.currentThread().getName(),
                creditRequest.getCreditLimit().getId());

        // locks the credit limit before updating the reserved and used amounts
        CreditLimit creditLimit = creditLimitRepository
                .findWithLockById(creditRequest.getCreditLimit().getId())
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found"));

        logger.info("[{}] MARK_USED | LOCK ACQUIRED | Limit ID={}",
                Thread.currentThread().getName(),
                creditLimit.getId());

        // gets the latest balance from the database
        entityManager.refresh(creditLimit, LockModeType.PESSIMISTIC_WRITE);

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
        // save which bank used the credit
        recordAuditLog(
                "CREDIT_CAPACITY_USED",
                savedCreditRequest,
                "Marked reserved credit capacity of " + savedCreditRequest.getAmount() + " as used",
                currentUser
        );

        // Notify the institution only after the new exposure balances are committed.
        Long institutionId = creditLimit.getFinancialInstitutionId();
        Long requestId = savedCreditRequest.getId();
        CreditRequestStatus status = savedCreditRequest.getStatus();
        // Record the event in PostgreSQL within the current transaction.
        // The Kafka publisher will deliver it after the transaction commits.
        notificationOutboxService.recordCreditRequestEvent(
                institutionId,
                requestId,
                savedCreditRequest.getRequesterId(),
                status,
                "CREDIT_REQUEST_USED",
                null
        );

        // Log the successful use of reserved capacity
        logger.info("[{}] MARK_USED | SUCCESS | Request ID={} | Status={}",
                Thread.currentThread().getName(),
                savedCreditRequest.getId(),
                savedCreditRequest.getStatus());

        // returns the updated credit request
        return toResponse(savedCreditRequest);
    }


    // cancels a reserved credit request
    @Transactional
    public CreditRequestResponse cancelCreditRequest(Long creditRequestId) {

        // logs the start of a cancellation attempt
        logger.info("[{}] CANCEL | START | Request ID={}",
                Thread.currentThread().getName(), creditRequestId);

        // logs the attempt to acquire the request row lock
        logger.info("[{}] CANCEL | WAITING FOR LOCK | Request ID={}",
                Thread.currentThread().getName(), creditRequestId);

        // locks the credit request so it cannot be canceled or changed twice at the same time
        CreditRequest creditRequest = creditRequestRepository
                .findWithLockById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException("Credit request not found"));

        // confirms the request row lock has been acquired
        logger.info("[{}] CANCEL | LOCK ACQUIRED | Request ID={}",
                Thread.currentThread().getName(), creditRequestId);

        institutionAccessService.requireInstitution(
                creditRequest.getCreditLimit().getFinancialInstitutionId()
        );

        // gets the currently logged-in user
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // prevents a Relationship Manager from cancelling another users request
        if (!creditRequest.getRequesterId()
                .equals(currentUser.getUserId())) {

            throw new AccessDeniedException(
                    "You do not have permission to cancel this credit request");
        }

        // checks that the current request status is allowed to move to CANCELLED
        // this prevents invalid transitions such as USED -> CANCELLED
        // or EXPIRED -> CANCELLED before reserved capacity is released
        validateStatusTransition(creditRequest, CreditRequestStatus.CANCELLED);

        // logs the attempt to acquire the credit limit row lock
        logger.info("[{}] CANCEL | WAITING FOR LOCK | Limit ID={}",
                Thread.currentThread().getName(), creditRequest.getCreditLimit().getId());

        // locks the credit limit before releasing the reserved amount
        CreditLimit creditLimit = creditLimitRepository
                .findWithLockById(creditRequest.getCreditLimit().getId())
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found"));

        // confirms the credit limit row lock has been acquired
        logger.info("[{}] CANCEL | LOCK ACQUIRED | Limit ID={}",
                Thread.currentThread().getName(), creditLimit.getId());

        // gets the latest balance from the database
        entityManager.refresh(creditLimit, LockModeType.PESSIMISTIC_WRITE);

        // makes sure reserved capacity cannot become negative
        // Do we have enough reservedAmount to remove this request amount?
        if (creditLimit.getReservedAmount()
                .compareTo(creditRequest.getAmount()) < 0) {

            // logs a cancellation that cannot release the requested amount
            logger.warn("[{}] CANCEL | FAILED | Request ID={} | Insufficient reserved capacity",
                    Thread.currentThread().getName(), creditRequestId);

            throw new InvalidCreditRequestOperationException(
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
        // save which bank canceled the credit request
        recordAuditLog(
                "CREDIT_REQUEST_CANCELLED",
                savedCreditRequest,
                "Cancelled reserved credit request of " + savedCreditRequest.getAmount() + " and released the reserved capacity",
                currentUser
        );

        // save the information needed for the SSE notification
        // we collect these values while the database transaction is active
        Long institutionId = creditLimit.getFinancialInstitutionId();
        Long requestId = savedCreditRequest.getId();
        CreditRequestStatus status = savedCreditRequest.getStatus();

        // register the SSE notification to run after a successful commit
        // if the database transaction rolls back, no notification is sent
        // Record the event in PostgreSQL within the current transaction.
        // The Kafka publisher will deliver it after the transaction commits.
        notificationOutboxService.recordCreditRequestEvent(
                institutionId,
                requestId,
                savedCreditRequest.getRequesterId(),
                status,
                "CREDIT_REQUEST_CANCELLED",
                null
        );

        // Log the successful cancellation
        logger.info("[{}] CANCEL | SUCCESS | Request ID={} | Status={}",
                Thread.currentThread().getName(),
                savedCreditRequest.getId(),
                savedCreditRequest.getStatus());

        // returns the updated credit request
        return toResponse(savedCreditRequest);
    }


    // expires an unused credit reservation
    @Transactional
    public void expireCreditRequest(Long creditRequestId) {

        // logs the start of an automatic expiry attempt
        logger.info("[{}] EXPIRE | START | Request ID={}",
                Thread.currentThread().getName(), creditRequestId);

        // logs the attempt to acquire the request row lock
        logger.info("[{}] EXPIRE | WAITING FOR LOCK | Request ID={}",
                Thread.currentThread().getName(), creditRequestId);

        // locks the credit request so it cannot be expired or changed twice
        CreditRequest creditRequest = creditRequestRepository
                .findWithLockById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException("Credit request not found"));

        // confirms the request row lock has been acquired
        logger.info("[{}] EXPIRE | LOCK ACQUIRED | Request ID={}",
                Thread.currentThread().getName(), creditRequestId);

        // checks that the current request status is allowed to move to EXPIRED
        // this prevents terminal requests such as USED or CANCELLED
        // from being expired and releasing capacity again
        validateStatusTransition(creditRequest, CreditRequestStatus.EXPIRED);

        // makes sure the reservation has an expiry time
        if (creditRequest.getExpiresAt() == null) {
            // logs a missing expiry timestamp
            logger.warn("[{}] EXPIRE | FAILED | Request ID={} | Missing expiry time",
                    Thread.currentThread().getName(), creditRequestId);
            throw new InvalidCreditRequestStateException("Credit request does not have an expiry time");
        }

        // prevents the reservation from expiring before its expiry time
        if (LocalDateTime.now().isBefore(creditRequest.getExpiresAt())) {
            // logs an expiry attempt made before the deadline
            logger.warn("[{}] EXPIRE | FAILED | Request ID={} | Reservation has not expired yet",
                    Thread.currentThread().getName(), creditRequestId);
            throw new InvalidCreditRequestStateException("Credit request cannot expire before its expiry time");
        }

        // logs the attempt to acquire the credit limit row lock
        logger.info("[{}] EXPIRE | WAITING FOR LOCK | Limit ID={}",
                Thread.currentThread().getName(), creditRequest.getCreditLimit().getId());

        // locks the credit limit before releasing the expired reservation
        CreditLimit creditLimit = creditLimitRepository
                .findWithLockById(creditRequest.getCreditLimit().getId())
                .orElseThrow(() ->
                        new CreditLimitNotFoundException("Credit limit not found"));

        // confirms the credit limit row lock has been acquired
        logger.info("[{}] EXPIRE | LOCK ACQUIRED | Limit ID={}",
                Thread.currentThread().getName(), creditLimit.getId());

        // gets the latest balance from the database
        entityManager.refresh(creditLimit, LockModeType.PESSIMISTIC_WRITE);

        // makes sure reserved capacity cannot become negative
        // Do we have enough reservedAmount to remove this request amount?
        // if yes -> subtract it if no -> stop
        // that would make the reserved amount negative
        if (creditLimit.getReservedAmount()
                .compareTo(creditRequest.getAmount()) < 0) {
            // logs an expiry that cannot release the requested amount
            logger.warn("[{}] EXPIRE | FAILED | Request ID={} | Insufficient reserved capacity",
                    Thread.currentThread().getName(), creditRequestId);
            throw new InvalidCreditRequestOperationException("Reserved amount cannot become negative");
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
        // save which bank this expired request belongs to
        recordAuditLog(
                "CREDIT_REQUEST_EXPIRED",
                savedCreditRequest,
                "Credit reservation of " + savedCreditRequest.getAmount() + " expired and released the reserved capacity",
                null
        );

        // save the information needed for notifications
        // qe collect these values while the database transaction is still active
        
        Long requestId = savedCreditRequest.getId();
        Long institutionId = creditLimit.getFinancialInstitutionId();
        CreditRequestStatus status = savedCreditRequest.getStatus();

        // register notifications to run after a successful database commit
        // this prevents sending notifications if the transaction rolls back
        // Record the event in PostgreSQL within the current transaction.
        // The Kafka publisher will deliver it after the transaction commits.
        notificationOutboxService.recordCreditRequestEvent(
                institutionId,
                requestId,
                savedCreditRequest.getRequesterId(),
                status,
                "CREDIT_REQUEST_EXPIRED",
                null
        );

        // logs the successful automatic expiry
        logger.info("[{}] EXPIRE | SUCCESS | Request ID={} | Status={} | Reserved capacity released",
                Thread.currentThread().getName(),
                savedCreditRequest.getId(),
                savedCreditRequest.getStatus());

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

        institutionAccessService.requireInstitution(creditRequest.getCreditLimit().getFinancialInstitutionId());

        // only requests waiting for approval can enter the approval-review workflow
        if (creditRequest.getStatus() != CreditRequestStatus.PENDING_APPROVAL) {
            throw new InvalidCreditRequestStateException("Credit request is not awaiting approval");
        }

        CreditLimit creditLimit = creditRequest.getCreditLimit();

        // shows the Risk Officer the headroom based on the latest exposure values
        // not values from when the credit request was originally submitted
        BigDecimal availableHeadroom = calculateAvailableHeadroom(creditLimit);

        return new CreditRequestReviewResponse(
                creditRequest.getId(),
                creditRequest.getAmount(),
                creditRequest.getStatus(),
                creditRequest.getCreatedAt(),

                creditRequest.getRequesterId(),
                null,

                creditLimit.getCounterparty().getId(),
                creditLimit.getCounterparty().getName(),

                creditLimit.getId(),
                creditLimit.getLimitAmount(),
                creditLimit.getUsedAmount(),
                creditLimit.getReservedAmount(),
                availableHeadroom,
                creditRequest.getExpiresAt(),
                creditLimit.getCounterparty().getStatus(),
                creditLimit.getFinancialInstitutionId(),
                null
        );
    }

    // gets the credit requests submitted by the logged-in user
    public Page<CreditRequestResponse> getMyCreditRequests(Pageable pageable) {

        // gets the currently logged-in user
        AuthenticatedCreditUser requester = getCurrentLoggedInUser();

        // finds only the credit requests created by this user
        Page<CreditRequest> creditRequests =
                creditRequestRepository.searchInInstitution(
                        institutionAccessService.getCurrentInstitutionId(),
                        null,
                        null,
                        requester.getUserId(),
                        pageable
                );

        // converts each CreditRequest into a CreditRequestResponse
        Page<CreditRequestResponse> responses = creditRequests.map(request -> toResponse(request));

        // returns the converted credit requests
        return responses;
    }

    // gets all credit requests that are waiting for Risk Officer approval
    public Page<CreditRequestResponse> getPendingCreditRequests(Pageable pageable) {

        // finds only requests that currently have the PENDING_APPROVAL status
        // pagination and sorting are applied through the Pageable object
        Page<CreditRequest> pendingRequests =
                creditRequestRepository.searchInInstitution(
                        institutionAccessService.getCurrentInstitutionId(),
                        CreditRequestStatus.PENDING_APPROVAL,
                        null,
                        null,
                        pageable
                );

        // converts each CreditRequest entity into a CreditRequestResponse DTO
        // so the API does not return the database entity directly
        Page<CreditRequestResponse> responses = pendingRequests.map(request -> toResponse(request));

        // returns the converted credit requests
        return responses;
    }

    // gets a credit request by ID for an authorized user
    public CreditRequestResponse getCreditRequestById(Long creditRequestId) {

        // checks that the credit request exists
        CreditRequest creditRequest = creditRequestRepository
                .findById(creditRequestId)
                .orElseThrow(() ->
                        new CreditRequestNotFoundException("Credit request not found"));

        institutionAccessService.requireInstitution(creditRequest.getCreditLimit().getFinancialInstitutionId());

        // gets the currently logged-in user
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // relationship managers can only view their own requests
        // If the logged-in user is a Relationship Manager AND this request does not belong to them, block them.
        if ("RELATIONSHIP_MANAGER".equals(currentUser.getUserRole())
                && !creditRequest.getRequesterId()
                .equals(currentUser.getUserId())) {

            throw new AccessDeniedException("You do not have permission to view this credit request");
        }

        // returns the credit request details
        return toResponse(creditRequest);
    }

    // searches and filters credit requests for the Risk Officer
    public Page<CreditRequestResponse> searchCreditRequests(
            CreditRequestStatus status,
            Long counterpartyId,
            Long requesterId,
            Pageable pageable) {

        // gets the matching credit requests from the database
        Page<CreditRequest> requests = creditRequestRepository.searchInInstitution(
                institutionAccessService.getCurrentInstitutionId(),
                status,
                counterpartyId,
                requesterId,
                pageable
        );

        // converts each credit request into a response
        Page<CreditRequestResponse> responses = requests.map(request -> toResponse(request));

        // returns the results
        return responses;
    }

    // converts a credit request into a response with only the information we want to send
    // we use this method to avoid writing the same response code in different methods
    private CreditRequestResponse toResponse(CreditRequest request) {
        // gets the credit limit associated with this credit request
        // so its financial institution and counterparty details can be included
        CreditLimit limit = request.getCreditLimit();

        // creates a DTO containing only the information required by the API
        // this avoids returning the database entity and its relationships directly
        return new CreditRequestResponse(
                // credit request information
                request.getId(), request.getAmount(), request.getStatus(),
                // associated credit limit, counterparty and requester IDs
                limit.getId(), limit.getCounterparty().getId(), request.getRequesterId(),
                // reservation expiry and request timestamps
                request.getExpiresAt(), request.getCreatedAt(), request.getUpdatedAt(),
                // counterparty information
                limit.getCounterparty().getName(), limit.getCounterparty().getStatus(),
                // combines the requester's first and last name for display
                null,
                // identifies the financial institution that owns the credit request
                limit.getFinancialInstitutionId(), null
        );
    }
}
