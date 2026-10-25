package com.example.limitguard.service;

import com.example.limitguard.dto.CorporateGroupMembershipRequest;
import com.example.limitguard.dto.CorporateGroupMembershipResponse;
import com.example.limitguard.exception.*;
import com.example.limitguard.model.CorporateGroup;
import com.example.limitguard.model.CorporateGroupMembership;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.repository.CorporateGroupMembershipRepository;
import com.example.limitguard.repository.CorporateGroupRepository;
import com.example.limitguard.repository.CounterpartyRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    public void deleteMembership(Long membershipId) {

        // find the membership before deleting it
        CorporateGroupMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new MembershipNotFoundException("membership not found"));

        // delete the membership
        membershipRepository.delete(membership);
    }

}