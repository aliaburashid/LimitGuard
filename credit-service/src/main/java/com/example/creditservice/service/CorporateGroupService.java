package com.example.creditservice.service;

import com.example.creditservice.dto.CorporateGroupRequest;
import com.example.creditservice.dto.CorporateGroupResponse;
import com.example.creditservice.exception.CorporateGroupAlreadyExistsException;
import com.example.creditservice.exception.InvalidCorporateGroupException;
import com.example.creditservice.model.AuditLog;
import com.example.creditservice.model.CorporateGroup;
import com.example.creditservice.repository.AuditLogRepository;
import com.example.creditservice.repository.CorporateGroupRepository;
import com.example.creditservice.security.InstitutionAccessService;
import com.example.creditservice.security.AuthenticatedCreditUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.example.creditservice.security.CatalogAccessService;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorporateGroupService {

    @Autowired
    private CatalogAccessService catalogAccess;

    // handles database operations for corporate groups
    @Autowired
    private CorporateGroupRepository corporateGroupRepository;

    // handles database operations for audit logs
    @Autowired
    private AuditLogRepository auditLogRepository;

    // gets the financial institution of the logged-in user
    @Autowired
    private InstitutionAccessService institutionAccessService;

    //-----------------------------------------------------------------

    // gets the currently logged-in user
    private AuthenticatedCreditUser getCurrentLoggedInUser() {
        return (AuthenticatedCreditUser) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }

    // records corporate group changes in the audit log
    private void recordAuditLog(
            String action,
            CorporateGroup group,
            String details) {

        // gets the user who performed the action
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // gets the financial institution of the logged-in user
        Long institutionId =
                institutionAccessService.getCurrentInstitutionId();

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
        auditLog.setActorId(currentUser.getUserId());

        // stores the bank that performed the action
        auditLog.setFinancialInstitutionId(institutionId);

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
                corporateGroupRepository.findVisible(institutionAccessService.getCurrentInstitutionId(), pageable);

        // converts each entity into a response dto
        return groups.map(this::convertToResponse);
    }

    // retrieves a corporate group using its id
    public CorporateGroupResponse getGroupById(Long id) {

        // finds the group or throws an exception
        CorporateGroup group = catalogAccess.requireGroup(id);

        return convertToResponse(group);
    }

    // updates an existing corporate group
    @Transactional
    public CorporateGroupResponse updateGroup(
            Long id,
            CorporateGroupRequest request) {

        // finds the existing corporate group
        CorporateGroup group = catalogAccess.requireWritableGroup(id);

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
        CorporateGroup group = catalogAccess.requireWritableGroup(id);

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
