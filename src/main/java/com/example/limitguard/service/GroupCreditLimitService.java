package com.example.limitguard.service;

import com.example.limitguard.dto.GroupCreditLimitRequest;
import com.example.limitguard.dto.GroupCreditLimitResponse;
import com.example.limitguard.exception.CorporateGroupNotFoundException;
import com.example.limitguard.exception.FinancialInstitutionNotFoundException;
import com.example.limitguard.exception.GroupCreditLimitAlreadyExistsException;
import com.example.limitguard.exception.InvalidGroupCreditLimitException;
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
}
