package com.example.limitguard.service;

import com.example.limitguard.dto.CorporateGroupMembershipRequest;
import com.example.limitguard.dto.CorporateGroupMembershipResponse;
import com.example.limitguard.exception.*;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.model.CorporateGroup;
import com.example.limitguard.model.CorporateGroupMembership;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.repository.CorporateGroupMembershipRepository;
import com.example.limitguard.repository.CorporateGroupRepository;
import com.example.limitguard.repository.CounterpartyRepository;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import com.example.limitguard.security.InstitutionAccessService;
import com.example.limitguard.security.MyUserDetails;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CorporateGroupMembershipService {

    // used to save and find corporate group memberships
    @Autowired
    private CorporateGroupMembershipRepository membershipRepository;

    // used to find corporate groups
    @Autowired
    private CorporateGroupRepository corporateGroupRepository;

    // used to find counterparties
    @Autowired
    private CounterpartyRepository counterpartyRepository;

    // used to save audit logs
    @Autowired
    private AuditLogRepository auditLogRepository;

    // used to find financial institutions
    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    // used to get the logged-in user's financial institution
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

    // records membership changes in the audit log
    private void recordAuditLog(String action, CorporateGroupMembership membership, String details) {

        // gets the user who performed the action
        User currentUser = getCurrentLoggedInUser();

        // gets the financial institution of the logged-in user
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // finds the financial institution
        FinancialInstitution financialInstitution =
                financialInstitutionRepository.findById(institutionId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "financial institution not found"
                                ));

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
        auditLog.setActor(currentUser);

        // stores the bank that performed the action
        auditLog.setFinancialInstitution(financialInstitution);

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
        CorporateGroup corporateGroup = corporateGroupRepository
                .findById(request.getCorporateGroupId())
                .orElseThrow(() -> new CorporateGroupNotFoundException("corporate group not found"));

        // find the counterparty
        Counterparty counterparty = counterpartyRepository
                .findById(request.getCounterpartyId())
                .orElseThrow(() -> new InvalidMembershipException("counterparty not found"));

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
        return membershipRepository.findAll()
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
        CorporateGroupMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new MembershipNotFoundException("membership not found"));

        // return the membership information
        return mapToResponse(membership);
    }


    // gets all memberships belonging to one corporate group
    public List<CorporateGroupMembershipResponse> getMembershipsByGroupId(Long groupId) {

        // check if the corporate group exists
        if (!corporateGroupRepository.existsById(groupId)) {
            throw new CorporateGroupNotFoundException("corporate group not found");
        }

        // find all memberships for this group
        return membershipRepository.findByCorporateGroupId(groupId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // deletes a membership using its id
    @Transactional
    public void deleteMembership(Long membershipId) {

        // find the membership before deleting it
        CorporateGroupMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new MembershipNotFoundException("membership not found"));

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
