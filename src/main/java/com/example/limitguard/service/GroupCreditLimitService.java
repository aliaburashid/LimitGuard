package com.example.limitguard.service;

import com.example.limitguard.dto.GroupCreditLimitRequest;
import com.example.limitguard.dto.GroupCreditLimitResponse;
import com.example.limitguard.dto.UpdateGroupCreditLimitRequest;
import com.example.limitguard.exception.*;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.model.CorporateGroup;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.GroupCreditLimit;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.repository.CorporateGroupRepository;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import com.example.limitguard.repository.GroupCreditLimitRepository;
import com.example.limitguard.security.InstitutionAccessService;
import com.example.limitguard.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.limitguard.model.CorporateGroupMembership;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.repository.CorporateGroupMembershipRepository;
import com.example.limitguard.repository.CreditLimitRepository;

import java.util.List;

import java.math.BigDecimal;

@Service
public class GroupCreditLimitService {

    // handles database operations for group credit limits
    @Autowired
    private GroupCreditLimitRepository groupCreditLimitRepository;

    // handles database operations for corporate groups
    @Autowired
    private CorporateGroupRepository corporateGroupRepository;

    // handles database operations for financial institutions
    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    // handles database operations for audit logs
    @Autowired
    private AuditLogRepository auditLogRepository;

    // checks which financial institution the logged-in user belongs to
    @Autowired
    private InstitutionAccessService institutionAccessService;

    // handles database operations for corporate group memberships
    @Autowired
    private CorporateGroupMembershipRepository corporateGroupMembershipRepository;

    // handles database operations for individual credit limits
    @Autowired
    private CreditLimitRepository creditLimitRepository;

    //-----------------------------------------------------------------

    // gets the currently logged-in user
    private User getCurrentLoggedInUser() {

        // gets the authenticated user's details from spring security
        MyUserDetails userDetails = (MyUserDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return userDetails.getUser();
    }

    // records group credit limit changes in the audit log
    private void recordAuditLog(String action, GroupCreditLimit groupCreditLimit, String details, User actor) {

        AuditLog auditLog = new AuditLog();
        // stores the action performed
        auditLog.setAction(action);
        // identifies the type of entity being changed
        auditLog.setEntityType("GROUP_CREDIT_LIMIT");
        // stores the group credit limit id
        auditLog.setEntityId(groupCreditLimit.getId());
        // stores information about the change
        auditLog.setDetails(details);
        // stores the user who performed the action
        auditLog.setActor(actor);
        // stores the bank that owns the group credit limit
        auditLog.setFinancialInstitution(
                groupCreditLimit.getFinancialInstitution()
        );
        // saves the audit log to the database
        auditLogRepository.save(auditLog);
    }

    // converts a group credit limit entity into a response dto
    private GroupCreditLimitResponse convertToResponse(
            GroupCreditLimit groupCreditLimit) {

        return new GroupCreditLimitResponse(
                groupCreditLimit.getId(),
                groupCreditLimit.getCorporateGroup().getId(),
                groupCreditLimit.getCorporateGroup().getName(),
                groupCreditLimit.getFinancialInstitution().getId(),
                groupCreditLimit.getAmount(),
                groupCreditLimit.getCreatedAt(),
                groupCreditLimit.getUpdatedAt()
        );
    }

    // creates a group credit limit for the logged-in user's bank
    @Transactional
    public GroupCreditLimitResponse createGroupCreditLimit(
            GroupCreditLimitRequest request) {

        // checks if the corporate group id is provided
        if (request == null || request.getCorporateGroupId() == null) {
            throw new InvalidGroupCreditLimitException(
                    "Corporate group ID is required"
            );
        }

        // checks if the credit limit amount is greater than zero
        if (request.getLimitAmount() == null ||
                request.getLimitAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidGroupCreditLimitException(
                    "Credit limit amount must be greater than zero"
            );
        }

        // gets the logged-in user's financial institution id
        Long institutionId =
                institutionAccessService.getCurrentInstitutionId();

        // checks that the financial institution exists
        FinancialInstitution financialInstitution =
                financialInstitutionRepository.findById(institutionId)
                        .orElseThrow(() ->
                                new FinancialInstitutionNotFoundException(
                                        "Financial institution not found"
                                ));

        // checks that the corporate group exists
        CorporateGroup corporateGroup = corporateGroupRepository
                .findById(request.getCorporateGroupId())
                .orElseThrow(() ->
                        new CorporateGroupNotFoundException(
                                "Corporate group not found with ID: "
                                        + request.getCorporateGroupId()
                        ));

        // prevents the same bank from creating duplicate group limits
        if (groupCreditLimitRepository
                .existsByFinancialInstitutionIdAndCorporateGroupId(
                        institutionId,
                        corporateGroup.getId())) {

            throw new GroupCreditLimitAlreadyExistsException(
                    "This financial institution already has a credit limit for this corporate group"
            );
        }

        // creates the new group credit limit
        GroupCreditLimit groupCreditLimit = new GroupCreditLimit();

        // assigns the corporate group
        groupCreditLimit.setCorporateGroup(corporateGroup);

        // assigns the logged-in user's financial institution
        groupCreditLimit.setFinancialInstitution(financialInstitution);

        // assigns the approved credit limit amount
        groupCreditLimit.setAmount(request.getLimitAmount());

        // saves the group credit limit to PostgreSQL
        GroupCreditLimit savedLimit =
                groupCreditLimitRepository.save(groupCreditLimit);

        // gets the user who created the group credit limit
        User currentUser = getCurrentLoggedInUser();

        // records the creation in the audit log
        recordAuditLog(
                "GROUP_CREDIT_LIMIT_CREATED", savedLimit,
                "Created group credit limit of "
                        + savedLimit.getAmount()
                        + " for corporate group "
                        + corporateGroup.getName()
                        + " (ID: " + corporateGroup.getId() + ")",
                currentUser
        );

        // returns the saved credit limit details
        return convertToResponse(savedLimit);
    }


    // gets one group credit limit belonging to the logged-in user's bank
    @Transactional(readOnly = true)
    public GroupCreditLimitResponse getGroupCreditLimitById(Long id) {

        // gets the logged-in user's financial institution id
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // checks that the group credit limit exists for this bank
        GroupCreditLimit groupCreditLimit = groupCreditLimitRepository
                .findByIdAndFinancialInstitutionId(id, institutionId)
                .orElseThrow(() -> new GroupCreditLimitNotFoundException(
                        "Group credit limit not found with ID: " + id
                ));

        // returns the group credit limit details
        return convertToResponse(groupCreditLimit);
    }


    // gets all group credit limits belonging to the logged-in user's bank
    // method only retries so read only
    @Transactional(readOnly = true)
    public Page<GroupCreditLimitResponse> getAllGroupCreditLimits(Pageable pageable) {

        // gets the logged-in user's financial institution id
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // gets only this bank's group credit limits with pagination and sorting
        Page<GroupCreditLimit> groupCreditLimits =
                groupCreditLimitRepository.findByFinancialInstitutionId(
                        institutionId, pageable
                );

        // converts the group credit limits into response dtos
        return groupCreditLimits.map(this::convertToResponse);
    }


    // updates an existing group credit limit
    @Transactional
    public GroupCreditLimitResponse updateGroupCreditLimit(
            Long id, UpdateGroupCreditLimitRequest request) {

        // gets the logged-in user's financial institution id
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // checks that the group credit limit exists and belongs to this bank
        GroupCreditLimit groupCreditLimit = groupCreditLimitRepository
                .findByIdAndFinancialInstitutionId(id, institutionId)
                .orElseThrow(() -> new GroupCreditLimitNotFoundException(
                        "Group credit limit not found with ID: " + id
                ));

        // checks if the updated credit limit amount is greater than zero
        if (request == null || request.getLimitAmount() == null ||
                request.getLimitAmount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new InvalidGroupCreditLimitException(
                    "Credit limit amount must be greater than zero"
            );
        }


        // gets the total money used and reserved by the group
        BigDecimal currentExposure = calculateGroupExposure(
                groupCreditLimit.getCorporateGroup().getId()
        );

        // checks if the new limit is smaller than the exposure
        if (request.getLimitAmount().compareTo(currentExposure) < 0) {

            // rejects the update
            throw new InvalidCreditLimitReductionException(
                    "Group credit limit cannot be lower than current group exposure"
            );
        }

        // keeps the old limit amount for the audit log
        BigDecimal oldLimitAmount = groupCreditLimit.getAmount();

        // updates only the approved group credit limit amount
        groupCreditLimit.setAmount(request.getLimitAmount());

        // saves the updated group credit limit to PostgreSQL
        GroupCreditLimit updatedLimit =
                groupCreditLimitRepository.save(groupCreditLimit);

        // gets the user who updated the group credit limit
        User currentUser = getCurrentLoggedInUser();

        // records the change in the audit log
        recordAuditLog(
                "GROUP_CREDIT_LIMIT_UPDATED", updatedLimit,
                "Group credit limit changed from "
                        + oldLimitAmount
                        + " to "
                        + updatedLimit.getAmount()
                        + " for corporate group "
                        + updatedLimit.getCorporateGroup().getName(),
                currentUser
        );

        // returns the updated group credit limit details
        return convertToResponse(updatedLimit);
    }


    // calculates the combined exposure of a corporate group for the logged-in user's bank
    @Transactional(readOnly = true)
    public BigDecimal calculateGroupExposure(Long corporateGroupId) {

        // gets the logged-in user's financial institution id
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // checks that the corporate group exists
        corporateGroupRepository.findById(corporateGroupId)
                .orElseThrow(() -> new CorporateGroupNotFoundException(
                        "Corporate group not found with ID: " + corporateGroupId
                ));

        // finds all counterparties assigned to this corporate group
        List<CorporateGroupMembership> memberships =
                corporateGroupMembershipRepository.findByCorporateGroupId(corporateGroupId);

        // returns zero if the corporate group has no counterparties
        if (memberships.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // gets the ids of all counterparties in the group
        List<Long> counterpartyIds = memberships.stream()
                .map(membership -> membership.getCounterparty().getId())
                .toList();

        // finds the credit limits for these counterparties belonging to this bank
        List<CreditLimit> creditLimits =
                creditLimitRepository.findByFinancialInstitutionIdAndCounterpartyIdIn(
                        institutionId,
                        counterpartyIds
                );

        // calculates the total used and reserved amounts across all group members
        BigDecimal totalExposure = BigDecimal.ZERO;

        for (CreditLimit creditLimit : creditLimits) {

            // adds the used amount and reserved amount of each counterparty
            BigDecimal counterpartyExposure =
                    creditLimit.getUsedAmount().add(creditLimit.getReservedAmount());

            // adds the counterparty exposure to the group's total exposure
            totalExposure = totalExposure.add(counterpartyExposure);
        }

        // returns the combined group exposure
        return totalExposure;
    }


    // calculates the available group credit headroom
    @Transactional(readOnly = true)
    public BigDecimal calculateGroupHeadroom(Long corporateGroupId) {

        // gets the logged-in user's financial institution id
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // finds the approved group credit limit for this bank
        GroupCreditLimit groupCreditLimit = groupCreditLimitRepository
                .findByFinancialInstitutionIdAndCorporateGroupId(
                        institutionId,
                        corporateGroupId
                )
                .orElseThrow(() -> new GroupCreditLimitNotFoundException(
                        "Group credit limit not found for corporate group ID: "
                                + corporateGroupId
                ));

        // gets the current combined group exposure
        BigDecimal totalExposure = calculateGroupExposure(corporateGroupId);

        // subtracts the exposure from the approved group limit
        return groupCreditLimit.getAmount().subtract(totalExposure);
    }



}
