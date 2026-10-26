package com.example.limitguard.service;

import com.example.limitguard.dto.CorporateGroupRequest;
import com.example.limitguard.dto.CorporateGroupResponse;
import com.example.limitguard.exception.CorporateGroupAlreadyExistsException;
import com.example.limitguard.exception.CorporateGroupNotFoundException;
import com.example.limitguard.exception.InvalidCorporateGroupException;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.model.CorporateGroup;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.repository.CorporateGroupRepository;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import com.example.limitguard.security.InstitutionAccessService;
import com.example.limitguard.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorporateGroupService {

    // handles database operations for corporate groups
    @Autowired
    private CorporateGroupRepository corporateGroupRepository;

    // handles database operations for audit logs
    @Autowired
    private AuditLogRepository auditLogRepository;

    // handles database operations for financial institutions
    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    // gets the financial institution of the logged-in user
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

    // records corporate group changes in the audit log
    private void recordAuditLog(
            String action,
            CorporateGroup group,
            String details) {

        // gets the user who performed the action
        User currentUser = getCurrentLoggedInUser();

        // gets the financial institution of the logged-in user
        Long institutionId =
                institutionAccessService.getCurrentInstitutionId();

        FinancialInstitution financialInstitution =
                financialInstitutionRepository.findById(institutionId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Financial institution not found"
                                ));

        // creates a new audit log
        AuditLog auditLog = new AuditLog();

        // stores the action performed
        auditLog.setAction(action);

        // stores the affected entity type
        auditLog.setEntityType("CORPORATE_GROUP");

        // stores the corporate group id
        auditLog.setEntityId(group.getId());

        // stores information about the change
        auditLog.setDetails(details);

        // stores the user who performed the action
        auditLog.setActor(currentUser);

        // stores the bank that performed the action
        auditLog.setFinancialInstitution(financialInstitution);

        // saves the audit log
        auditLogRepository.save(auditLog);
    }

    //-----------------------------------------------------------------

    // converts a corporate group entity into a response dto
    private CorporateGroupResponse convertToResponse(CorporateGroup group) {

        return new CorporateGroupResponse(
                group.getId(),
                group.getName(),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }

    // creates a new corporate group
    @Transactional
    public CorporateGroupResponse createGroup(CorporateGroupRequest request) {

        // checks if the group name is empty
        if (request == null || request.getName() == null
                || request.getName().isBlank()) {

            throw new InvalidCorporateGroupException(
                    "Corporate group name is required"
            );
        }

        // checks if another corporate group already has this name
        if (corporateGroupRepository.existsByName(request.getName())) {

            throw new CorporateGroupAlreadyExistsException(
                    "Corporate group already exists with name: "
                            + request.getName()
            );
        }

        // creates a new corporate group entity
        CorporateGroup group = new CorporateGroup();
        group.setName(request.getName());

        // saves the group in the database
        CorporateGroup savedGroup = corporateGroupRepository.save(group);

        // records the creation in the audit log
        recordAuditLog(
                "CORPORATE_GROUP_CREATED", savedGroup,
                "Created corporate group: " + savedGroup.getName()
        );

        // converts the saved entity into a response dto
        return convertToResponse(savedGroup);
    }

    // retrieves corporate groups with pagination and sorting
    public Page<CorporateGroupResponse> getAllGroups(Pageable pageable) {

        // gets corporate groups from the database
        Page<CorporateGroup> groups =
                corporateGroupRepository.findAll(pageable);

        // converts each entity into a response dto
        return groups.map(this::convertToResponse);
    }

    // retrieves a corporate group using its id
    public CorporateGroupResponse getGroupById(Long id) {

        // finds the group or throws an exception
        CorporateGroup group = corporateGroupRepository.findById(id)
                .orElseThrow(() ->
                        new CorporateGroupNotFoundException(
                                "Corporate group not found with ID: " + id
                        ));

        return convertToResponse(group);
    }

    // updates an existing corporate group
    @Transactional
    public CorporateGroupResponse updateGroup(
            Long id,
            CorporateGroupRequest request) {

        // finds the existing corporate group
        CorporateGroup group = corporateGroupRepository.findById(id)
                .orElseThrow(() ->
                        new CorporateGroupNotFoundException(
                                "Corporate group not found with ID: " + id
                        ));

        // checks if the new name is empty
        if (request == null || request.getName() == null
                || request.getName().isBlank()) {

            throw new InvalidCorporateGroupException(
                    "Corporate group name is required"
            );
        }

        // checks if another group already has the new name
        if (!group.getName().equals(request.getName())
                && corporateGroupRepository.existsByName(request.getName())) {

            throw new CorporateGroupAlreadyExistsException(
                    "Corporate group already exists with name: "
                            + request.getName()
            );
        }

        // stores the previous name for the audit log
        String oldName = group.getName();

        // updates the corporate group name
        group.setName(request.getName());

        // saves the updated group
        CorporateGroup updatedGroup = corporateGroupRepository.save(group);

        // records the update in the audit log
        recordAuditLog(
                "CORPORATE_GROUP_UPDATED", updatedGroup,
                "Corporate group name changed from "
                        + oldName
                        + " to "
                        + updatedGroup.getName()
        );

        return convertToResponse(updatedGroup);
    }

    // deletes a corporate group
    @Transactional
    public void deleteGroup(Long id) {

        // finds the group or throws an exception
        CorporateGroup group = corporateGroupRepository.findById(id)
                .orElseThrow(() ->
                        new CorporateGroupNotFoundException(
                                "Corporate group not found with ID: " + id
                        ));

        // stores the group name before deletion
        String groupName = group.getName();

        // deletes the corporate group
        corporateGroupRepository.delete(group);

        // records the deletion in the audit log
        recordAuditLog(
                "CORPORATE_GROUP_DELETED", group,
                "Deleted corporate group: " + groupName
        );
    }
}
