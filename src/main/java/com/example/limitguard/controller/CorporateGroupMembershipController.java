package com.example.limitguard.controller;

import com.example.limitguard.documentation.CorporateGroupMembershipApi;
import com.example.limitguard.dto.CorporateGroupMembershipRequest;
import com.example.limitguard.dto.CorporateGroupMembershipResponse;
import com.example.limitguard.service.CorporateGroupMembershipService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/corporate-group-memberships")
public class CorporateGroupMembershipController implements CorporateGroupMembershipApi {

    // connects the controller to the membership service
    @Autowired
    private CorporateGroupMembershipService membershipService;

    //--------------------------------------------------------

    // creates a new membership between a corporate group and a counterparty
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER')")
    @Override
    public ResponseEntity<CorporateGroupMembershipResponse> createMembership(
            @Valid @RequestBody CorporateGroupMembershipRequest request) {

        // sends the request to the service to create the membership
        CorporateGroupMembershipResponse membership = membershipService.createMembership(request);

        // returns the created membership with status 201
        return ResponseEntity.status(HttpStatus.CREATED).body(membership);
    }


    // gets all memberships from the database
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER', 'RELATIONSHIP_MANAGER')")
    @Override
    public ResponseEntity<List<CorporateGroupMembershipResponse>> getAllMemberships() {

        // gets all memberships from the service
        List<CorporateGroupMembershipResponse> memberships = membershipService.getAllMemberships();

        // returns the memberships with status 200
        return ResponseEntity.ok(memberships);
    }


    // gets one membership using its id
    @GetMapping("/{membershipId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER', 'RELATIONSHIP_MANAGER')")
    @Override
    public ResponseEntity<CorporateGroupMembershipResponse> getMembershipById(
            @PathVariable Long membershipId) {

        // finds the membership using its id
        CorporateGroupMembershipResponse membership = membershipService.getMembershipById(membershipId);

        // returns the membership with status 200
        return ResponseEntity.ok(membership);
    }


    // gets all memberships belonging to a specific corporate group
    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER', 'RELATIONSHIP_MANAGER')")
    @Override
    public ResponseEntity<List<CorporateGroupMembershipResponse>> getMembershipsByGroupId(
            @PathVariable Long groupId) {

        // finds all memberships for the selected corporate group
        List<CorporateGroupMembershipResponse> memberships = membershipService.getMembershipsByGroupId(groupId);

        // returns the memberships with status 200
        return ResponseEntity.ok(memberships);
    }


    // deletes a membership using its id
    @DeleteMapping("/{membershipId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER')")
    @Override
    public ResponseEntity<Void> deleteMembership(
            @PathVariable Long membershipId) {

        // deletes the membership using the service
        membershipService.deleteMembership(membershipId);

        // returns status 204 because there is no response body
        return ResponseEntity.noContent().build();
    }
}