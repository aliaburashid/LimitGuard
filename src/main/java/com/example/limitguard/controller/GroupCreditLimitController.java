package com.example.limitguard.controller;

import com.example.limitguard.documentation.GroupCreditLimitApi;
import com.example.limitguard.dto.GroupCreditLimitRequest;
import com.example.limitguard.dto.GroupCreditLimitResponse;
import com.example.limitguard.dto.UpdateGroupCreditLimitRequest;
import com.example.limitguard.service.GroupCreditLimitService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/group-credit-limits")
public class GroupCreditLimitController implements GroupCreditLimitApi {

    @Autowired
    private GroupCreditLimitService groupCreditLimitService;

    //--------------------------------------------------------

    // creates a credit limit for an existing corporate group
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER')")
    @Override
    public ResponseEntity<GroupCreditLimitResponse> createGroupCreditLimit(
            @Valid @RequestBody GroupCreditLimitRequest request) {

        // calls the service to validate and save the credit limit
        GroupCreditLimitResponse response =
                groupCreditLimitService.createGroupCreditLimit(request);

        // returns 201 created with the saved credit limit details
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // gets one group credit limit belonging to the logged-in user's bank
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER', 'RELATIONSHIP_MANAGER')")
    @Override
    public ResponseEntity<GroupCreditLimitResponse> getGroupCreditLimitById(
            @PathVariable Long id) {

        // calls the service to retrieve the group credit limit
        GroupCreditLimitResponse response =
                groupCreditLimitService.getGroupCreditLimitById(id);

        // returns 200 ok with the group credit limit details
        return ResponseEntity.ok(response);
    }

    // gets all group credit limits belonging to the logged-in user's bank
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER', 'RELATIONSHIP_MANAGER')")
    @Override
    public ResponseEntity<Page<GroupCreditLimitResponse>> getAllGroupCreditLimits(
            @ParameterObject
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {

        // calls the service to retrieve group credit limits with pagination and sorting
        Page<GroupCreditLimitResponse> response =
                groupCreditLimitService.getAllGroupCreditLimits(pageable);

        // returns 200 ok with the paginated group credit limits
        return ResponseEntity.ok(response);
    }

    // updates an existing group credit limit
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER')")
    @Override
    public ResponseEntity<GroupCreditLimitResponse> updateGroupCreditLimit(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGroupCreditLimitRequest request) {

        // calls the service to validate and update the group credit limit
        GroupCreditLimitResponse response =
                groupCreditLimitService.updateGroupCreditLimit(id, request);

        // returns 200 ok with the updated group credit limit details
        return ResponseEntity.ok(response);
    }

    // gets the total used and reserved exposure of a corporate group
    @GetMapping("/groups/{corporateGroupId}/exposure")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER', 'RELATIONSHIP_MANAGER')")
    public ResponseEntity<BigDecimal> getGroupExposure(
            @PathVariable Long corporateGroupId) {

        // calculates the combined exposure of the group
        BigDecimal exposure = groupCreditLimitService
                .calculateGroupExposure(corporateGroupId);

        // returns the calculated exposure
        return ResponseEntity.ok(exposure);
    }

    // gets the remaining available credit headroom of a corporate group
    @GetMapping("/groups/{corporateGroupId}/headroom")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_OFFICER', 'RELATIONSHIP_MANAGER')")
    public ResponseEntity<BigDecimal> getGroupHeadroom(
            @PathVariable Long corporateGroupId) {

        // calculates the remaining available credit for the group
        BigDecimal headroom = groupCreditLimitService
                .calculateGroupHeadroom(corporateGroupId);

        // returns the available group headroom
        return ResponseEntity.ok(headroom);
    }


}
