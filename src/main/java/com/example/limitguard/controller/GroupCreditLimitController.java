package com.example.limitguard.controller;

import com.example.limitguard.documentation.GroupCreditLimitApi;
import com.example.limitguard.dto.GroupCreditLimitRequest;
import com.example.limitguard.dto.GroupCreditLimitResponse;
import com.example.limitguard.service.GroupCreditLimitService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
}
