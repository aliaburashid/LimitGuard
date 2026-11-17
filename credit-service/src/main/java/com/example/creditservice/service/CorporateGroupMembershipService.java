package com.example.creditservice.service;

import com.example.creditservice.dto.CorporateGroupMembershipRequest;
import com.example.creditservice.dto.CorporateGroupMembershipResponse;
import com.example.creditservice.exception.*;
import com.example.creditservice.model.AuditLog;
import com.example.creditservice.model.CorporateGroup;
import com.example.creditservice.model.CorporateGroupMembership;
import com.example.creditservice.model.Counterparty;
import com.example.creditservice.repository.AuditLogRepository;
import com.example.creditservice.repository.CorporateGroupMembershipRepository;
import com.example.creditservice.security.InstitutionAccessService;
import com.example.creditservice.security.AuthenticatedCreditUser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.example.creditservice.security.CatalogAccessService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CorporateGroupMembershipService {

    @Autowired
    private CatalogAccessService catalogAccess;

    // used to save and find corporate group memberships
    @Autowired
    private CorporateGroupMembershipRepository membershipRepository;

    // used to save audit logs
    @Autowired
    private AuditLogRepository auditLogRepository;

    // used to get the logged-in user's financial institution
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

    // records membership changes in the audit log
    private void recordAuditLog(String action, CorporateGroupMembership membership, String details) {

        // gets the user who performed the action
        AuthenticatedCreditUser currentUser = getCurrentLoggedInUser();

        // gets the financial institution of the logged-in user
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // creates a new audit log
        AuditLog auditLog = new AuditLog();

        // stores the action performed
        auditLog.setAction(action);

        // stores the affected entity type
        auditLog.setEntityType("CORPORATE_GROUP_MEMBERSHIP");

        // stores the membership id
        auditLog.setEntityId(membership.getId());

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

    // converts a membership into a response dto
    private CorporateGroupMembershipResponse mapToResponse(CorporateGroupMembership membership) {

        // return the membership details
        return new CorporateGroupMembershipResponse(
                membership.getId(),
                membership.getCorporateGroup().getId(),
                membership.getCorporateGroup().getName(),
                membership.getCounterparty().getId()
        );
    }

    // creates a membership between a group and a counterparty
    @Transactional
    public CorporateGroupMembershipResponse createMembership(CorporateGroupMembershipRequest request) {

        // check if the request contains both ids
        if (request == null || request.getCorporateGroupId() == null
                || request.getCounterpartyId() == null) {
            throw new InvalidMembershipException("corporate group id and counterparty id are required");
        }

        // find the corporate group
        CorporateGroup corporateGroup = catalogAccess.requireWritableGroup(request.getCorporateGroupId());

        // find the counterparty
        Counterparty counterparty = catalogAccess.requireWritableCounterparty(request.getCounterpartyId());

        // check if the counterparty already belongs to a group
        if (membershipRepository.existsByCounterpartyId(counterparty.getId())) {
            throw new MembershipExistsException("counterparty already belongs to a corporate group");
        }

        // create a new membership
        CorporateGroupMembership membership = new CorporateGroupMembership();

        // connect the group and counterparty
        membership.setCorporateGroup(corporateGroup);
        membership.setCounterparty(counterparty);

        // save the membership
        CorporateGroupMembership savedMembership = membershipRepository.save(membership);

        // records the new membership in the audit log
        recordAuditLog(
                "CORPORATE_GROUP_MEMBER_ADDED", savedMembership,
                "Added counterparty " + counterparty.getId()
                        + " (" + counterparty.getName() + ")"
                        + " to corporate group " + corporateGroup.getId()
                        + " (" + corporateGroup.getName() + ")"
        );

        // return the saved membership information
        return mapToResponse(savedMembership);
    }


    // gets all memberships
    public List<CorporateGroupMembershipResponse> getAllMemberships() {

        // find all memberships and convert them to response dtos
        return membershipRepository.findVisible(institutionAccessService.getCurrentInstitutionId())
                // convert the list into a stream so we can process each membership
                .stream()
                // take each membership and convert it into a response dto
                .map(this::mapToResponse)
                // collect all the response dtos into a list
                .toList();
    }


    // gets a membership using its id
    public CorporateGroupMembershipResponse getMembershipById(Long membershipId) {

        // find the membership or show an error
        CorporateGroupMembership membership = membershipRepository.findVisibleById(membershipId, institutionAccessService.getCurrentInstitutionId())
                .orElseThrow(() -> new MembershipNotFoundException("membership not found"));

        // return the membership information
        return mapToResponse(membership);
    }


    // gets all memberships belonging to one corporate group
    public List<CorporateGroupMembershipResponse> getMembershipsByGroupId(Long groupId) {

        // check if the corporate group exists
        catalogAccess.requireGroup(groupId);

        // find all memberships for this group
        return membershipRepository.findVisibleByGroupId(groupId, institutionAccessService.getCurrentInstitutionId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // deletes a membership using its id
    @Transactional
    public void deleteMembership(Long membershipId) {

        // find the membership before deleting it
        CorporateGroupMembership membership = membershipRepository.findVisibleById(membershipId, institutionAccessService.getCurrentInstitutionId())
                .orElseThrow(() -> new MembershipNotFoundException("membership not found"));

        catalogAccess.requireWritableGroup(membership.getCorporateGroup().getId());
        catalogAccess.requireWritableCounterparty(membership.getCounterparty().getId());

        // stores the membership information before deletion
        Long groupId = membership.getCorporateGroup().getId();
        String groupName = membership.getCorporateGroup().getName();
        Long counterpartyId = membership.getCounterparty().getId();
        String counterpartyName = membership.getCounterparty().getName();

        // delete the membership
        membershipRepository.delete(membership);

        // records the removal in the audit log
        recordAuditLog(
                "CORPORATE_GROUP_MEMBER_REMOVED", membership,
                "Removed counterparty " + counterpartyId
                        + " (" + counterpartyName + ")"
                        + " from corporate group " + groupId
                        + " (" + groupName + ")"
        );
    }

}
